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
import de.offis.feelslike.insituarousal.containers.HeartRateMeasurement;
import de.offis.feelslike.insituarousal.containers.StatisticalResult;

public class AnalysisAndNotificationService extends Service
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status> {

    private static final String TAG = "AnAndNotService";

    // Variables used for storing the received HeartRateMeasurments and statistical values
    private static final int BUFFER_SIZE_HEART_RATE_MEASUREMENTS = 60 * 60;
    private long measurementsCounter;
    // ToDo: Circular list is way to big overkill. Change to simple queue instead.
    private ArrayList<HeartRateMeasurement> heartRateMeasurements;
    private static final int BUFFER_SIZE_STATISTICAL_RESULTS = 60 * 60;
    private long statisticalResultsCounter;
    // ToDo: Circular list is way to big overkill. Change to simple queue instead.
    private ArrayList<StatisticalResult> statisticalResults;
    private static final long STATISTICAL_VALUES_INTERVAL = 1000L * 60 * 2;
    private static final long STATISTICAL_COMPARISON_TIME_DIFFERENCE = 1000L * 60 * 2;
    private static final long STATISTICAL_BASED_NOTIFICATION_MINIMUM_TIME_DIFFERENCE = 1000L * 60 * 6;
    private static final double MRR_FACTOR_THRESHOLD = 1.2;

    // Variable used for frequently throwing notification,
    // even if no Significant heart rate could be detected
    private static final long REGULAR_NOTIFICATION_TIME = 1000L * 60 * 60;
    private long timeStampLastStatisticalBasedNotification;
    private long timeStampLastRegularNotification;

    // Variables for GoogleApiConnection, used for Activity-Detection
    private GoogleApiClient mGoogleApiClient;
    private static final long DETECT_INTERVAL = 0;
    private DetectedActivity lastActivity;
    private String lastActivityType = "NOT_YET_INITIALIZED";
    private int lastActivityConf = 0;
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_SELECTED_STATISTICAL_RESULTS = "selected_statistical_results";
    public static final String EXTRA_NOTIFICATION_TYPE = "notification_type";
    public static final String NOTIFICATION_TYPE_SIGNIFICANT_STATISTICAL_RESULT = "significant_statistical_result";
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
        timeStampLastStatisticalBasedNotification = System.currentTimeMillis();
        timeStampLastRegularNotification = System.currentTimeMillis();

        // Check if data from a last session has to be loaded
        heartRateMeasurements = new ArrayList<>();  //loadPersistentHeartRates();
        measurementsCounter = 0L;
//        mrrValues = new ArrayList<>();
//        sdnnValues = new ArrayList<>();
//        rmssdValues = new ArrayList<>();
        statisticalResults = new ArrayList<>();
        statisticalResultsCounter = 0L;

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
            // ToDo: Maybe this check can be deleted, because service maybe only running when needed
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
                measurementsCounter++;

                // Update Mrr, Sdnn, rmssd
                calculateStatisticalResults(timeStampCurrentTime - STATISTICAL_VALUES_INTERVAL,
                        timeStampCurrentTime);

                // Check if significant entry is found
                // Therefore compare current statistical result with statistical result from
                // 1 minute ago
                boolean significantDifferenceFound = compareStatisticalResults(timeStampCurrentTime,
                        timeStampCurrentTime - STATISTICAL_COMPARISON_TIME_DIFFERENCE);
                if(significantDifferenceFound){
                    if(timeStampCurrentTime - timeStampLastStatisticalBasedNotification
                            >= STATISTICAL_BASED_NOTIFICATION_MINIMUM_TIME_DIFFERENCE){
                        // Create an intent for starting the ArousalInput activity.
                        Intent intentQuestionnaire = new Intent(AnalysisAndNotificationService.this, QuestionnaireActivity.class);
                        intentQuestionnaire.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intentQuestionnaire.putExtra(EXTRA_ACTIVITY_TYPE, lastActivityType);
                        intentQuestionnaire.putExtra(EXTRA_NOTIFICATION_TYPE, NOTIFICATION_TYPE_SIGNIFICANT_STATISTICAL_RESULT);
                        intentQuestionnaire.putExtra(EXTRA_SELECTED_STATISTICAL_RESULTS,
                                getStatisticalResultsForTimeWindow(
                                        timeStampCurrentTime - STATISTICAL_VALUES_INTERVAL,
                                        timeStampCurrentTime));

                        // Create a PendingIntent for the created intent.
                        PendingIntent pendingIntentArousalInput =
                                PendingIntent.getActivity(
                                        AnalysisAndNotificationService.this,
                                        0,
                                        intentQuestionnaire,
                                        PendingIntent.FLAG_UPDATE_CURRENT
                                );

                        // Save current time as last timestamp, when a notification has been thrown
                        timeStampLastStatisticalBasedNotification = timeStampCurrentTime;

                        // Prompt the notification.
                        AnalysisAndNotificationService.this.throwQuestionnaireNotification(
                                AnalysisAndNotificationService.this, pendingIntentArousalInput);
                    }
                }
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
            if(timeStampCurrentTime - timeStampLastRegularNotification >=
                    REGULAR_NOTIFICATION_TIME){
                // Create an intent for starting the ArousalInput activity.
                Intent intentQuestionnaire = new Intent(AnalysisAndNotificationService.this, QuestionnaireActivity.class);
                intentQuestionnaire.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intentQuestionnaire.putExtra(EXTRA_ACTIVITY_TYPE, lastActivityType);
                intentQuestionnaire.putExtra(EXTRA_NOTIFICATION_TYPE, NOTIFICATION_TYPE_REGULAR_TIMER);
                intentQuestionnaire.putExtra(EXTRA_SELECTED_STATISTICAL_RESULTS,
                        getStatisticalResultsForTimeWindow(
                                timeStampCurrentTime - STATISTICAL_VALUES_INTERVAL,
                                timeStampCurrentTime));

                // Create a PendingIntent for the created intent.
                PendingIntent pendingIntentArousalInput =
                        PendingIntent.getActivity(
                                AnalysisAndNotificationService.this,
                                0,
                                intentQuestionnaire,
                                PendingIntent.FLAG_UPDATE_CURRENT
                        );

                // Prompt the notification.
                timeStampLastRegularNotification = timeStampCurrentTime;
                AnalysisAndNotificationService.this.throwQuestionnaireNotification(
                        AnalysisAndNotificationService.this, pendingIntentArousalInput);
            }
        }
    };

    private ArrayList<StatisticalResult> getStatisticalResultsForTimeWindow(
            long timeStampStart, long timeStampEnd){
        ArrayList<StatisticalResult> entriesInTimeWindow = new ArrayList<>();
        for(StatisticalResult result : statisticalResults){
            if(result.getTimeStamp() >= timeStampStart
                    && result.getTimeStamp() < timeStampEnd){
                entriesInTimeWindow.add(result);
            }
        }

        // Sort the found entries
        Collections.sort(entriesInTimeWindow);
        return entriesInTimeWindow;
    }

    private void calculateStatisticalResults(long timeStampStart, long timeStampEnd){
        // Find all entries in heartRateMeasurements list, that are within the
        // specified time window.
        // ToDo: There might be more efficient solutions, by using the circular list assumption.
        ArrayList<HeartRateMeasurement> entriesInTimeWindow = new ArrayList<>();
        for(HeartRateMeasurement measurement : heartRateMeasurements){
            if(measurement.getTimeStamp() >= timeStampStart
                    && measurement.getTimeStamp() <= timeStampEnd){
                entriesInTimeWindow.add(measurement);
            }
        }
        Log.d(TAG, "calc: heartRateMeasurementsSize: " + heartRateMeasurements.size()
                + " entriesInTimeWindow: " + entriesInTimeWindow.size());

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

        // Store result in according Container object, using timestamp from the newest
        // used HeartRateMeasurement

        // First remove the current entry from the "circular list", if there is already one
        // at the specified position
        if(statisticalResultsCounter >= BUFFER_SIZE_STATISTICAL_RESULTS){
            statisticalResults.remove(
                    (int)statisticalResultsCounter % BUFFER_SIZE_STATISTICAL_RESULTS);
        }

        // Add new received measurement to list of heart rate measurements.
        // Use "modulo", to obtain a circular list behavior and entry positioning.
        statisticalResults.add(
                (int)(statisticalResultsCounter % BUFFER_SIZE_STATISTICAL_RESULTS),
                new StatisticalResult(timeStampEnd, mrr, sdnn, rmssd));

        statisticalResultsCounter++;
    }

    private boolean compareStatisticalResults(long timeStampNewResult, long timeStampOldResult){
        boolean significantDifferenceFound = false;

        StatisticalResult selectedNewStatisticalResult = null;
        StatisticalResult selectedOldStatisticalResult = null;
        long currentSmallestTimeDifferenceNewResult = Long.MAX_VALUE;
        long currentSmallestTimeDifferenceOldResult = Long.MAX_VALUE;
        long timeDifference;
        for(StatisticalResult result : statisticalResults){
            // Find closest fitting entry for the selected "newResult" timestamp
            if((timeDifference = Math.abs(result.getTimeStamp() - timeStampNewResult))
                    < currentSmallestTimeDifferenceNewResult){
                currentSmallestTimeDifferenceNewResult = timeDifference;
                selectedNewStatisticalResult = result;
            }

            // Find closest fitting entry for the selected "oldResult" timestamp
            if((timeDifference = Math.abs(result.getTimeStamp() - timeStampOldResult))
                    < currentSmallestTimeDifferenceOldResult){
                currentSmallestTimeDifferenceOldResult = timeDifference;
                selectedOldStatisticalResult = result;
            }
        }

        if(selectedNewStatisticalResult != null && selectedOldStatisticalResult != null){
            // Compare selected StatisticalEntries and decide if it shows a significant difference
            double biggerValue = Math.max(selectedNewStatisticalResult.getMrr(),
                    selectedOldStatisticalResult.getMrr());
            double smallerValue = Math.min(selectedNewStatisticalResult.getMrr(),
                    selectedOldStatisticalResult.getMrr());
            Log.d(TAG, "statisticalComparison mrr: "
                    + "old: " + (Math.round(selectedOldStatisticalResult.getMrr() * 1000.0) / 1000.0)
                    + " new: " + (Math.round(selectedNewStatisticalResult.getMrr() * 1000.0) / 1000.0)
                    + " ratio: " + (Math.round((biggerValue / smallerValue) * 1000.0) / 1000.0));
            if(biggerValue / smallerValue > MRR_FACTOR_THRESHOLD){
                significantDifferenceFound = true;
            }
        }

        return significantDifferenceFound;
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
}
