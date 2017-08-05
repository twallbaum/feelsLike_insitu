package de.offis.feelslike.insituarousal;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toolbar;

import java.util.UUID;

import de.offis.feelslike.insituarousal.bleservice.BleService;
import de.offis.feelslike.insituarousal.bleservice.GattAttributes;

import static de.offis.feelslike.insituarousal.bleservice.BleService.EXTRA_DATA;

/**
 * Created by niebelschuetz on 17.07.16.
 *
 * Activity that connects with a BLE heart rate monitor through a bound service,
 * provides reconnection capabilities and a user interface for communication with it
 *
 */

// TODO handle reconnection attempt timeout (after 10s)
// TODO think receivers through again
public abstract class BleActivityV1 extends AppCompatActivity {
    protected final String TAG = getClass().getName();

    protected BleService mService;
    private boolean mBound;

    private ProgressDialog mProgressDialog;
    SharedPreferences mPreferences;
    ActionBar mActionBar;
    private BleBroadcastReceiver mBleActivityReceiver = new BleBroadcastReceiver();
    private boolean bleActivityReceiverIsRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");

        setContentView(R.layout.activity_ble);

        bleActivityReceiverIsRegistered = false;

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mProgressDialog = new ProgressDialog(this);
        setActionBar((Toolbar) findViewById(R.id.toolbar));
        mActionBar = getActionBar();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart()");

//        setupServiceAndReceiver();
//        resetHearRateViews();
    }

    protected void setupServiceAndReceiver(){
        // register for relevant broadcasts
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTING);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTING);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        //intentFilter.addAction(Config.ACTION_PUSH_NOTIFICATION);

        // start BleService
        Intent bleServiceIntent = new Intent(this, BleService.class);
        startService(bleServiceIntent);
        // when only bindService, than still running when minimized, but closing when activity closed
        // Service not destroyed. seems to just not receive heart rate measurements anymore. Connection closed/disconnected?
        //      never onDestroy gets called, but the started threads stop. strange....
        // onStartCommand always called when app is brought to foreground, because always startService gets called.
        //      But there should be no problem about it, because the service doesn't get resetted or so,
        //      just onStartCommand with the given intent gets called.
        bindService(bleServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mBleActivityReceiver, intentFilter);
        bleActivityReceiverIsRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");

        if(mPreferences.getBoolean(MainActivity.PREFERENCES_STUDY_RUNNING, false)){
            setupServiceAndReceiver();
        }

        // clearing the notification tray
        //NotificationUtils.clearNotifications();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause()");
        super.onPause();

        if(mPreferences.getBoolean(MainActivity.PREFERENCES_STUDY_RUNNING, false)){
            // unregister receiver
            // First check, if the service is registered at all
            if(bleActivityReceiverIsRegistered){
                unregisterReceiver(mBleActivityReceiver);
                bleActivityReceiverIsRegistered = false;
            }

            // unbind BleService
            if (mBound) {
                unbindService(mServiceConnection);
            }
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");

        // time for a new try
        mPreferences.edit().putBoolean(getString(R.string.pref_key_reconnect), true).apply();

        // dismiss any open progress dialog
        // TODO check if this is really needed
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    protected ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected()");
            BleService.LocalBinder binder = (BleService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            Log.i(TAG, "Service successfully connected");
            if (mPreferences.getBoolean(getString(R.string.pref_key_reconnect), true)) {
                reconnectDevice();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected()");
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putString(getString(R.string.pref_key_heart_rate), String.valueOf(-1));
            mService = null;
            mBound = false;
        }
    };

    private void reconnectDevice() {
        String lastDevice = mPreferences.getString(getResources().getString(R.string.pref_key_last_connected_device), null);
        int connectionState = mService.getConnectionState();
        if (lastDevice != null && connectionState == BleService.STATE_DISCONNECTED) {
            Log.i(TAG, "reconnecting…");
            connectDevice(lastDevice);
        }
    }

    private void connectDevice(String deviceAdress) {
        Log.i(TAG, "connectDevice(" + deviceAdress + ")");
        mService.connect(deviceAdress);
    }

    private void showConnectionProgress(BluetoothDevice device) {
        mProgressDialog.setTitle(R.string.label_connecting);
        mProgressDialog.setMessage( getResources().getString(
                R.string.message_connecting, device.getName() ) );
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(mOnProgressDialogCanceledListener);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
    }

    private DialogInterface.OnCancelListener mOnProgressDialogCanceledListener = new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            stopReconnect();
        }
    };

    void updateHeartRateViews(String hr) {
        if (!(hr == null)) {
            // set the Activity's subtitle to match the hr value
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            if (mActionBar != null) {
                mActionBar.setSubtitle(String.valueOf(hr));
            } else if (toolbar != null) {
                toolbar.setSubtitle(String.valueOf(hr));
                toolbar.setSubtitleTextColor(Color.WHITE);
            }
        }
    }

    void resetHearRateViews() {
        if (mActionBar != null) {
            mActionBar.setSubtitle("no heart rate");
            mActionBar.setTitle("Chats");
        }
    }

    private void stopReconnect() {
        Log.i(TAG, "stopReconnect()");
        mPreferences.edit().putBoolean(getString(R.string.pref_key_reconnect), false).apply();
        mService.disconnect();
    }

    protected void disconnectDevice() {
        Log.i(TAG, "disconnectDevice()");
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.remove(getString(R.string.pref_key_last_connected_device));
        editor.putBoolean(getString(R.string.pref_key_reconnect), false);
        editor.putString(getString(R.string.pref_key_heart_rate), String.valueOf(-1));
        editor.apply();
        mService.disconnect();
    }

    protected void discoverDevices() {
        Log.i(TAG, "discoverDevices()");
        // Launch DeviceListActivity to scan for ble devices and select one or disconnect
        Intent connectIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(connectIntent, DeviceListActivity.REQUEST_CONNECT_BLE);
    }

    abstract void handlePushNotification(Intent intent);



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsmenu()");
        getMenuInflater().inflate(R.menu.menu_ble, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.i(TAG, "onPrepareOptionsMenu(" + menu.toString() + ")");
        MenuItem bleConnect = menu.findItem(R.id.menu_connect);
        if (mService != null && bleConnect != null) {
            switch (mService.getConnectionState()) {
                case BleService.STATE_CONNECTED:
                    bleConnect.setTitle(R.string.label_disconnect);
                    bleConnect.setEnabled(true);
                    break;
                case BleService.STATE_DISCONNECTED:
                    bleConnect.setTitle(R.string.label_connect);
                    bleConnect.setEnabled(true);
                    break;
                case BleService.STATE_CONNECTING:
                case BleService.STATE_DISCONNECTING:
                default:
                    bleConnect.setEnabled(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "onOptionsItemSelected( " + item.getTitleCondensed() + " )");
        switch (item.getItemId()) {
//            case R.id.menu_preferences:
//                Intent settingsIntent = new Intent(this, SettingsActivity.class);
//                startActivity(settingsIntent);
//                return true;
//            case R.id.menu_connect:
//                if (mService.getConnectionState() == BleService.STATE_CONNECTED) {
//                    disconnectDevice();
//                } else {
//                    discoverDevices();
//                }
//                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            Log.i(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", "+ data.toUri(0) + ")");
        } else {
            Log.i(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", null)");
        }

        switch (requestCode) {
            case DeviceListActivity.REQUEST_CONNECT_BLE:
                if (resultCode == RESULT_OK && data != null) {
                    // device successfully discovered, now connect to it
                    connectDevice(data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS));
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    Log.i(TAG, "Device selection canceled");
                } else {
                    Log.e(TAG, "Bad result");
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    class BleBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive(" + intent.toUri(0) + ")");
            switch (intent.getAction()) {
                case BleService.ACTION_GATT_CONNECTED: {
                    Log.i(TAG, "onReceive(ACTION_DEVICE_CONNECTED)");
                    invalidateOptionsMenu();
                    BluetoothDevice connected_device = intent.getParcelableExtra(EXTRA_DATA);
                    mPreferences.edit().putString(getResources().getString(R.string.pref_key_last_connected_device),
                            connected_device.getAddress()).apply();
                    mPreferences.edit().putBoolean(getString(R.string.pref_key_reconnect), true).apply();
                    break;
                }
                case BleService.ACTION_GATT_DISCONNECTED: {
                    resetHearRateViews();
                    invalidateOptionsMenu();
                    break;
                }
                case BleService.ACTION_GATT_CONNECTING: {
                    BluetoothDevice connecting_device = intent.getParcelableExtra(EXTRA_DATA);
                    showConnectionProgress(connecting_device);
                    break;
                }
                case BleService.ACTION_GATT_DISCONNECTING: {
                    invalidateOptionsMenu();
                    break;
                }
                case BleService.ACTION_DATA_AVAILABLE: {
                    // sensor is transmitting, dismiss connection dialog
                    if (mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }
                    String my_heart_rate = intent.getStringExtra(EXTRA_DATA);
                    updateHeartRateViews(my_heart_rate);
                    mPreferences.edit().putString(getResources().getString(R.string.pref_key_heart_rate), my_heart_rate).apply();
                    break;
                }
                case BleService.ACTION_GATT_SERVICES_DISCOVERED: {
                    // get GATT heart rate service
                    // TODO maybe de-couple this more
                    BluetoothGattService hr_service =
                            mService.getService(UUID.fromString(GattAttributes.HEART_RATE_SERVICE));
                    if (hr_service != null) {
                        BluetoothGattCharacteristic hr_measurement_characteristic
                                = hr_service.getCharacteristic(UUID.fromString(GattAttributes.HEART_RATE_MEASUREMENT));
                        if (hr_measurement_characteristic != null) {
                            // found the characteristic, set up notification
                            mService.setCharacteristicNotification(hr_measurement_characteristic, true);
                        }
                    } else {
                        Log.d(TAG, "hr_service is null.");
                    }
                    break;
                }
//                case Config.ACTION_PUSH_NOTIFICATION: {
//                    // new push notification is received
//                    handlePushNotification(intent);
//                    break;
//                }
                default: {
                    Log.e(TAG, "onReceive( UNKNOWN ACTION )");
                }
            }
        }
    }
}
