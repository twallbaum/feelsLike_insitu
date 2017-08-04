package de.offis.feelslike.insituarousal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class QuestionnaireActivity extends AppCompatActivity implements MultiSpinner.MultiSpinnerListener,
        View.OnClickListener, AdapterView.OnItemSelectedListener{

    protected static final String TAG = "ArousalInput";

    private static final String INPUT_METHOD = "insitu";

    private static List<String> itemsPeople = new ArrayList<>(Arrays.asList("Alone", "Friends", "Family", "Colleagues", "Strangers"));
    private static List<String> itemsIntake = new ArrayList<>(Arrays.asList("caffein", "nicotine", "alcohol", "other drugs, medications"));

    private MultiSpinner accompanySpinner;
    private MultiSpinner intakeSpinner;
    private Spinner activitySpinner;
    private Spinner locationSpinner;
    private EditText additionalLocation;
    private EditText freeform;
    private Button btndone;

    private boolean valenceSelected = false;
    private boolean arousalSelected = false;

    private long startTime;
    private long stopTime;
    private long timeToInput;
    private int userID;
    public String valanceArousal = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arousalinput);

        for (int i = 1; i <= 5; i++) {
            int id = this.getResources().getIdentifier("imgSv" + i, "id", getPackageName());
            ImageView view = ((ImageView) findViewById(id));
            view.setOnClickListener(this);
            view.setTag("drawable/sv" + i);
            view.setTag(id, "val");
        }

        for (int i = 1; i <= 5; i++) {
            int id = this.getResources().getIdentifier("imgSa" + i, "id", getPackageName());
            ImageView view = ((ImageView) findViewById(id));
            view.setOnClickListener(this);
            view.setTag("drawable/sa" + i);
            view.setTag(id, "aro");
        }

        locationSpinner = (Spinner) findViewById(R.id.location);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.locations, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(adapter);
        locationSpinner.setOnItemSelectedListener(this);

        additionalLocation = (EditText) findViewById(R.id.additionalLocation);

        activitySpinner = (Spinner) findViewById(R.id.activity);
        ArrayAdapter<CharSequence> adapterAct = ArrayAdapter.createFromResource(this,
                R.array.activities, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activitySpinner.setAdapter(adapterAct);
        activitySpinner.setOnItemSelectedListener(this);

        accompanySpinner = (MultiSpinner) findViewById(R.id.partners);
        accompanySpinner.setItems(itemsPeople, getString(R.string.peopleAlt), this);

        intakeSpinner = (MultiSpinner) findViewById(R.id.intake);
        intakeSpinner.setItems(itemsIntake, "select options", this);

        freeform = (EditText) findViewById(R.id.freeform);

        btndone = (Button) findViewById(R.id.btndone);
        btndone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(valenceSelected && arousalSelected) {
                    stopTime = System.currentTimeMillis();
                    timeToInput = stopTime - startTime;

                    logActions(getLogStorageDir(QuestionnaireActivity.this, "feelslikeinsitu"));
                    finish();
                }else {
                    Snackbar.make(findViewById(R.id.myCoordinatorLayout), "Please select at least valence and arousal levels.",
                            Snackbar.LENGTH_SHORT)
                            .show();
                }
            }
        });

        this.startTime = System.currentTimeMillis();

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        this.userID = sharedPref.getInt("uid", 0);
    }


    @Override
    public void onClick(View v) {
        ImageView imageView = (ImageView) v;

        this.translateToMood(v);

        if(imageView.getTag(v.getId()).equals("val")) {
            this.valenceSelected = true;
        }else if(imageView.getTag(v.getId()).equals("aro")) {
            this.arousalSelected = true;
        }

        this.markSelection(imageView);
    }

    private void markSelection(ImageView v) {

        int imageResource = getResources().getIdentifier(v.getTag().toString(), null, getPackageName());

        Bitmap sam = BitmapFactory.decodeResource(getResources(), imageResource);
        Bitmap checkmark = BitmapFactory.decodeResource(getResources(), R.drawable.checkmark);

        float[] colorTransform = {
                0, 0, 0, 0, 0,
                0, 255.0f, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 1.0f, 0};

        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.set(colorTransform);

        ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
        Paint paint = new Paint();
        paint.setColorFilter(colorFilter);

        Bitmap tmpbmp = Bitmap.createBitmap(sam.getWidth(), sam.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tmpbmp);
        c.drawBitmap(sam, 0, 0, null);
        c.drawBitmap(checkmark, 100, 50, paint);

        v.setImageBitmap(tmpbmp);
    }

    public void translateToMood(View v) {
        switch (v.getId()) {
            case R.id.imgSv1: this.valanceArousal+="[sv1]";break;
            case R.id.imgSv2: this.valanceArousal+="[sv2]";break;
            case R.id.imgSv3: this.valanceArousal+="[sv3]";break;
            case R.id.imgSv4: this.valanceArousal+="[sv4]";break;
            case R.id.imgSv5: this.valanceArousal+="[sv5]";break;

            case R.id.imgSa1: this.valanceArousal+="[sa1]";break;
            case R.id.imgSa2: this.valanceArousal+="[sa2]";break;
            case R.id.imgSa3: this.valanceArousal+="[sa3]";break;
            case R.id.imgSa4: this.valanceArousal+="[sa4]";break;
            case R.id.imgSa5: this.valanceArousal+="[sa5]";break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, parent.getItemAtPosition(position).toString());

        switch(parent.getId()) {
            case R.id.location:
                break;

            case R.id.activity:
                break;

            case R.id.intake:
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onItemsSelected(boolean[] selected) {
        Log.d(TAG, "selected: " + selected.toString());
    }

    private void logActions(File directory) {
        File file = new File(directory, "feelslikeinsitu_" + this.userID +".log");

        // Get additional information from the intent
        String activityType = "";
        String notificationType = "";
        Intent receivedIntent = getIntent();
        if(receivedIntent != null){
            Log.d(TAG, "receivedIntent != null");
            // Get activity type
            String activityTypeTemp = receivedIntent.getStringExtra(
                    AnalysisAndNotificationService.EXTRA_ACTIVITY_TYPE);
            if(activityTypeTemp != null){
                activityType = activityTypeTemp;
                Log.d(TAG, "receivedIntent activityType " + activityType);
            }

            // Get notification type
            String notificationTypeTemp = receivedIntent.getStringExtra(
                    AnalysisAndNotificationService.EXTRA_NOTIFICATION_TYPE);
            if(notificationTypeTemp != null){
                notificationType = activityTypeTemp;
                Log.d(TAG, "receivedIntent notificationType " + notificationType);
            }
        }

        try {
            FileOutputStream fOut = new FileOutputStream(file, true);

            OutputStreamWriter osw = new OutputStreamWriter(fOut);
            try {
                String content = "";
                content +=  this.userID+";"+
                            this.getCurrentTime()+";"+
                            this.stopTime+";"+
                            this.startTime+";"+
                            this.timeToInput+";"+
                            notificationType+";"+
                            activityType+";"+
                            this.valanceArousal+";"+
                            this.locationSpinner.getSelectedItem().toString()+";"+
                            this.additionalLocation.getText()+";"+
                            this.accompanySpinner.getSelectedItem().toString()+";"+
                            this.intakeSpinner.getSelectedItem().toString()+";"+
                            this.activitySpinner.getSelectedItem().toString()+";"+
                            this.freeform.getText()+"\n";
//                osw.write(
//                        this.userID+";"+
//                        this.getCurrentTime()+";"+
//                        this.stopTime+";"+
//                        this.startTime+";"+
//                        this.timeToInput+";"+
//                        notificationType+";"+
//                        activityType+";"+
//                        this.valanceArousal+";"+
//                        this.locationSpinner.getSelectedItem().toString()+";"+
//                        this.additionalLocation.getText()+";"+
//                        this.accompanySpinner.getSelectedItem().toString()+";"+
//                        this.intakeSpinner.getSelectedItem().toString()+";"+
//                        this.activitySpinner.getSelectedItem().toString()+";"+
//                        this.freeform.getText()+"\n");
                Log.d(TAG, content);
                osw.write(content);
                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "Added answers to: " + file.getAbsolutePath(), Toast.LENGTH_LONG);
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
}
