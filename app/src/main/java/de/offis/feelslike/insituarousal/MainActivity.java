package de.offis.feelslike.insituarousal;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import de.offis.feelslike.insituarousal.bleservice.BleService;

public class MainActivity extends BleActivity implements View.OnClickListener {

    public static final String PREFERENCES_STUDY_RUNNING = "study_running";

    private Intent intent;
    private PendingIntent pendingIntent;
    private AlarmManager alarm;

    boolean blhrCalculationRunning;
    ArrayList<Double> blhrCalculationValues;
    double blhrMean;

    @Override
    void handlePushNotification(Intent intent) {
        Log.d(TAG, "###### handle push notification");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        blhrMean = 0.0;
        blhrCalculationRunning = false;

        Button startBLHRCalculation = (Button)findViewById(R.id.startBaselineHRCalculation);
        startBLHRCalculation.setOnClickListener(this);

        Button stopBLHRCalculation = (Button)findViewById(R.id.stopBaselineHRCalculation);
        stopBLHRCalculation.setOnClickListener(this);

        Button startStudy = (Button)findViewById(R.id.startStudy);
        startStudy.setOnClickListener(this);

        Button stopStudy = (Button)findViewById(R.id.stopStudy);
        stopStudy.setOnClickListener(this);

//        if(Utils.isServiceRunning(BleService.class, this)){
//            // Deactivate "Start study", if study is already running
//            startStudy.setEnabled(false);
//            stopStudy.setEnabled(true);
//        } else{
//            // Deactivate "Stop study", if study isn't running at the moment
//            startStudy.setEnabled(true);
//            stopStudy.setEnabled(false);
//        }

//        Button arousal = (Button)findViewById(R.id.arousal);
//        arousal.setOnClickListener(this);

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        int userID = sharedPref.getInt("uid", 0);

        EditText txtUid = (EditText)this.findViewById(R.id.editTextUid);
        txtUid.setText(userID+"");

        // Disable according ui elements, if study is already running
        if(mPreferences.getBoolean(PREFERENCES_STUDY_RUNNING, false)){
            txtUid.setEnabled(false);
            startStudy.setEnabled(false);
            stopStudy.setEnabled(true);
        } else{
            txtUid.setEnabled(true);
            startStudy.setEnabled(true);
            stopStudy.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Dis-/Enable BLHR Calculation Start and Stop button accordingly
        Button startBLHRCalculation = (Button)findViewById(R.id.startBaselineHRCalculation);
        Button stopBLHRCalculation = (Button)findViewById(R.id.stopBaselineHRCalculation);
        startBLHRCalculation.setEnabled(!blhrCalculationRunning);
        stopBLHRCalculation.setEnabled(blhrCalculationRunning);

        // If user paused app during BLHR calculation, restart BLHR calculation
        if(blhrCalculationRunning){
            blhrCalculationValues = new ArrayList<>();

            // Register receiver for broadcasts from BleService and ActivityDetectionService
            IntentFilter filter = new IntentFilter();
            filter.addAction(BleService.ACTION_DATA_AVAILABLE);
            registerReceiver(broadcastReceiverBLHR, filter);

            Toast toast = Toast.makeText(getApplicationContext(),
                    "Restarted baseline heart rate calculation...", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // If user paused app during BLHR calculation, unregister according receiver
        if(blhrCalculationRunning){
            unregisterReceiver(broadcastReceiverBLHR);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startBaselineHRCalculation:{
                if(!blhrCalculationRunning){
                    blhrCalculationValues = new ArrayList<>();
                    blhrCalculationRunning = true;

                    // Register receiver for broadcasts from BleService and ActivityDetectionService
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(BleService.ACTION_DATA_AVAILABLE);
                    registerReceiver(broadcastReceiverBLHR, filter);

                    // Dis-/Enable BLHR Calculation Start and Stop button accordingly
                    Button startBLHRCalculation = (Button)findViewById(R.id.startBaselineHRCalculation);
                    Button stopBLHRCalculation = (Button)findViewById(R.id.stopBaselineHRCalculation);
                    startBLHRCalculation.setEnabled(!blhrCalculationRunning);
                    stopBLHRCalculation.setEnabled(blhrCalculationRunning);

                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Started baseline heart rate calculation...", Toast.LENGTH_SHORT);
                    toast.show();
                }
                break;
            }
            case R.id.stopBaselineHRCalculation:{
                if(blhrCalculationRunning){
                    // Release BroadcastReceiver and Activity-API-Connection
                    unregisterReceiver(broadcastReceiverBLHR);
                    blhrCalculationRunning = false;

                    // Dis-/Enable BLHR Calculation Start and Stop button accordingly
                    Button startBLHRCalculation = (Button)findViewById(R.id.startBaselineHRCalculation);
                    Button stopBLHRCalculation = (Button)findViewById(R.id.stopBaselineHRCalculation);
                    startBLHRCalculation.setEnabled(!blhrCalculationRunning);
                    stopBLHRCalculation.setEnabled(blhrCalculationRunning);

                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Stopped baseline heart rate calculation.", Toast.LENGTH_SHORT);
                    toast.show();
                }
                break;
            }
            case R.id.startStudy: {
                this.saveUid();

                if (super.mService.getConnectionState() == BleService.STATE_CONNECTED) {
                    // En-/Disable ui elements
                    EditText txtUid = (EditText)this.findViewById(R.id.editTextUid);
//                    EditText txtBaselineHeartRate = (EditText)this.findViewById(R.id.editTextBaselineHeartRate);
                    Button startStudy = (Button)findViewById(R.id.startStudy);
                    Button stopStudy = (Button)findViewById(R.id.stopStudy);

                    startStudy.setEnabled(false);
                    stopStudy.setEnabled(true);
                    txtUid.setEnabled(false);
//                    txtBaselineHeartRate.setEnabled(false);

                    // Mark in preferences, that a study is currently running
                    mPreferences.edit().putBoolean(PREFERENCES_STUDY_RUNNING, true).apply();

                    // Start analysis and questionnaire notification service
                    startService(new Intent(MainActivity.this, AnalysisAndNotificationService.class));

                    Toast toast = Toast.makeText(getApplicationContext(), "Study started.", Toast.LENGTH_SHORT);
                    toast.show();

                    // Write initial data to file (baseline heart rate, start time, id)
                    logInitialData(getLogStorageDir(MainActivity.this, "feelslikeinsitu"));
                } else{
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Could not start study, because no belt is connected.", Toast.LENGTH_LONG);
                    toast.show();
                }
                break;
            }
            case R.id.stopStudy: {
                new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Terminating Study")
                        .setMessage("Are you sure you want to finish the current study?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Mark in preferences, that a study is currently running
                                mPreferences.edit().putBoolean(PREFERENCES_STUDY_RUNNING, false).apply();

                                // En-/Disable ui elements
                                EditText txtUid = (EditText)findViewById(R.id.editTextUid);
//                                EditText txtBaselineHeartRate = (EditText)findViewById(R.id.editTextBaselineHeartRate);
                                Button startStudy = (Button)findViewById(R.id.startStudy);
                                Button stopStudy = (Button)findViewById(R.id.stopStudy);

                                startStudy.setEnabled(true);
                                stopStudy.setEnabled(false);
                                txtUid.setEnabled(true);
//                                txtBaselineHeartRate.setEnabled(true);

                                Toast toast = Toast.makeText(getApplicationContext(),
                                        "Study stopped", Toast.LENGTH_SHORT);
                                toast.show();

//                mService.disconnect();
//                super.unbindService(super.mServiceConnection);
//                stopService(new Intent(this, BleService.class));
//                stopService(new Intent(this, AnalysisAndNotificationService.class));
                                stopService(new Intent(MainActivity.this, AnalysisAndNotificationService.class));

                            }

                        })
                        .setNegativeButton("No", null)
                        .show();
                break;
            }
        }
    }

    private final BroadcastReceiver broadcastReceiverBLHR = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals(BleService.ACTION_DATA_AVAILABLE)) {
                String heartRate = intent.getStringExtra(BleService.EXTRA_DATA);
                blhrCalculationValues.add(Double.parseDouble(heartRate));

                // Calculate received heart rates average
                blhrMean = 0.0;
                for(Double value : blhrCalculationValues){
                    blhrMean += value;
                }
                blhrMean /= blhrCalculationValues.size();

                // Display current received heart rate value and calculated average
                // baseline heart rate value
                TextView textViewBLHRResult = (TextView) findViewById(R.id.textViewBLHRResult);
                textViewBLHRResult.setText("BLHR-C: " + heartRate + "\nBLHR-M: " + (int)blhrMean);
                Log.d(TAG, "heartRate: " + heartRate);
            }
        }
    };

    private void saveUid() {
        EditText txt = (EditText)this.findViewById(R.id.editTextUid);
        int uid = Integer.parseInt(txt.getText().toString());
        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("uid", uid);
        editor.commit();
    }

    private void logInitialData(File directory) {
        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        int userID = sharedPref.getInt("uid", 0);
        File file = new File(directory, "feelslikeinsitu_" + userID +".log");

        try {
            FileOutputStream fOut = new FileOutputStream(file, true);

            OutputStreamWriter osw = new OutputStreamWriter(fOut);
            try {
                osw.write(  userID + ";" +
                            blhrMean + ";" +
                            System.currentTimeMillis()+"\n");
                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Toast toast = Toast.makeText(this, "Adding log to: " + file.getAbsolutePath(), Toast.LENGTH_LONG);
        toast.show();
    }

    public File getLogStorageDir(Context context, String dirName) {
        File file = new File(context.getExternalFilesDir(
                null), dirName);
        if (!file.mkdirs()) {
            Log.e("LoggingActivity", "Directory not created");
        }
        return file;
    }

    private String getCurrentTime() {
        Time today = new Time(Time.getCurrentTimezone());
        today.setToNow();

        return today.format("%Y%m%dT%H%M%S");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(!mPreferences.getBoolean(MainActivity.PREFERENCES_STUDY_RUNNING, false)){
            if (mService.getConnectionState() == BleService.STATE_CONNECTED) {
                super.disconnectDevice();
            }
            stopService(new Intent(this, BleService.class));
        }
//        if(!mPreferences.getBoolean(MainActivity.PREFERENCES_STUDY_RUNNING, false)){
//            if (mService.getConnectionState() == BleService.STATE_CONNECTED) {
//                disconnectDevice();
//            } else {
//                discoverDevices();
//            }
//        }
    }
}
