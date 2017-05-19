package de.offis.feelslike.insituarousal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Time;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;


public class LoggingActivity extends ActionBarActivity {

    private long startTime;
    private long stopTime;
    private long timeToInput;
    private int userID;
    public String moodSelection = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.startTime = System.currentTimeMillis();

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        this.userID = sharedPref.getInt("uid", 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.stopTime = System.currentTimeMillis();
        this.timeToInput = this.stopTime - this.startTime;

        if(this.isExternalStorageWritable()) {
            File directory =  this.getLogStorageDir(this, "moodmessenger_log");
            this.logActions(directory);
        }
    }

    private void logActions(File directory) {
        File file = new File(directory, "moodmessenger_" + this.userID +".log");

        try {
            FileOutputStream fOut = new FileOutputStream(file, true);

            OutputStreamWriter osw = new OutputStreamWriter(fOut);
            try {
                osw.write(this.userID+";"+this.getCurrentTime()+";"+this.stopTime+";"+this.startTime+";"+this.timeToInput+";"+this.getInputMethod()+";"+this.moodSelection+"\n");
                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTime() {
        Time today = new Time(Time.getCurrentTimezone());
        today.setToNow();

        return today.format("%Y%m%dT%H%M%S");
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public File getLogStorageDir(Context context, String dirName) {
        File file = new File(context.getExternalFilesDir(
                null), dirName);
        if (!file.mkdirs()) {
            Log.e("LoggingActivity", "Directory not created");
        }
        return file;
    }

    public String getInputMethod() { return ""; }

    public void translateToMood(View v) {}

}
