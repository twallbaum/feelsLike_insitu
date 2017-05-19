package de.offis.feelslike.insituarousal;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.Arrays;
import java.util.Random;

public class InputService extends IntentService {

    private Class [] activities = {ArousalInput.class};
    private int [] inputsOrder = {0, 0, 0, 0};

    private static final String ACTION_START = "de.offis.contact.moodmessengerinput.action.START";
    private static final String ACTION_STOP = "de.offis.contact.moodmessengerinput.action.STOP";
    private static final String ACTION_NOTIFY = "de.offis.contact.moodmessengerinput.action.NOTIFY";
    private static final String ACTION_INPUT = "de.offis.contact.moodmessengerinput.action.INPUT";

    private int activitySelector;

    public InputService() {
        super("InputService");
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, InputService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, InputService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        final String action = intent.getAction();

        if (ACTION_START.equals(action)) {
           this.handleStartAction();

        } else if(ACTION_NOTIFY.equals(action)) {
            this.handleNotifyAction();

        } else if(ACTION_INPUT.equals(action)) {
            this.handleInputAction();

        } else if(ACTION_STOP.equals(action)) {
            this.handleStopAction();
        }
    }

    private void handleStopAction() {
        this.deleteAlarm();

        NotificationManager mNotifyMgr =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyMgr.cancelAll();
    }

    private void handleInputAction() {
        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        String inputs = sharedPref.getString("inputs", "");

        if(inputs.length() > 1) {
            String[] substrings = inputs.split(",", 2);

            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("inputs", substrings[1]);
            editor.commit();

            activitySelector = Integer.parseInt(substrings[0].substring(0, 1));
            this.setupAlarm();
        }
        else {
            activitySelector = Integer.parseInt(inputs);
            InputService.stop(this);
        }

        Intent intent = new Intent(this, this.activities[this.activitySelector]);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
    }

    private void handleNotifyAction() {
        Intent intent = new Intent(this, InputService.class);
        intent.setAction(ACTION_INPUT);

        PendingIntent pendingIntent = PendingIntent.getService(this, 21, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        this.throwNotification(this, pendingIntent);
    }

    private void handleStartAction() {
        this.shuffleInputs(this.inputsOrder);
        String inputsString = (Arrays.toString(this.inputsOrder)).replaceAll("\\s","");

        //Log.i("InputService", "orig string: " + inputsString.substring(1, inputsString.length()-1));

        SharedPreferences sharedPref = getSharedPreferences("MoodMessengerPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("inputs", inputsString.substring(1, inputsString.length()-1));
        editor.commit();

        this.setupAlarm();
    }

    private PendingIntent createPendingIntent(Context context, Intent intent) {
        return PendingIntent.getService(context, 42, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private void setupAlarm() {
        Intent intent = new Intent(this, InputService.class);
        intent.setAction(ACTION_NOTIFY);

        PendingIntent pendingIntent = this.createPendingIntent(this, intent);

        //long offset = (60*1000)*(45+((long)Math.random()*15));
        long offset = (10*1000);

        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarm.set(AlarmManager.RTC, System.currentTimeMillis() + offset, pendingIntent);
    }

    private void deleteAlarm() {
        Intent intent = new Intent(this, InputService.class);
        intent.setAction(ACTION_NOTIFY);

        PendingIntent pendingIntent = PendingIntent.getService(this, 42, intent, PendingIntent.FLAG_NO_CREATE);

        if(pendingIntent != null) {
            AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarm.cancel(pendingIntent);
        }else {Log.wtf("InputService", "WTF");}
    }


    private void throwNotification(Context context, PendingIntent pendingIntent) {
        long[] pattern = {200, 200, 200, 200};
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.notification)
                        .setContentTitle("Feels like S1")
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

    private void shuffleInputs(int[] ar)
    {
        Random rnd = new Random();
        for (int i = ar.length - 1; i > 0; i--)
        {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            int a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }
}
