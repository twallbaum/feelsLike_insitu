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
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.offis.feelslike.insituarousal.containers.StatisticalResult;


public class QuestionnaireActivity extends AppCompatActivity implements MultiSpinner.MultiSpinnerListener,
        View.OnClickListener, AdapterView.OnItemSelectedListener, TextView.OnEditorActionListener{

    protected static final String TAG = "ArousalInput";

    private static final String INPUT_METHOD = "insitu";

    private static List<String> itemsPeople = new ArrayList<>(Arrays.asList("Alone", "Friends", "Family", "Colleagues", "Strangers"));
    private static List<String> itemsIntake = new ArrayList<>(Arrays.asList("caffein", "nicotine", "alcohol", "other drugs, medications"));

    private MultiSpinner accompanySpinner;
    private MultiSpinner intakeSpinner;
    private Spinner activitySpinner;
    private Spinner locationSpinner;
    //private EditText additionalLocation;
    private EditText freeform;
    private Button btndone;

    private boolean valenceSelected = false;
    private boolean arousalSelected = false;
    private boolean locationSelected = false;

    private long startTime;
    private long stopTime;
    private long timeToInput;
    private int userID;
    public int valance = -1;
    public int arousal = -1;
    public int location = -1;

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

        for (int i = 1; i <= 5; i++) {
            int id = this.getResources().getIdentifier("imgSl" + i, "id", getPackageName());
            ImageView view = ((ImageView) findViewById(id));
            view.setOnClickListener(this);
            view.setTag("drawable/sl" + i);
            view.setTag(id, "loc");
        }

        /*Location
        locationSpinner = (Spinner) findViewById(R.id.location);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.locations, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(adapter);
        locationSpinner.setOnItemSelectedListener(this);*/

        //additionalLocation = (EditText) findViewById(R.id.additionalLocation);

        activitySpinner = (Spinner) findViewById(R.id.activity);
        ArrayAdapter<CharSequence> adapterAct = ArrayAdapter.createFromResource(this,
                R.array.activities, android.R.layout.simple_spinner_item);
        adapterAct.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activitySpinner.setAdapter(adapterAct);
        activitySpinner.setOnItemSelectedListener(this);

        accompanySpinner = (MultiSpinner) findViewById(R.id.partners);
        accompanySpinner.setItems(itemsPeople, getString(R.string.peopleAlt), this);

        intakeSpinner = (MultiSpinner) findViewById(R.id.intake);
        intakeSpinner.setItems(itemsIntake, "select options", this);

        freeform = (EditText) findViewById(R.id.freeform);
        freeform.setOnEditorActionListener(this);

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
        Log.d(TAG, "test " + v.getId() + "clicked");
        //this.translateToMood(v);

        if(v.getTag(v.getId()).equals("val")){
            if(this.valance != -1){
                Log.d(TAG, "test valance already set");
                int currentId = this.getResources().getIdentifier("imgSv" + this.valance, "id", getPackageName());
                ImageView currentView = ((ImageView) findViewById(currentId));
                this.unmarkSelection(currentView);
                Log.d(TAG, "test" + currentId + " : " + v.getId());
                if(currentId != v.getId()){
                    Log.d(TAG, "test not equal, mark the clicked");
                    this.markSelection(imageView);
                }
            } else {
                this.markSelection(imageView);
            }
        } else if(v.getTag(v.getId()).equals("aro")){
            if(this.arousal != -1){
                Log.d(TAG, "test arousal already set");
                int currentId = this.getResources().getIdentifier("imgSa" + this.arousal, "id", getPackageName());
                ImageView currentView = ((ImageView) findViewById(currentId));
                this.unmarkSelection(currentView);
                Log.d(TAG, "test" + currentId + " : " + v.getId());
                if(currentId != v.getId()){
                    Log.d(TAG, "test not equal, mark the clicked");
                    this.markSelection(imageView);
                }
            } else {
                this.markSelection(imageView);
            }
        } else if(v.getTag(v.getId()).equals("loc")){
            if(this.location != -1){
                Log.d(TAG, "test location already set");
                int currentId = this.getResources().getIdentifier("imgSl" + this.location, "id", getPackageName());
                ImageView currentView = ((ImageView) findViewById(currentId));
                this.unmarkSelection(currentView);
                Log.d(TAG, "test" + currentId + " : " + v.getId());
                if(currentId != v.getId()){
                    Log.d(TAG, "test not equal, mark the clicked");
                    this.markSelection(imageView);
                }
            } else {
                this.markSelection(imageView);
            }
        }
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
        c.drawBitmap(checkmark, 0, 0, paint);

        this.translateToMood(v);
        v.setImageBitmap(tmpbmp);
        if(v.getTag(v.getId()).equals("val")) {
            this.valenceSelected = true;
        }else if(v.getTag(v.getId()).equals("aro")) {
            this.arousalSelected = true;
        }else if(v.getTag(v.getId()).equals("loc")) {
            this.locationSelected = true;
        }
    }

    private void unmarkSelection(ImageView v) {

        int imageResource = getResources().getIdentifier(v.getTag().toString(), null, getPackageName());

        Bitmap sam = BitmapFactory.decodeResource(getResources(), imageResource);

        Bitmap tmpbmp = Bitmap.createBitmap(sam.getWidth(), sam.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tmpbmp);
        c.drawBitmap(sam, 0, 0, null);

        v.setImageBitmap(tmpbmp);
        if(v.getTag(v.getId()).equals("val")) {
            this.valenceSelected = false;
            this.valance = -1;
        }else if(v.getTag(v.getId()).equals("aro")) {
            this.arousalSelected = false;
            this.arousal = -1;
        }else if(v.getTag(v.getId()).equals("loc")) {
            this.locationSelected = false;
            this.location = -1;
        }
    }

    public void translateToMood(View v) {
        switch (v.getId()) {
            case R.id.imgSv1: this.valance=1;break;
            case R.id.imgSv2: this.valance=2;break;
            case R.id.imgSv3: this.valance=3;break;
            case R.id.imgSv4: this.valance=4;break;
            case R.id.imgSv5: this.valance=5;break;

            case R.id.imgSa1: this.arousal=1;break;
            case R.id.imgSa2: this.arousal=2;break;
            case R.id.imgSa3: this.arousal=3;break;
            case R.id.imgSa4: this.arousal=4;break;
            case R.id.imgSa5: this.arousal=5;break;

            case R.id.imgSl1: this.location=1;break;
            case R.id.imgSl2: this.location=2;break;
            case R.id.imgSl3: this.location=3;break;
            case R.id.imgSl4: this.location=4;break;
            case R.id.imgSl5: this.location=5;break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, parent.getItemAtPosition(position).toString());

        switch(parent.getId()) {
            //case R.id.location:
            //    break;

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

//    @Override
//    protected void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//        String activityType = "";
//        String notificationType = "";
//        Intent receivedIntent = intent;
//        if(receivedIntent != null){
//            Log.d(TAG, "receivedIntent != null");
//            // Get activity type
//            String activityTypeTemp = receivedIntent.getStringExtra(
//                    AnalysisAndNotificationService.EXTRA_ACTIVITY_TYPE);
//            if(activityTypeTemp != null){
//                activityType = activityTypeTemp;
//                Log.d(TAG, "receivedIntent activityType " + activityType);
//            }
//
//            // Get notification type
//            String notificationTypeTemp = receivedIntent.getStringExtra(
//                    AnalysisAndNotificationService.EXTRA_NOTIFICATION_TYPE);
//            if(notificationTypeTemp != null){
//                notificationType = activityTypeTemp;
//                Log.d(TAG, "receivedIntent notificationType " + notificationType);
//            }
//        }
//    }

    private void logActions(File directory) {
        File file = new File(directory, "feelslikeinsitu_" + this.userID +".log");

        // Get additional information from the intent
        String activityType = "";
        String notificationType = "";
        String sdnnResults = "";
        String rmssdResults = "";
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
                notificationType = notificationTypeTemp;
                Log.d(TAG, "receivedIntent notificationType " + notificationType);
            }

            // Get statistical results for the last analysed time window
            ArrayList<StatisticalResult> statisticalResultsTemp = receivedIntent.getParcelableArrayListExtra(
                    AnalysisAndNotificationService.EXTRA_SELECTED_STATISTICAL_RESULTS);
            if(statisticalResultsTemp != null){
                for(int i = 0; i < statisticalResultsTemp.size(); i++){
                    StatisticalResult result = statisticalResultsTemp.get(i);

                    // Fill sdnn results string, round to 2 decimal points
                    sdnnResults += Math.round(result.getSdnn()*100.0)/100.0;
                    if(i < statisticalResultsTemp.size() - 1){
                        sdnnResults += ",";
                    }

                    // Fill rmssd results string
                    rmssdResults += Math.round(result.getRmssd()*100.0)/100.0;
                    if(i < statisticalResultsTemp.size() - 1){
                        rmssdResults += ",";
                    }
                }

                Log.d(TAG, "receivedIntent sdnnResults: " + sdnnResults);
                Log.d(TAG, "receivedIntent rmssdResults: " + rmssdResults);
            }
        }

        try {
            FileOutputStream fOut = new FileOutputStream(file, true);

            OutputStreamWriter osw = new OutputStreamWriter(fOut);
            String locationOut = "";
            if(location != -1){
                locationOut = getResources().getStringArray(R.array.locations)[location-1]+";";
            }
            try {
                String content = "";
                content +=  this.userID+";"+
                            this.getCurrentTime()+";"+
                            this.stopTime+";"+
                            this.startTime+";"+
                            this.timeToInput+";"+
                            notificationType+";"+
                            activityType+";"+
                            sdnnResults+";"+
                            rmssdResults+";"+
                            "[sv"+this.valance+"]"+
                            "[sa"+this.arousal+"];"+
                            locationOut+
                            //this.locationSpinner.getSelectedItem().toString()+";"+
                            //this.additionalLocation.getText()+";"+
                            this.accompanySpinner.getSelectedItem().toString()+";"+
                            this.intakeSpinner.getSelectedItem().toString()+";"+
                            this.activitySpinner.getSelectedItem().toString()+"\n"+
                            this.freeform.getText()+"\n";
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

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        }
        return false;
    }

}
