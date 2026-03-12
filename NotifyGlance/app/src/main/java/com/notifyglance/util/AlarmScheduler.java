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

        long intervalMs;
        if (Prefs.TIMED_HOURLY.equals(mode)) {
            intervalMs = 60 * 60 * 1000L;
        } else {
            int minutes = prefs.getCustomInterval();
            if (minutes < 1)  minutes = 1;
            if (minutes > 60) minutes = 60;
            intervalMs = minutes * 60 * 1000L;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getPendingIntent(context);

        long triggerAt = System.currentTimeMillis() + intervalMs;

        // setExactAndAllowWhileIdle fires even during Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }

        Log.d(TAG, "Alarm scheduled in " + (intervalMs / 1000 / 60) + " minutes");
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