package de.offis.feelslike.insituarousal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.offis.feelslike.insituarousal.bleservice.BleService;

/**
 * Created by QuincyLappie on 18.07.2017.
 */
public class BleConnectionStateReceiver extends BroadcastReceiver {

    private static final String TAG = "BleConnectionStateRcvr";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(action.equals(BleService.ACTION_GATT_CONNECTED)){
            Log.d(TAG, "Ble connected received!!");
            context.startService(new Intent(context, SignificantHeartRateDetectorService.class));
        } else if(action.equals(BleService.ACTION_GATT_DISCONNECTED)){
            Log.d(TAG, "Ble disconnected received!!");
        }
    }
}
