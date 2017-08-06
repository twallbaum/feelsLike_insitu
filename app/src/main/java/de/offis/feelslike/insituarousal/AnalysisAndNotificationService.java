package de.offis.feelslike.insituarousal;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
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

public class AnalysisAndNotificationService extends Service
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status> {

    private static final String TAG = "SigHrtRtDetectorService";

    // Variables used for storing the received HeartRateMeasurments
    private static final int BUFFER_SIZE_HEART_RATE_MEASUREMENTS = 60 * 60;
    private long measurementsCounter;
    private ArrayList<HeartRateMeasurement> heartRateMeasurements;

    // Variable used for frequently throwing notification,
    // even if no Significant heart rate could be detected
    private static final long REGULAR_NOTIFICATION_TIME = 1000L * 30; //1000L * 60 * 60;
    private long timeStampLastNotification;

    // Variables used for frequently executing the calculation
    private static final long SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL = 1000L * 60 * 5;
    private long timeStampLastCalculation;
    private static final double RMSSD_THRESHOLD = 99999999.9;

    // Variables for GoogleApiConnection, used for Activity-Detection
    private GoogleApiClient mGoogleApiClient;
    private static final long DETECT_INTERVAL = 0;
    private DetectedActivity lastActivity;
    private String lastActivityType = "NOT_YET_INITIALIZED";
    private int lastActivityConf = 0;
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_NOTIFICATION_TYPE = "notification_type";
    public static final String NOTIFICATION_TYPE_SIGNIFICANT_HEART_RATE = "significant_heart_rate";
    public static final String NOTIFICATION_TYPE_REGULAR_TIMER = "regular_timer";

    public AnalysisAndNotificationService() {}

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize calculation timestamp
        timeStampLastCalculation = System.currentTimeMillis();
        timeStampLastNotification = System.currentTimeMillis();

        // Check if data from a last session has to be loaded
        heartRateMeasurements = new ArrayList<>();  //loadPersistentHeartRates();
        measurementsCounter = 0L;

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
        Log.d(TAG, "onDestroy");

        // Save current stored heart rate entries history
        // saveHeartRatesPersistent();

        // Release BroadcastReceiver and Activity-API-Connection
        unregisterReceiver(broadcastReceiver);
        mGoogleApiClient.disconnect();
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "listSize: " + heartRateMeasurements.size() + " | counter: " + measurementsCounter);

            // If Study isn't activated, discard received intent
            SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(
                    AnalysisAndNotificationService.this);
            boolean studyIsRunning = mPreferences.getBoolean(MainActivity.PREFERENCES_STUDY_RUNNING, false);
            if(!studyIsRunning){
                return;
            }

            // Handle heart rate update intent
            long timeStampCurrentTime = System.currentTimeMillis();
            if(action.equals(BleService.ACTION_DATA_AVAILABLE)){
                String heartRate = intent.getStringExtra(BleService.EXTRA_DATA);
                Log.d(TAG, "heartRate: " + heartRate);

                if(measurementsCounter >= BUFFER_SIZE_HEART_RATE_MEASUREMENTS){
                    heartRateMeasurements.remove((int)measurementsCounter % BUFFER_SIZE_HEART_RATE_MEASUREMENTS);
                }

                // Add new received measurement to list of heart rate measurements.
                // Use "modulo", to obtain a circular list behavior.
                heartRateMeasurements.add((int)(measurementsCounter % BUFFER_SIZE_HEART_RATE_MEASUREMENTS),
                        new HeartRateMeasurement(
                                timeStampCurrentTime,
                                Double.valueOf(heartRate),
                                lastActivityType));

                // Compute if a significant heart rate can be found
                // in the past time window of 60 seconds.
                // ToDo: Only execute this finding method, if at least n entries are stored
                // ToDo: Don't execute this finding method, if the user is currently full filling a questionnaire
                // ToDo: Don't execute this finding method, if there is less than t time passed since the last questionnaire
                if(timeStampCurrentTime - timeStampLastCalculation
                        >= SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL){
                    String foundSignificantEntryActivityType = findSignificantHeartRateEntry(
                            timeStampCurrentTime - SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL,
                            timeStampCurrentTime);

                    // If any significant entry could be found,
                    // prompt user notification, to start the questionnaire.
                    if(foundSignificantEntryActivityType != null){
                        // Create an intent for starting the ArousalInput activity.
                        Intent intentQuestionnaire = new Intent(AnalysisAndNotificationService.this, QuestionnaireActivity.class);
                        intentQuestionnaire.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intentQuestionnaire.putExtra(EXTRA_ACTIVITY_TYPE, foundSignificantEntryActivityType);
                        intentQuestionnaire.putExtra(EXTRA_NOTIFICATION_TYPE, NOTIFICATION_TYPE_SIGNIFICANT_HEART_RATE);

                        // Create a PendingIntent for the created intent.
                        PendingIntent pendingIntentArousalInput =
                                PendingIntent.getActivity(
                                        AnalysisAndNotificationService.this,
                                        0,
                                        intentQuestionnaire,
                                        PendingIntent.FLAG_UPDATE_CURRENT
                                );

                        // Prompt the notification.
                        timeStampLastNotification = timeStampCurrentTime;
                        AnalysisAndNotificationService.this.throwQuestionnaireNotification(
                                AnalysisAndNotificationService.this, pendingIntentArousalInput);
                    }

                    // Store the current time stamp, as time stamp for the last executed calculation
                    timeStampLastCalculation = timeStampCurrentTime;
                }
                measurementsCounter++;

//                // ToDo: Only for Debugging
//                if(measurementsCounter % 30 == 0){
//                    // Create an intent for starting the ArousalInput activity.
//                    Intent intentArousalInput = new Intent(AnalysisAndNotificationService.this, ArousalInputActivity.class);
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//
//                    // Create a PendingIntent for the created intent.
//                    PendingIntent pendingIntentArousalInput =
//                            PendingIntent.getActivity(
//                                    AnalysisAndNotificationService.this,
//                                    0,
//                                    intentArousalInput,
//                                    PendingIntent.FLAG_UPDATE_CURRENT
//                            );
//
//                    // Prompt the notification.
//                    AnalysisAndNotificationService.this.throwQuestionnaireNotification(
//                            AnalysisAndNotificationService.this, pendingIntentArousalInput);
//                }
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

            // Throw regular notification, if there wasn't thrown a notification
            // for a specified time
            if(timeStampCurrentTime - timeStampLastNotification >=
                    REGULAR_NOTIFICATION_TIME){
                // Create an intent for starting the ArousalInput activity.
                Intent intentQuestionnaire = new Intent(AnalysisAndNotificationService.this, QuestionnaireActivity.class);
                intentQuestionnaire.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intentQuestionnaire.putExtra(EXTRA_ACTIVITY_TYPE, lastActivityType);
                intentQuestionnaire.putExtra(EXTRA_NOTIFICATION_TYPE, NOTIFICATION_TYPE_REGULAR_TIMER);

                // Create a PendingIntent for the created intent.
                PendingIntent pendingIntentArousalInput =
                        PendingIntent.getActivity(
                                AnalysisAndNotificationService.this,
                                0,
                                intentQuestionnaire,
                                PendingIntent.FLAG_UPDATE_CURRENT
                        );

                // Prompt the notification.
                timeStampLastNotification = timeStampCurrentTime;
                AnalysisAndNotificationService.this.throwQuestionnaireNotification(
                        AnalysisAndNotificationService.this, pendingIntentArousalInput);
            }
        }
    };

    /**
     * Test if the can be found a significant heart rate in the
     * specified time window.
     *
     * @param timeStampStart    Start time of the specified time window.
     * @param timeStampEnd      End time of the specified time window.
     * @return  Found Activity type if there is a significant heart rate in the specified
     *          time window, null, if there is none significant heart rate in the specified
     *          time window.
     */
    private String findSignificantHeartRateEntry(long timeStampStart, long timeStampEnd){
        String foundSignificantEntryType = null;

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

        // Find most used activity type
        // Order for activityTypeCounter:
        /*
            "IN_VEHICLE"
            "ON_BICYCLE"
            "ON_FOOT"
            "RUNNING"
            "STILL"
            "TILTING"
            "WALKING"
            "UNKNOWN"
            "UNEXPECTED
            "NOT_YET_INITIALIZED"
         */
        int[] activityTypeCounter = {0,0,0,0,0,0,0,0,0,0};
        for(HeartRateMeasurement measurement : entriesInTimeWindow){
            if(measurement.getActivityType().equalsIgnoreCase("IN_VEHICLE")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("ON_BICYCLE")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("ON_FOOT")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("RUNNING")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("STILL")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("TILTING")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("WALKING")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("UNKNOWN")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("UNEXPECTED")){
                activityTypeCounter[0]++;
            } else if(measurement.getActivityType().equalsIgnoreCase("NOT_YET_INITIALIZED")){
                activityTypeCounter[0]++;
            }
        }

        // Find most used type index
        int chosenTypeIndex = -1;
        int chosenTypeCounter = -1;
        String chosenType = null;
        for(int i = 0; i < activityTypeCounter.length; i++){
            if(activityTypeCounter[i] > chosenTypeCounter){
                chosenTypeIndex = i;
                chosenTypeCounter = activityTypeCounter[i];
            }
        }

        // Convert type index to according type String
        if(chosenTypeIndex == -1){
            chosenType = null;
        } else if(chosenTypeIndex == 0){
            chosenType = "IN_VEHICLE";
        } else if(chosenTypeIndex == 1){
            chosenType = "ON_BICYCLE";
        } else if(chosenTypeIndex == 2){
            chosenType = "ON_FOOT";
        } else if(chosenTypeIndex == 3){
            chosenType = "RUNNING";
        } else if(chosenTypeIndex == 4){
            chosenType = "STILL";
        } else if(chosenTypeIndex == 5){
            chosenType = "TILTING";
        } else if(chosenTypeIndex == 6){
            chosenType = "WALKING";
        } else if(chosenTypeIndex == 7){
            chosenType = "UNKNOWN";
        } else if(chosenTypeIndex == 8){
            chosenType = "UNEXPECTED";
        } else if(chosenTypeIndex == 9){
            chosenType = "NOT_YET_INITIALIZED";
        }

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
        if(rmssd > RMSSD_THRESHOLD){
            foundSignificantEntryType = chosenType;
        }

        return foundSignificantEntryType;
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
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    @Override
    public void onResult(Status status) {
        if (!status.isSuccess()) {
            Log.d(TAG,
                    "Activity Recognition failed to start: " + status.getStatusCode() + ", " + status
                            .getStatusMessage());
        }
    }

    // Unused methods for saving and loading heart rate entries

    private ArrayList<HeartRateMeasurement> loadPersistentHeartRates(){
        ArrayList<HeartRateMeasurement> measurements = new ArrayList<>();

        // Create a list of HeartRateEntries
        String fileContent = Utils.readFromFile(this, Utils.HEART_RATE_ENTRIES_FILE_NAME);
        String[] lines = fileContent.split("\n");
        for(int i = 1; i < lines.length; i++){
            String[] line = lines[i].split(",");

            // There might be an empty line at the end, which we don't
            // want to parse a HeartRateMeasurement from
            // ToDo: Check if this check is necessary
            if(line.length == 3){
                measurements.add(new HeartRateMeasurement(
                        Long.parseLong(line[0]),
                        Double.parseDouble(line[1]),
                        line[2]));
            }
        }

        // Sort the created list, according to each entry's timestamp
        Collections.sort(measurements);

        // Remove the entries, that are to old
        // An entry is to old, if it's longer away than the calculation
        // interval length
        long minimumAllowableTimeStamp = System.currentTimeMillis()
                - SIGNIFICANT_HEART_RATE_CALCULATION_INTERVAL;
        for(int i = 0; i < measurements.size();){
            if(minimumAllowableTimeStamp - measurements.get(i).getTimeStamp() > 0){
                measurements.remove(i);
                continue;
            } else{
                i++;
            }
        }

        return measurements;
    }

    private void saveHeartRatesPersistent(){
        // Create content String form HeartRateMeasurments list
        String content = "timeStamp,heartRate,activityType\n";
        for(HeartRateMeasurement measurement : heartRateMeasurements){
            content += measurement.getTimeStamp() + ",";
            content += measurement.getHeartRate() + ",";
            content += measurement.getActivityType() + "\n";
        }

        // Write content to file
        Utils.writeToFile(content, this, Utils.HEART_RATE_ENTRIES_FILE_NAME);
    }
}
