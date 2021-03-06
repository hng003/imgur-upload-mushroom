package jp.juggler.util;

public abstract class WorkerBase extends Thread {

	// 継承するクラスはcancel() の実装が必要
	public abstract void cancel();

	// 継承するクラスはrun() の実装が必要
	@Override
	public abstract void run();


	// cancel() を連打しつつjoin待機
	public void joinLoop(LogCategory log,String caption){
		long timeNextWarn = System.currentTimeMillis()+2000;
		for(;;){
			cancel();

			try{ join(100); }catch(InterruptedException ex){}

			if(!isAlive()) break;

			// 待機状態が長い場合、定期的にログ出力
			long now = System.currentTimeMillis();
			if(now >= timeNextWarn ){
				timeNextWarn = now + 5000;
				log.w("waiting end of %s",caption);
			}
		}
		// もう死んでいるはずだが、リソースを確実に解放させるためにもう一度呼んでおく
		try{ join(100); }catch(Throwable ex){}
	}

	// joinLoopを別スレッドから起動する
	public void joinASync(final LogCategory log,final String caption){
		// 呼び出し元スレッドからキャンセルをかけておく
		cancel();
		// 作成したスレッドで終了待機を行う
		new Thread(){
			@Override
			public void run() {
				joinLoop(log,caption);
			}

		}.start();
	}

	/////////////////////////////////////
	// 毎回catchを書くのが面倒なので用意した補助メソッド

	public synchronized void notifyEx(){ notify(); }
	public synchronized void waitEx(long ms){
		try{ wait(ms); }catch(InterruptedException ex){}
	}
	public synchronized void waitEx(){
		try{ wait(); }catch(InterruptedException ex){}
	}


	public static void notify_object(Object o){
		synchronized(o){ o.notify(); }
	}
	public static void wait_object(Object o){
		synchronized(o){
			try{ o.wait(); }catch(InterruptedException ex){}
		}
	}
	public static void wait_object(Object o,long ms){
		synchronized(o){
			try{ o.wait(ms); }catch(InterruptedException ex){}
		}
	}

}
