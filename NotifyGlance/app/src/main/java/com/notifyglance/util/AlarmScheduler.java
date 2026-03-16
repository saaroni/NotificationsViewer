package com.notifyglance.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.notifyglance.receiver.AlarmReceiver;

public class AlarmScheduler {

    private static final String TAG    = "AlarmScheduler";
    private static final int    REQ_ID = 7001;

    public static void schedule(Context context) {
        Prefs prefs = new Prefs(context);

        if (!prefs.isMasterOn()) {
            cancel(context);
            return;
        }

        String mode = prefs.getTimedSession();
        if (Prefs.TIMED_OFF.equals(mode)) {
            cancel(context);
            return;
        }

        int intervalMinutes = prefs.getTimedSessionIntervalMinutes();
        if (intervalMinutes <= 0) {
            cancel(context);
            return;
        }


        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getPendingIntent(context);

        long triggerAt = computeNextAlignedTriggerAt(intervalMinutes);

        // setExactAndAllowWhileIdle fires even during Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }

        Log.d(TAG, "Alarm scheduled at " + new java.util.Date(triggerAt) + " (every " + intervalMinutes + " minutes)");
    }


    private static long computeNextAlignedTriggerAt(int intervalMinutes) {
        long now = System.currentTimeMillis();
        long intervalMs = intervalMinutes * 60L * 1000L;
        long currentBucket = now / intervalMs;
        return (currentBucket + 1) * intervalMs;
    }

    public static long getNextScheduledTriggerAtMillis(Context context) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isMasterOn()) return -1;
        if (Prefs.TIMED_OFF.equals(prefs.getTimedSession())) return -1;

        int intervalMinutes = prefs.getTimedSessionIntervalMinutes();
        if (intervalMinutes <= 0) return -1;

        return computeNextAlignedTriggerAt(intervalMinutes);
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getPendingIntent(context));
        Log.d(TAG, "Alarm cancelled");
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(context, REQ_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}