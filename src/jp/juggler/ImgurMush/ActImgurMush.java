package jp.juggler.ImgurMush;

import java.io.File;

import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ImageTempDir;
import jp.juggler.ImgurMush.helper.MenuDialog;
import jp.juggler.ImgurMush.helper.MushroomHelper;
import jp.juggler.ImgurMush.helper.PreviewLoader;
import jp.juggler.ImgurMush.helper.TextFormat;
import jp.juggler.ImgurMush.helper.UploadTargetManager;
import jp.juggler.ImgurMush.uploader.UploadJob;
import jp.juggler.ImgurMush.uploader.UploaderUI;
import jp.juggler.util.LogCategory;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class ActImgurMush extends BaseActivity {
	static final LogCategory log = new LogCategory("ActImgurMush");

	@Override
	public void onCreate(Bundle savedInstanceState) {
		log.d("onCreate");
		super.onCreate(savedInstanceState);
		initUI();
		init_page();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		init_page();
	}

	@Override
	protected void onPause() {
		super.onPause();
		save_status();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent detail) {
		log.d("onActivityResult req=%s res=%sdetail=%s",requestCode,resultCode,detail);
		switch(requestCode){
		case MenuDialog.REQ_FILEPICKER:
			if( resultCode == RESULT_OK && detail != null ){
				String path = MushroomHelper.uri_to_path(env,detail.getData());
				if(path != null ) setCurrentFile(MenuDialog.FILE_FROM_PICK,path);
			}
			break;
		case MenuDialog.REQ_HISTORY:
			if( resultCode == RESULT_OK && detail != null ){
				MushroomHelper.finish_mush(env,true,detail.getStringExtra("url"));
			}
			break;
		case MenuDialog.REQ_PREF:
			upload_target_manager.reload();
			upload_autostart = env.pref().getBoolean(PrefKey.KEY_AUTO_UPLOAD,false);
			editor_autostart = env.pref().getBoolean(PrefKey.KEY_AUTO_EDIT,false);
			break;
		case MenuDialog.REQ_ARRANGE:
			if( resultCode ==  RESULT_OK && detail != null){
				String path = detail.getStringExtra(PrefKey.EXTRA_DST_PATH);
				if( path != null ) setCurrentFile( MenuDialog.FILE_FROM_EDIT,path);
			}
			break;
		case MenuDialog.REQ_APPINFO:
			break;
		case MenuDialog.REQ_CAPTURE:
			if(resultCode == RESULT_OK ){
				Uri uri = (detail==null ? null : detail.getData());
				if( uri == null ) uri = capture_uri;
				if( uri == null ){
					log.e("cannot get capture uri");
				}else{
					log.d("capture uri = %s", uri);
					 setCurrentFile(MenuDialog.FILE_FROM_PICK,MushroomHelper.uri_to_path(env,uri));
				}
			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, detail);
	}

	@Override
	protected void  procMenuKey() {
		MenuDialog.menu_dialog(env);
	}

	//////////////////////////////////////////////////////////////

	final ActImgurMush act = this;
	ImageView preview;
	TextView tvFileDesc;
	Button btnEdit;
	Button btnUpload;


	UploadTargetManager upload_target_manager;
	UploaderUI uploader;

	void initUI(){
		setContentView(R.layout.act_imgur_mush);

		preview = (ImageView)findViewById(R.id.preview);
		tvFileDesc = (TextView)findViewById(R.id.tvFileDesc);

		btnEdit= (Button)findViewById(R.id.btnEdit);
		btnUpload = (Button)findViewById(R.id.btnUpload);

		uploader = new UploaderUI(env,new UploaderUI.Callback() {
			@Override public void onStatusChanged(boolean bBusy) {
				updateUploadButtonStatus();
			}
			@Override
			public void onAbort(String error_message) {
				env.show_toast(false,error_message);
			}
			
			@Override
			public void onTextOutput(String text_output) {
				MushroomHelper.finish_mush(env,false,text_output);
//				int t = Integer.parseInt(env.pref().getString(PrefKey.KEY_URL_MODE,"0"));
//				switch(t){
//				default:
//				case 0: MushroomHelper.finish_mush(env,true,image_url); return;
//				case 1: MushroomHelper.finish_mush(env,true,page_url); return;
//				}
				
			}
		});

		upload_target_manager = new UploadTargetManager(env);

		// 旧版からの以降に関するデータ型の変更
		try{
			SharedPreferences pref = env.pref();
			int i = pref.getInt(PrefKey.KEY_URL_MODE ,-1);
			if( i != -1 ){
				SharedPreferences.Editor e = pref.edit();
				e.putString(PrefKey.KEY_URL_MODE, Integer.toString(i));
				e.commit();
			}
		}catch(ClassCastException ex){
		}

		findViewById(R.id.btnCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				MushroomHelper.finish_mush(env,false,"");
			}
		});

		findViewById(R.id.btnSetting).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				MenuDialog.menu_dialog(env);
			}
		});

		findViewById(R.id.btnPicker).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				open_file_picker();
			}
		});
		
		findViewById(R.id.btnCapture).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				open_capture();
			}
		});

		btnEdit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if( btnUpload.isEnabled() ){
					open_editor();
				}
			}
		});

		btnUpload.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				upload_start();
			}
		});

	}

	boolean upload_autostart = false;
	boolean editor_autostart = false;

	void init_page(){
		upload_autostart = env.pref().getBoolean(PrefKey.KEY_AUTO_UPLOAD,false);
		editor_autostart = env.pref().getBoolean(PrefKey.KEY_AUTO_EDIT,false);
		//
		setCurrentFile(0,null);
		
		// 画面回転などからリストアしたなら真
		boolean bRestore = false;
		
		// 画像アプリの共有インテントなどから起動された場合、インテントの指定から画像を選択する
		Intent intent = getIntent();
		if( intent != null ){

			// リストアしたかどうか調べる
			bRestore = intent.getBooleanExtra(PrefKey.EXTRA_IS_STATUS_SAVE,false);
			
			// カメラ画面を呼び出した際のURIを復旧
			String v = intent.getStringExtra(PrefKey.EXTRA_CAPTURE_URI);
			if( v != null ) this.capture_uri = Uri.parse(v);
			
			// 共有インテントで画像を指定されたとか、画像選択後の状態から復旧したとか
			Uri uri = intent.getData();
			if(uri == null){
				Bundle extra = intent.getExtras();
				if( extra != null ){
					uri  = (Uri)extra.get(Intent.EXTRA_STREAM);
				}
			}
			if( uri != null ){
				String path = MushroomHelper.uri_to_path(env,uri);
				if( path != null ) setCurrentFile( (bRestore?MenuDialog.FILE_FROM_RESTORE:MenuDialog.FILE_FROM_PICK),path);
				return;
			}
		}

		// それ以外の場合、設定されていれば自動的にピッカーを開く
		if( !bRestore && env.pref().getBoolean(PrefKey.KEY_AUTO_PICK,false) ) open_file_picker();
	}

	String file_path;
	int open_type;
	Uri capture_uri;
	
	void updateUploadButtonStatus(){
		log.d("uploadButtonStatus selected=%s busy=%s"
			,(file_path != null)
			,(uploader.isBusy())
		);
		btnUpload.setEnabled(file_path != null && !uploader.isBusy() );
		
	}
	
	void save_status(){
		Intent intent = getIntent();
		if( file_path != null ) intent.setData( Uri.fromFile(new File(file_path)));
		intent.putExtra( PrefKey.EXTRA_IS_STATUS_SAVE , true );
		if(capture_uri != null) intent.putExtra( PrefKey.EXTRA_CAPTURE_URI, capture_uri.toString() );
		setIntent(intent);
	}

	void setCurrentFile(int open_type, String path){
		this.file_path = path;
		this.open_type = open_type;
		delay_open.run();
	}

	Runnable delay_open = new Runnable() {
		@Override
		public void run() {
			env.handler.removeCallbacks(delay_open);

			if(isFinishing()) return;

			preview.setVisibility(View.INVISIBLE);
			if( file_path == null ){
				tvFileDesc.setText(getString(R.string.image_not_selected));
				btnEdit.setEnabled(false);
				updateUploadButtonStatus();
				return;
			}else{
				btnEdit.setEnabled(true);
				updateUploadButtonStatus();
			}

			// レイアウトが完了してないならもう少し後で実行する
			int w = preview.getWidth();
			int h = preview.getHeight();
			if( w < 1 || h < 1 ){
				env.handler.postDelayed(delay_open,111);
				return;
			}

			if( open_type == MenuDialog.FILE_FROM_RESTORE ){
				log.d("this is reconstruct. skip autostart..");
			}else{
				// 画像選択後に自動処理が設定されていれば、それを開始する
				if( editor_autostart && open_type == MenuDialog.FILE_FROM_PICK ){
					open_editor();
				}else if( upload_autostart ){
					upload_start();
				}
			}

			// 画像の情報をテキスト表示
			final File file = new File(file_path);
			final String size = TextFormat.formatByteSize(file.length());
			final String mtime = TextFormat.formatTime(act,file.lastModified());
			tvFileDesc.setText(String.format(
				"%s %s\n%s"
				,size
				,mtime
				,file_path
			));

			// プレビュー無効の設定があるかもしれない
			boolean measure_only = env.pref().getBoolean(PrefKey.KEY_DISABLE_PREVIEW,false);
			if( measure_only ){
				preview.setVisibility(View.VISIBLE);
				preview.setImageResource(R.drawable.preview_disabled);
			}

			// 測定とプレビューを開始する
			PreviewLoader.load(env,file_path, measure_only,preview.getWidth(),preview.getHeight(),new PreviewLoader.Callback() {
				@Override
				public void onMeasure(int w, int h) {
					if( act.isFinishing() ) return;
					tvFileDesc.setText(String.format(
							"%s %sx%spx %s\n%s"
							,size
							,w,h
							,mtime
							,file_path
						));
				}

				@Override
				public void onLoad(Bitmap bitmap) {
					if( act.isFinishing() ) return;
					if(bitmap != null ){
						preview.setVisibility(View.VISIBLE);
						preview.setImageBitmap(bitmap);
					}
				}

			});
		}
	};

	// 画像選択画面を開く
	void open_file_picker(){
		try{
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			// open chooser
		//	startActivityForResult(Intent.createChooser(intent,"file picker"),REQ_FILEPICKER);
			startActivityForResult(intent,MenuDialog.REQ_FILEPICKER);
			return;
		}catch(ActivityNotFoundException ex ){
			env.show_toast(true,R.string.picker_missing);
		}
		log.d("open_file_picker :finish");
		MushroomHelper.finish_mush(env,false,"");
	}
	
	void open_capture(){
		try{
			capture_uri = Uri.fromFile(new File(
				ImageTempDir.getTempDir(act.env)
				,String.format("capture-%s",System.currentTimeMillis())
			));
		}catch(Throwable ex){
			ex.printStackTrace();
			return;
		}
		try{
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT,capture_uri);
			startActivityForResult(intent,MenuDialog.REQ_CAPTURE);
			return;
		}catch(ActivityNotFoundException ex ){
			env.show_toast(true,R.string.capture_missing);
		}
	}
	
	// 画像加工画面を開く
	void open_editor(){
		Intent intent = new Intent(ActImgurMush.this,ActArrange.class);
		intent.putExtra(PrefKey.EXTRA_SRC_PATH,file_path);
		startActivityForResult(intent,MenuDialog.REQ_ARRANGE);
	}

	void upload_start(){
		if(isFinishing()) return;

		// 画像が選択されていない
		if( file_path == null ) return;

		// アップロードするアカウントとアルバム
		ImgurAlbum album = upload_target_manager.getSelectedAlbum();
		ImgurAccount account = upload_target_manager.getSelectedAccount();
		// アップロードジョブを登録してアップロード開始
		UploadJob job = new UploadJob(account, album);
		job.addFile(file_path);
		uploader.upload(job);
	}
}
