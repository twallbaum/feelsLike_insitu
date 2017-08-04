package de.offis.feelslike.insituarousal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import de.offis.feelslike.insituarousal.bleservice.BleService;

public class MainActivity extends BleActivity implements View.OnClickListener {

    public static final String PREFERENCES_STUDY_RUNNING = "study_running";

    private Intent intent;
    private PendingIntent pendingIntent;
    private AlarmManager alarm;

    @Override
    void handlePushNotification(Intent intent) {
        Log.d(TAG, "###### handle push notification");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startStudy = (Button)findViewById(R.id.starteStudie);
        startStudy.setOnClickListener(this);

        Button stopStudy = (Button)findViewById(R.id.stopStudie);
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

        Button arousal = (Button)findViewById(R.id.arousal);
        arousal.setOnClickListener(this);

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        int userID = sharedPref.getInt("uid", 0);

        EditText txtUid = (EditText)this.findViewById(R.id.editTextUid);
        txtUid.setText(userID+"");

        EditText txtBaselineHeartRate = (EditText)this.findViewById(R.id.editTextBaselineHeartRate);
        txtBaselineHeartRate.setText("60");

        // Disable according ui elements, if study is already running
        if(mPreferences.getBoolean(PREFERENCES_STUDY_RUNNING, false)){
            txtUid.setEnabled(false);
            txtBaselineHeartRate.setEnabled(false);
            startStudy.setEnabled(false);
            stopStudy.setEnabled(true);
        } else{
            txtUid.setEnabled(true);
            txtBaselineHeartRate.setEnabled(true);
            startStudy.setEnabled(true);
            stopStudy.setEnabled(false);
        }
//        startService(new Intent(this, TestService.class));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.starteStudie: {
                this.saveUid();
//                InputService.start(this);
                // En-/Disable ui elements
                EditText txtUid = (EditText)this.findViewById(R.id.editTextUid);
                EditText txtBaselineHeartRate = (EditText)this.findViewById(R.id.editTextBaselineHeartRate);
                Button startStudy = (Button)findViewById(R.id.starteStudie);
                Button stopStudy = (Button)findViewById(R.id.stopStudie);

                startStudy.setEnabled(false);
                stopStudy.setEnabled(true);
                txtUid.setEnabled(false);
                txtBaselineHeartRate.setEnabled(false);

                // Mark in preferences, that a study is currently running
                mPreferences.edit().putBoolean(PREFERENCES_STUDY_RUNNING, true).apply();

                Context context = getApplicationContext();
                CharSequence text = "study started";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                super.setupServiceAndReceiver();
                super.discoverDevices();

                Log.d(TAG, "study started");

                // Put to onResult...
                // Write initial data to file (baseline heart rate, start time, id)
                logInitialData(getLogStorageDir(MainActivity.this, "feelslikeinsitu"));
                break;
            }
            case R.id.stopStudie: {
                // En-/Disable ui elements
                EditText txtUid = (EditText)this.findViewById(R.id.editTextUid);
                EditText txtBaselineHeartRate = (EditText)this.findViewById(R.id.editTextBaselineHeartRate);
                Button startStudy = (Button)findViewById(R.id.starteStudie);
                Button stopStudy = (Button)findViewById(R.id.stopStudie);

                startStudy.setEnabled(true);
                stopStudy.setEnabled(false);
                txtUid.setEnabled(true);
                txtBaselineHeartRate.setEnabled(true);
//                InputService.stop(this);
                if(mPreferences.getBoolean(PREFERENCES_STUDY_RUNNING, false)){
//                    if (mService.getConnectionState() == BleService.STATE_CONNECTED) {
//                        disconnectDevice();
//                    }
                    super.unbindService(super.mServiceConnection);
                }
                // Mark in preferences, that a study is currently running
                mPreferences.edit().putBoolean(PREFERENCES_STUDY_RUNNING, false).apply();

                stopService(new Intent(this, BleService.class));
                stopService(new Intent(this, AnalysisAndNotificationService.class));

                Context context = getApplicationContext();
                CharSequence text = "study ended";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                break;
            }
            case R.id.arousal: {
                Intent intent = new Intent(this, QuestionnaireActivity.class);
                startActivity(intent);
                break;
            }
        }
    }

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
                            ((EditText)this.findViewById(R.id.editTextBaselineHeartRate)).getText() + ";" +
                            System.currentTimeMillis()+"\n");
                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Toast toast = Toast.makeText(this, "Added answers to: " + file.getAbsolutePath(), Toast.LENGTH_LONG);
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
}
