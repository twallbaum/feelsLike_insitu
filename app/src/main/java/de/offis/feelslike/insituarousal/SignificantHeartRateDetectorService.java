package de.offis.feelslike.insituarousal;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.offis.feelslike.insituarousal.activityService.ActivityDetectionService;
import de.offis.feelslike.insituarousal.bleservice.BleService;

public class SignificantHeartRateDetectorService extends Service
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status> {

    private static final String TAG = "SigHrtRtDetectorService";

    // Variables used for storing the received HeartRateMeasurments
    private static final int BUFFER_SIZE_HEART_RATE_MEASUREMENTS = 60 * 60;
    private long measurementsCounter = 0L;
    private ArrayList<HeartRateMeasurement> heartRateMeasurements;

    // Variables used for frequently executing the calculation
    private static final long SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL = 60 * 1000L;
    private long timeStampLastCalculation;

    // Variables for GoogleApiConnection, used for Activity-Detection
    private GoogleApiClient mGoogleApiClient;
    private static final long DETECT_INTERVAL = 0;
    private DetectedActivity lastActivity;
    private String lastActivityType = "";
    private int lastActivityConf = 0;

    public SignificantHeartRateDetectorService() {}

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize HeartRate storage and calculation variables
        heartRateMeasurements = new ArrayList<>();
        timeStampLastCalculation = System.currentTimeMillis();

        // Register receiver for broadcasts from BleService and ActivityDetectionService
        IntentFilter filter = new IntentFilter();
        filter.addAction(BleService.ACTION_DATA_AVAILABLE);
        filter.addAction(ActivityDetectionService.ACTION_NEW_ACTIVITY);
        registerReceiver(broadcastReceiver, filter);

        // Start GoogleApiClient for receiving ActivityRecognition results in ActivityDetectionService
        mGoogleApiClient =
                new GoogleApiClient.Builder(this).addApi(ActivityRecognition.API)
                        .addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(broadcastReceiver);
        mGoogleApiClient.disconnect();
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(BleService.ACTION_DATA_AVAILABLE)){
                String heartRate = intent.getStringExtra(BleService.EXTRA_DATA);
                Log.d(TAG, "heartRate: " + heartRate);

                if(measurementsCounter >= BUFFER_SIZE_HEART_RATE_MEASUREMENTS){
                    heartRateMeasurements.remove((int)measurementsCounter % BUFFER_SIZE_HEART_RATE_MEASUREMENTS);
                }

                // Add new received measurement to list of heart rate measurements.
                // Use "modulo", to obtain a circular list behavior.
                heartRateMeasurements.add((int)(measurementsCounter % BUFFER_SIZE_HEART_RATE_MEASUREMENTS),
                        new HeartRateMeasurement(Double.valueOf(heartRate)));

                // Compute if a significant heart rate can be found
                // in the past time window of 60 seconds.
                // ToDo: Only execute this finding method, if at least n entries are stored
                // ToDo: Don't execute this finding method, if the user is currently full filling a questionnaire
                // ToDo: Don't execute this finding method, if there is less than t time passed since the last questionnaire
                long timeStampCurrentTime = System.currentTimeMillis();
                if(timeStampCurrentTime - timeStampLastCalculation
                        >= SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL){
                    boolean foundSignificantEntry = findSignificantHeartRateEntry(
                            timeStampCurrentTime - SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL,
                            timeStampCurrentTime);

                    // If any significant entry could be found,
                    // prompt user notification, to start the questionnaire.
                    if(foundSignificantEntry){
                        // Create an intent for starting the ArousalInput activity.
                        Intent intentArousalInput = new Intent(SignificantHeartRateDetectorService.this, ArousalInputActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                        // Create a PendingIntent for the created intent.
                        PendingIntent pendingIntentArousalInput =
                                PendingIntent.getActivity(
                                        SignificantHeartRateDetectorService.this,
                                        0,
                                        intentArousalInput,
                                        PendingIntent.FLAG_UPDATE_CURRENT
                                );

                        // Prompt the notification.
                        SignificantHeartRateDetectorService.this.throwQuestionnaireNotification(
                                SignificantHeartRateDetectorService.this, pendingIntentArousalInput);
                    }

                    // Store the current time stamp, as time stamp for the last executed calculation
                    timeStampLastCalculation = timeStampCurrentTime;
                }
                measurementsCounter++;

                // ToDo: Only for Debugging
                if(measurementsCounter % 30 == 0){
                    // Create an intent for starting the ArousalInput activity.
                    Intent intentArousalInput = new Intent(SignificantHeartRateDetectorService.this, ArousalInputActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    // Create a PendingIntent for the created intent.
                    PendingIntent pendingIntentArousalInput =
                            PendingIntent.getActivity(
                                    SignificantHeartRateDetectorService.this,
                                    0,
                                    intentArousalInput,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );

                    // Prompt the notification.
                    SignificantHeartRateDetectorService.this.throwQuestionnaireNotification(
                            SignificantHeartRateDetectorService.this, pendingIntentArousalInput);
                }
//                Log.d(TAG, "measurementsCounter: " + measurementsCounter);
            }

            // Handle the Broadcast for the current detected activity from ActivityDetectionService
            else if(action.equals(ActivityDetectionService.ACTION_NEW_ACTIVITY)){
                List<DetectedActivity>
                        detectedActivities =
                        intent.getParcelableArrayListExtra(ActivityDetectionService.DETECTED_ACTIVITIES);
                StringBuilder builder = new StringBuilder();
                builder.append("result:\n");
                if (detectedActivities != null) {
                    String currentType = "";
                    DetectedActivity mostConfidentActivity = null;
                    for (DetectedActivity detectedActivity : detectedActivities) {
                        switch (detectedActivity.getType()) {
                            case DetectedActivity.IN_VEHICLE:
                                currentType = "IN_VEHICLE";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.ON_BICYCLE:
                                currentType = "ON_BICYCLE";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.ON_FOOT:
                                currentType = "ON_FOOT";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.RUNNING:
                                currentType = "RUNNING";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.STILL:
                                currentType = "STILL";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.TILTING:
                                currentType = "TILTING";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.WALKING:
                                currentType = "WALKING";
                                builder.append(currentType);
                                break;
                            case DetectedActivity.UNKNOWN:
                                currentType = "UNKNOWN";
                                builder.append(currentType);
                                break;
                            default:
                                currentType = "UNEXPECTED";
                                builder.append(currentType);
                                break;
                        }

//                        // Old solution
//                        if(lastActivityConf < detectedActivity.getConfidence()) {
//                            lastActivityConf = detectedActivity.getConfidence();
//                            lastActivityType = currentType;
//                        }
//                        builder.append(": ").append(lastActivityConf).append('\n');

                        // New solution
                        if(mostConfidentActivity == null || mostConfidentActivity.getConfidence()
                                 < detectedActivity.getConfidence()){
                            mostConfidentActivity = detectedActivity;
                            lastActivity = mostConfidentActivity;
                            lastActivityConf = detectedActivity.getConfidence();
                            lastActivityType = currentType;
                        }
                        // ToDo: Not sure, what this following line is good for.
                        builder.append(": ").append(lastActivityConf).append('\n');
                    }
                }
                Log.d(TAG, builder.toString());
            }
        }
    };

    /**
     * Test if the can be found a significant heart rate in the
     * specified time window.
     *
     * @param timeStampStart    Start time of the specified time window.
     * @param timeStampEnd      End time of the specified time window.
     * @return  True if there is a significant heart rate in the specified
     *          time window, false if not.
     */
    private boolean findSignificantHeartRateEntry(long timeStampStart, long timeStampEnd){
        boolean foundSignificantEntry = false;

        // Find all entries in heartRateMeasurements list, that are within the
        // specified time window.
        // ToDo: There might be more efficient solutions, by using the circular list assumption.
        ArrayList<HeartRateMeasurement> entriesInTimeWindow = new ArrayList<>();
        for(HeartRateMeasurement measurement : heartRateMeasurements){
            if(measurement.getTimeStamp() >= timeStampStart
                    && measurement.getTimeStamp() < timeStampEnd){
                entriesInTimeWindow.add(measurement);
            }
        }

        // Sort the found entries
        Collections.sort(entriesInTimeWindow);

        // Start calculating statistical values

        // AVNN (MRR), mean of NN intervals:
        // MRR = Inv(I) =
        //   1      N
        // ----- * Sum(I(n))
        // (N-1)   n=2
        double mrr = 0;
        // ToDo: Check, if I(n) really is entriesInTimeWindow.get(i).getHeartRate()
        for(int i = 1; i < entriesInTimeWindow.size(); i++){
            mrr += entriesInTimeWindow.get(i).getHeartRate();
        }
        mrr *= (1.0 / (entriesInTimeWindow.size() - 1));

        // SDNN, standard deviation of NN intervals:
        //     (    1      N             )
        // Sqrt(  ----- * Sum(I(n)-MRR)  )
        //     (  (N-1)   n=2            )
        double sdnn = 0;
        for(int i = 1; i < entriesInTimeWindow.size(); i++){
            sdnn += Math.pow(entriesInTimeWindow.get(i).getHeartRate() - mrr, 2);
        }
        sdnn *= (1.0 / (entriesInTimeWindow.size() - 1));
        sdnn = Math.sqrt(sdnn);

        // rMSSD, square root of the mean squared difference of successive NN intervals:
        //     (    1      N                )
        // Sqrt(  ----- * Sum(I(n)-I(n-1))  )
        //     (  (N-2)   n=3               )
        double rmssd = 0;
        for(int i = 2; i < entriesInTimeWindow.size(); i++){
            rmssd += Math.pow(entriesInTimeWindow.get(i).getHeartRate()
                             - entriesInTimeWindow.get(i-1).getHeartRate(), 2);
        }
        rmssd *= (1.0 / (entriesInTimeWindow.size() - 2));
        rmssd = Math.sqrt(rmssd);

        // Decide, if there was a significant value or not
        // ToDo: Implement decision making, on the calculated values


        return foundSignificantEntry;
    }

    private void throwQuestionnaireNotification(Context context, PendingIntent pendingIntent) {
        long[] pattern = {200, 200, 200, 200};
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.notification)
                        .setContentTitle("Condition Questionnaire")
                        .setContentText("Please tap to enter data")
                        .setVibrate(pattern)
                        .setSound(alarmSound)
                        .setContentIntent(pendingIntent)
                        .setDeleteIntent(pendingIntent)
                        .setAutoCancel(true);

        int mNotificationId = 001;
        NotificationManager mNotifyMgr =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    // Callbacks for GoogleAPI-Connection (used for Activity-Detection)

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
}
