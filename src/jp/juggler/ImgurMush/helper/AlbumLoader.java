package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.SharedPreferences;

import jp.juggler.ImgurMush.Config;
import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.data.SignedClient;

import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;

public class AlbumLoader {
	static final LogCategory log = new LogCategory("AlbumLoader");

	public interface Callback{
		void onLoad();
	}

	final HelperEnvUI eh;
	final Callback callback;

	public AlbumLoader(HelperEnvUI eh,Callback callback){
		this.eh = eh;
		this.callback = callback;
		eh.lifecycle_manager.add(activity_listener);

		cache_load();

		reload();
	}

	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override public void onNewIntent() {
			reload();
		}
		@Override public void onDestroy() {
			load_thread.joinASync(log,"album loader");
		}
		@Override
		public void onPause() {
			cache_save();
		}
	};

	public void reload() {
		if(load_thread != null ) load_thread.joinASync(log,"album loader");
		load_thread = new LoadThread();
		load_thread.start();
	}

	static final ConcurrentHashMap<String,AlbumList> cache = new ConcurrentHashMap<String,AlbumList>();
	
	public AlbumList findAlbumList(String account_name) {
		if( account_name == null ) return null;
		return cache.get(account_name);
	}
	
	public ImgurAlbum findAlbum(String account_name, String album_id) {
		AlbumList list = findAlbumList(account_name);
		return list==null?null :list.get(album_id);
	}

	void cache_load(){
		// 既にロード済みならロードしない
		if( cache.size() > 0 ) return;
		//
		SharedPreferences pref = eh.pref();
		int n = pref.getInt(PrefKey.KEY_ALBUM_CACHE_COUNT,0);
		for(int i=0;i<n;++i){
			try{
				AlbumList result = new AlbumList();
				String account_name = pref.getString(PrefKey.KEY_ALBUM_CACHE_ACCOUNT_NAME+i,null);
				JSONArray list = new JSONArray(pref.getString(PrefKey.KEY_ALBUM_CACHE_ALBUM_LIST+i,null));
				for(int j=0,je=list.length();j<je;++j){
					result.add( new ImgurAlbum(account_name,list.getJSONObject(j)));
				}
				result.from = AlbumList.FROM_CACHE;
				result.update_map();
				cache.put(account_name,result);
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
	}

	void cache_save(){
		SharedPreferences.Editor e = eh.pref().edit();
		int i=0;
		for( Map.Entry<String,AlbumList> entry : cache.entrySet() ){
			String account_name = entry.getKey();
			AlbumList result = entry.getValue();
			if(result.from == AlbumList.FROM_RESPONSE ){
				try{
					JSONArray json_list = new JSONArray();
					for( ImgurAlbum album : result.iter() ){
						json_list.put( album.toJSON() );
					}
					e.putString( PrefKey.KEY_ALBUM_CACHE_ACCOUNT_NAME+i, account_name );
					e.putString( PrefKey.KEY_ALBUM_CACHE_ALBUM_LIST+i, json_list.toString() );
				}catch(Throwable ex){
					ex.printStackTrace();
				}
			}
			++i;
		}
		e.putInt(PrefKey.KEY_ALBUM_CACHE_COUNT,i);
		e.commit();
	}
	///////////////////////////


	LoadThread load_thread;
	class LoadThread extends WorkerBase{
		AtomicBoolean bCancelled = new AtomicBoolean(false);

		@Override
		public void cancel() {
			bCancelled.set(true);
			notifyEx();
		}

		@Override
		public void run() {
			final HashMap<String,AlbumList> tmp_map = new HashMap<String,AlbumList> ();
			// アカウント一覧をロードする
			final ArrayList<ImgurAccount> account_list = ImgurAccount.loadAll( eh.cr ,bCancelled);
			// 各アカウントのアルバム一覧をロードする
			StringBuilder sb = new StringBuilder();
			for( ImgurAccount account : account_list ){
				if(bCancelled.get()) return;
				AlbumList data = load(account);
				tmp_map.put( account.name, data );
				if( data.from == AlbumList.FROM_ERROR && data.err != null ){
					if(sb.length() > 0 ) sb.append("\n");
					sb.append(account.name);
					sb.append(": ");
					sb.append(data.err);
				}
			}
			if( sb.length() > 0 ){
				eh.show_toast(true,eh.getString(R.string.album_load_error)+"\n"+sb.toString());
			}
			eh.handler.post(new Runnable() {
				@Override
				public void run() {
					if(bCancelled.get()) return;
					for(Map.Entry<String,AlbumList> entry : tmp_map.entrySet() ){
						String key = entry.getKey();
						AlbumList new_result = entry.getValue();
						AlbumList old_result = cache.get(key);
						// エラーレスポンスはあまり有用ではないので、古いデータがエラー以外なら更新しない
						if( new_result.from == AlbumList.FROM_ERROR
						&& old_result != null
						&& old_result.from != AlbumList.FROM_ERROR
						){
							continue;
						}
						cache.put( key, new_result );
					}
					callback.onLoad();
				}
			});
		}

		AlbumList load(ImgurAccount account){
			AlbumList data = new AlbumList();
			String cancel_message = eh.getString(R.string.cancelled);
			data.from = AlbumList.FROM_ERROR;
			try{
				SignedClient client = new SignedClient(eh);
				client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
				//
				APIResult result = client.json_signed_get("http://api.imgur.com/2/account/albums.json?count=999",cancel_message,account.name);
				if( !result.isError() && result.content_json.isNull("albums") ){
					result.setErrorExtra("missing 'albums' in response");
				}
				if( result.isError() ){
					data.err = result.getError();
					if( -1 != data.err.indexOf("No albums found") ){
						// アルバムがないのは正常ケース
						data.from = AlbumList.FROM_RESPONSE;
					}else{
						result.save_error(eh);
					}
				}else{
					JSONArray src_list = result.content_json.getJSONArray("albums");
					for(int j=0,je=src_list.length();j<je;++j){
						JSONObject src = src_list.getJSONObject(j);
						data.add(new ImgurAlbum(account.name,src));
					}
					data.from = AlbumList.FROM_RESPONSE;
				}
			}catch(Throwable ex){
				ex.printStackTrace();
				data.err = String.format("%s: %s",ex.getClass().getSimpleName(),ex.getMessage());
			}
			data.update_map();
			return data;
			// nullを返すと UploadTargetManager の初期化が終わらないことになってしまうので、
		}
	}
}
