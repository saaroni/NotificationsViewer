package com.notifyglance.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.notifyglance.overlay.OverlayService;
import com.notifyglance.util.Prefs;

import java.util.concurrent.TimeUnit;

public class TimedSessionWorker extends Worker {

    private static final String TAG          = "TimedSessionWorker";
    private static final String WORK_NAME    = "timed_overlay_session";

    public TimedSessionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Prefs prefs = new Prefs(getApplicationContext());

        if (!prefs.isMasterOn()) {
            Log.d(TAG, "Master off, skipping timed session");
            return Result.success();
        }
        if (prefs.isQuietNow()) {
            Log.d(TAG, "Quiet hours, skipping timed session");
            return Result.success();
        }

        Log.d(TAG, "Timed session triggered");
        OverlayService.triggerOverlay(getApplicationContext());
        return Result.success();
    }

    /** Schedule or cancel WorkManager periodic work based on current prefs */
    public static void reschedule(Context context) {
        Prefs prefs = new Prefs(context);
        WorkManager wm = WorkManager.getInstance(context);
        String mode = prefs.getTimedSession();

        if (Prefs.TIMED_OFF.equals(mode)) {
            wm.cancelUniqueWork(WORK_NAME);
            Log.d(TAG, "Timed sessions disabled");
            return;
        }

        long intervalMinutes = prefs.getTimedSessionIntervalMinutes();
        if (intervalMinutes <= 0) {
            wm.cancelUniqueWork(WORK_NAME);
            Log.d(TAG, "Timed sessions disabled due to invalid interval");
            return;
        }

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                TimedSessionWorker.class, intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.NONE)
                .build();

        wm.enqueueUniquePeriodicWork(WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, request);
        Log.d(TAG, "Timed sessions scheduled every " + intervalMinutes + " min");
    }
}
