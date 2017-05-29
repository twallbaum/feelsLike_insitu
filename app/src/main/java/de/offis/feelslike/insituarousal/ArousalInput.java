package de.offis.feelslike.insituarousal;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.offis.feelslike.insituarousal.activityService.ActivityDetectionService;
import de.offis.feelslike.insituarousal.bleservice.BleService;


public class ArousalInput extends BleActivity implements MultiSpinner.MultiSpinnerListener, View.OnClickListener, AdapterView.OnItemSelectedListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status> {

    protected static final String TAG = "ArousalInput";

    private static final long DETECT_INTERVAL = 0;
    private static final String BROADCAST_ACTION = "de.offis.feelslike.insituarousal.broadcast";
    private static final String DETECTED_ACTIVITIES = "detectedActivities";

    private boolean valenceSelected = false;
    private boolean arousalSelected = false;
    private boolean locationSelected = false;
    private boolean accompanySelected = false;
    private boolean activitySelected = false;

    private static final String INPUT_METHOD = "arousal";
    private MultiSpinner accompanySpinner;
    private Spinner activitySpinner;
    private List<String> itemsPeople = new ArrayList<>(Arrays.asList("Alone", "Friends", "Family", "Colleagues", "Strangers"));
    private Spinner locationSpinner;

    public static Intent newBroadcastIntent(List<DetectedActivity> detectedActivities) {
        final ArrayList<DetectedActivity> activities = new ArrayList<DetectedActivity>(detectedActivities);
        return new Intent(BROADCAST_ACTION).putParcelableArrayListExtra(DETECTED_ACTIVITIES, activities);
    }

    public static IntentFilter newBroadcastIntentFilter() {
        return new IntentFilter(BROADCAST_ACTION);
    }

    private GoogleApiClient mGoogleApiClient;
    private BroadcastReceiver mBroadcastReceiver;

    @Override
    void handlePushNotification(Intent intent) {
        Log.d(TAG, "###### handle push notification");
    }

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

        activitySpinner = (Spinner) findViewById(R.id.activity);
        ArrayAdapter<CharSequence> adapterAct = ArrayAdapter.createFromResource(this,
                R.array.activities, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activitySpinner.setAdapter(adapterAct);
        activitySpinner.setOnItemSelectedListener(this);

        accompanySpinner = (MultiSpinner) findViewById(R.id.partners);
        accompanySpinner.setItems(itemsPeople, getString(R.string.peopleAlt), this);

        mGoogleApiClient =
                new GoogleApiClient.Builder(this).addApi(ActivityRecognition.API)
                        .addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                List<DetectedActivity>
                        detectedActivities =
                        intent.getParcelableArrayListExtra(DETECTED_ACTIVITIES);
                StringBuilder builder = new StringBuilder();
                builder.append("result:\n");
                if (detectedActivities != null) {
                    for (DetectedActivity detectedActivity : detectedActivities) {
                        switch (detectedActivity.getType()) {
                            case DetectedActivity.IN_VEHICLE:
                                builder.append("IN_VEHICLE");
                                break;
                            case DetectedActivity.ON_BICYCLE:
                                builder.append("ON_BICYCLE");
                                break;
                            case DetectedActivity.ON_FOOT:
                                builder.append("ON_FOOT");
                                break;
                            case DetectedActivity.RUNNING:
                                builder.append("RUNNING");
                                break;
                            case DetectedActivity.STILL:
                                builder.append("STILL");
                                break;
                            case DetectedActivity.TILTING:
                                builder.append("TILTING");
                                break;
                            case DetectedActivity.WALKING:
                                builder.append("WALKING");
                                break;
                            case DetectedActivity.UNKNOWN:
                                builder.append("UNKNOWN");
                                break;
                            default:
                                builder.append("UNEXPECTED");
                                break;
                        }
                        builder.append(": ").append(detectedActivity.getConfidence()).append('\n');
                    }
                }
                Log.d(TAG, builder.toString());
            }
        };

    }


    @Override
    public void onClick(View v) {
        ImageView imageView = (ImageView) v;

        //this.translateToMood(v);

        if(imageView.getTag(v.getId()).equals("val")) {
            this.valenceSelected = true;
        }else if(imageView.getTag(v.getId()).equals("aro")) {
            this.arousalSelected = true;
        }

        this.markSelection(imageView);

        if(this.valenceSelected && this.arousalSelected && this.accompanySelected && this.locationSelected) {
            finish();
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
        c.drawBitmap(checkmark, 100, 50, paint);

        v.setImageBitmap(tmpbmp);

    }

   /* @Override
    public String getInputMethod() {
        return this.INPUT_METHOD;
    }

    @Override
    public void translateToMood(View v) {
        switch (v.getId()) {
            case R.id.imgSv1: this.moodSelection+="[sv1]";break;
            case R.id.imgSv2: this.moodSelection+="[sv2]";break;
            case R.id.imgSv3: this.moodSelection+="[sv3]";break;
            case R.id.imgSv4: this.moodSelection+="[sv4]";break;
            case R.id.imgSv5: this.moodSelection+="[sv5]";break;

            case R.id.imgSa1: this.moodSelection+="[sa1]";break;
            case R.id.imgSa2: this.moodSelection+="[sa2]";break;
            case R.id.imgSa3: this.moodSelection+="[sa3]";break;
            case R.id.imgSa4: this.moodSelection+="[sa4]";break;
            case R.id.imgSa5: this.moodSelection+="[sa5]";break;
        }
    }*/

    @Override
    public void onConnected(Bundle bundle) {
        final PendingResult<Status>
                statusPendingResult =
                ActivityRecognition.ActivityRecognitionApi
                        .requestActivityUpdates(mGoogleApiClient, DETECT_INTERVAL, PendingIntent
                                .getService(this, 0, new Intent(this, ActivityDetectionService.class),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
        statusPendingResult.setResultCallback(this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "connection failed");
    }

    @Override
    public void onResult(Status status) {
        if (!status.isSuccess()) {
            Log.d(TAG,
                    "Activity Recognition failed to start: " + status.getStatusCode() + ", " + status
                            .getStatusMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBroadcastReceiver, newBroadcastIntentFilter());
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, parent.getItemAtPosition(position).toString());

        switch(parent.getId()) {
            case R.id.location:
                this.locationSelected = true;
                break;

            case R.id.activity:
                this.activitySelected = true;
                break;
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onItemsSelected(boolean[] selected) {
        Log.d(TAG, "selected: " + selected.toString());
        this.accompanySelected = true;
    }
}
