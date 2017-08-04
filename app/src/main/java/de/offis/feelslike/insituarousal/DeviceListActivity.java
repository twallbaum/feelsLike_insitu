package de.offis.feelslike.insituarousal;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.offis.feelslike.insituarousal.bleservice.GattAttributes;

public class DeviceListActivity extends Activity {

    private static final int REQUEST_ENABLE_BT_FOR_DISCOVERY = 1;
    static final int REQUEST_CONNECT_BLE = 2;
    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 100;
    static final String EXTRA_DEVICE_ADDRESS = "de.vonniebelschuetz.ble.device_address";
    private static final long SCAN_PERIOD = 60000;
    private final String TAG = this.getClass().getName();
    private ArrayAdapter<String> mNewDevicesArrayAdapter;
    private BluetoothAdapter mAdapter;
    private BluetoothLeScanner mScanner;
    private Button mScanButton;

    private ScanCallback mLeScanCallback = new ScanCallback() {

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    Log.e(TAG, "onScanFailed( SCAN_FAILED_ALREADY_STARTED )");
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    Log.e(TAG, "onScanFailed( SCAN_FAILED_APPLICATION_REGISTRATION_FAILED )");
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    Log.e(TAG, "onScanFailed( SCAN_FAILED_INTERNAL_ERROR )");
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG, "onScanFailed( SCAN_FAILED_FEATURE_UNSUPPORTED )");
                    break;
                default:
                    Log.e(TAG, "onScanFailed( ERROR CODE " + errorCode + " )");
            }
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String deviceString = device.getName() + "\n" + device.getAddress();

            // only add device if it is not yet in the list
            boolean add = true;
            if (!mNewDevicesArrayAdapter.isEmpty()) {
                for (int i = 0; i < mNewDevicesArrayAdapter.getCount(); i++) {
                    if (mNewDevicesArrayAdapter.getItem(i).equals(deviceString)) {
                        add = false;
                    }
                }
            }
            if (add) {
                mNewDevicesArrayAdapter.add(deviceString);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (android.bluetooth.le.ScanResult result : results) {
                super.onBatchScanResults(results);
                BluetoothDevice device = result.getDevice();
                String deviceString = device.getName() + "\n" + device.getAddress();

                // only add device if it is not yet in the list
                boolean add = true;
                if (!mNewDevicesArrayAdapter.isEmpty()) {
                    for (int i = 0; i < mNewDevicesArrayAdapter.getCount(); i++) {
                        if (mNewDevicesArrayAdapter.getItem(i).equals(deviceString)) {
                            add = false;
                        }
                    }
                }
                if (add) {
                    mNewDevicesArrayAdapter.add(deviceString);
                }
            }
        }
    };

    /**
     * Broadcast Receiver to handle new device discovery and end of discovery from BTAdapter
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothDevice.ACTION_FOUND:
                    // Get the device object from the intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (!(device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC)) {
                        mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    setProgressBarIndeterminateVisibility(false);
                    setTitle(R.string.select_device);
                    if (mNewDevicesArrayAdapter.getCount() == 0) {
                        String noDevices = getResources().getText(R.string.none_found).toString();
                        mNewDevicesArrayAdapter.add(noDevices);
                    }
                    break;
            }
        }
    };

    /**
     * handle clicks on discovered device entries in the list
     */
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // cancel discovery because its costly and we don't need it anymore
            stopScan();

            // get MAC address of device, which is the last 17 chars in the view
            String info = ((TextView) view).getText().toString();
            String address = info.substring(info.length() - 17);

            // create the result intent and include MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            // set result and finish activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        // set up the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_list);

        // set default result in case the user cancels
        setResult(Activity.RESULT_CANCELED, new Intent());

        // Initialize the button to perform device discovery
        mScanButton = (Button) findViewById(R.id.button_scan);
        mScanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery(true);
                v.setEnabled(false);
            }
        });

        // Initialize array adapter for discovered devices
        mNewDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

        // Find and set up the ListView for newly discovered devices
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        // Register for broadcasts when discovery has finished
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // get the Bluetooth adapter
        final BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdapter = manager.getAdapter();
        mScanner = mAdapter.getBluetoothLeScanner();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop()");
        super.onStop();

        // Make sure we're not doing discovery anymore
        if (mAdapter != null) {
            doDiscovery(false);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();

        // unregister broadcast receivers
        this.unregisterReceiver(mReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            Log.i(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", "+ data.toUri(0) + ")");
        } else {
            Log.i(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", null)");
        }
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT_FOR_DISCOVERY:
                if (resultCode == RESULT_OK) {
                    // continue with discovery
                    doDiscovery(true);
                } else {
                    finish();
                }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult(" + String.valueOf(requestCode) + ", " + Arrays.toString(permissions) + Arrays.toString(grantResults) + ")");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    doDiscovery(true);
                } else {
                    finish();
                }
                break;
        }
    }

    private void doDiscovery(boolean enable) {
        Log.i(TAG, "doDiscovery( " + enable + " )");

        // check if BT is enabled, ask to otherwise
        if (!mAdapter.isEnabled() && enable) {
            Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // The REQUEST_ENABLE_BT_FOR_DISCOVERY constant passed to startActivityForResult() is a locally defined integer (which must be greater than 0), that the system passes back to you in your onActivityResult()
            // implementation as the requestCode parameter.

            startActivityForResult(intentBtEnabled, REQUEST_ENABLE_BT_FOR_DISCOVERY);
        }

        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // check for COARSE_LOCATION permission, needed from API 23 and up
        if (Build.VERSION.SDK_INT >= 23) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]
                        {   Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, //{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
            }
        }

        Handler mHandler = new Handler();
        if (mScanner != null) {
            if (enable) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopScan();
                    }
                }, SCAN_PERIOD);

                List<ScanFilter> filters = new ArrayList<>();
                filters.add(new ScanFilter.Builder().setServiceUuid(
                        ParcelUuid.fromString(GattAttributes.HEART_RATE_SERVICE)).build());
                Log.d(TAG, "ParcelUUID: " + ParcelUuid.fromString(GattAttributes.HEART_RATE_SERVICE).toString());
                ScanSettings settings = new ScanSettings.Builder().build();
                mScanner.startScan(filters, settings, mLeScanCallback);

                Log.d(TAG, "doDiscovery()");

                // Indicate scanning in the title
                setProgressBarIndeterminateVisibility(true);
                setTitle(R.string.scanning);

                // Turn on sub-title for new devices
                findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

            } else {
                stopScan();
            }
        } else {
            Log.e(TAG, "BluetoothScanner not found!");
        }
    }

    /**
     * stops a ble scan and changes UI components according to its result
     *
     */
    private void stopScan() {
        Log.i(TAG, "stopScan()");
        mScanner.stopScan(mLeScanCallback);

        setProgressBarIndeterminateVisibility(false);
        setTitle(R.string.select_device);
        mScanButton.setEnabled(true);

        if (mNewDevicesArrayAdapter.isEmpty()) {
            String noDevices = getResources().getText(R.string.none_found).toString();
            mNewDevicesArrayAdapter.add(noDevices);
        }
    }
}
