package de.offis.feelslike.insituarousal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by QuincyLappie on 21.07.2017.
 */
public class TestService extends Service {

    private int testCounter = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Thread t = new Thread(){
            @Override
            public void run() {
                while(true){
                    Log.d("TestThread", "still running " + testCounter++);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t.start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("TestService", "onDestroy");
    }
}
