package jp.juggler.ImgurMush.helper;

import jp.juggler.ImgurMush.R;
import jp.juggler.util.DialogManager;
import jp.juggler.util.LifeCycleManager;
import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.Toast;

public class BaseActivity extends Activity {
	public static final LogCategory log = new LogCategory("Activity");

	public Handler ui_handler;
	public LayoutInflater inflater;
	public ContentResolver cr;
	public LifeCycleManager lifecycle_manager = new LifeCycleManager();
	public DialogManager dialog_manager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ui_handler = new Handler();
		inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		cr =getContentResolver();
		dialog_manager = new DialogManager(this);
	}

	@Override protected void onDestroy() {
		super.onDestroy();
		dialog_manager.onDestroy();
		lifecycle_manager.fire_onDestroy();
	}

	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		lifecycle_manager.fire_onNewIntent();
	}

	@Override protected void onStart() {
		super.onStart();
		lifecycle_manager.fire_onStart();
	}

	@Override protected void onRestart() {
		super.onRestart();
		lifecycle_manager.fire_onRestart();
	}

	@Override protected void onStop() {
		super.onStop();
		lifecycle_manager.fire_onStop();
	}

	@Override protected void onResume() {
		super.onResume();
		lifecycle_manager.fire_onResume();
	}

	@Override protected void onPause() {
		super.onPause();
		lifecycle_manager.fire_onPause();
	}

	SparseBooleanArray pressed_key = new SparseBooleanArray();

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( event.getAction() == KeyEvent.ACTION_DOWN ){
			switch(keyCode){
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				pressed_key.put(keyCode,true);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if( event.getAction() == KeyEvent.ACTION_UP && pressed_key.get(keyCode) ){
			pressed_key.delete(keyCode);
			switch(keyCode){
			case KeyEvent.KEYCODE_BACK:
				procBackKey();
				return true;
			case KeyEvent.KEYCODE_MENU:
				procMenuKey();
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}
	
	protected void  procMenuKey() {
	}

	protected void  procBackKey() {
		finish();
	}
	
	//////////////////////////////////////////////////

	public boolean isUIThread(){
		 return Thread.currentThread().equals(getMainLooper().getThread());
	}
	
	public void show_toast(final boolean bLong,final int resid,final Object... args){
		if( !isUIThread() ){
			ui_handler.post(new Runnable() {
				@Override public void run() {
					show_toast(bLong,resid,args);
				}
			});
			return;
		}
		try{
			Toast.makeText(BaseActivity.this,getString(resid,args),(bLong?Toast.LENGTH_LONG:Toast.LENGTH_SHORT)).show();
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	public void show_toast(final boolean bLong,final String text){
		if( !isUIThread() ){
			ui_handler.post(new Runnable() {
				@Override public void run() {
					show_toast(bLong,text);
				}
			});
			return;
		}
		try{
			Toast.makeText(BaseActivity.this,text,(bLong?Toast.LENGTH_LONG:Toast.LENGTH_SHORT)).show();
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	public void finish_with_message(final String msg){
		if( !isUIThread() ){
			ui_handler.post(new Runnable() {
				@Override public void run() {
					finish_with_message(msg);
				}
			});
			return;
		}
		try{
			if(isFinishing()) return;
			//
			Dialog dialog =  new AlertDialog.Builder(this)
			.setCancelable(true)
			.setNegativeButton(R.string.close,null)
			.setMessage(msg)
			.create();
			//
			dialog.setOnDismissListener(new OnDismissListener() {
				@Override public void onDismiss(DialogInterface dialog) {
					finish();
				}
			});
			//
			dialog_manager.show_dialog(dialog);
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	public void report_ex(Throwable ex){
		ex.printStackTrace();
		show_toast(true,String.format("%s: %s",ex.getClass().getSimpleName(),ex.getMessage()));
	}

	public SharedPreferences pref(){
		return getPref(this);
	}

	public static SharedPreferences getPref(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context);
	}
}
