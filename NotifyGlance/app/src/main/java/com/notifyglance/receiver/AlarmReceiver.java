package com.notifyglance.receiver;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.notifyglance.db.AppDatabase;
import com.notifyglance.overlay.OverlayService;
import com.notifyglance.util.AlarmScheduler;
import com.notifyglance.util.Prefs;
import com.notifyglance.util.WakeUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "=== ALARM FIRED ===");

        // Check screen state
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm != null && pm.isInteractive();
        Log.d(TAG, "Screen is: " + (screenOn ? "ON" : "OFF"));

        // Check keyguard state
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        Log.d(TAG, "Keyguard locked: " + locked);

        Prefs prefs = new Prefs(context);
        Log.d(TAG, "Master on: " + prefs.isMasterOn());
        Log.d(TAG, "Quiet now: " + prefs.isQuietNow());

        if (!prefs.isMasterOn() || prefs.isQuietNow()) {
            AlarmScheduler.schedule(context);
            return;
        }

        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        DB_EXECUTOR.execute(() -> {
            try {
                long lookbackThreshold = System.currentTimeMillis()
                        - (prefs.getOverlayLookbackMinutes() * 60L * 1000L);

                int pendingCount = AppDatabase.getInstance(appContext)
                        .notificationDao()
                        .countUnpresentedSince(lookbackThreshold);
                int totalCount = AppDatabase.getInstance(appContext)
                        .notificationDao()
                        .countAllSince(lookbackThreshold);

                Log.d(TAG, "Notifications in lookback: pending=" + pendingCount + ", total=" + totalCount);

                if (pendingCount <= 0 && totalCount <= 0) {
                    Log.d(TAG, "No notifications available, skipping wake/overlay trigger");
                    AlarmScheduler.schedule(appContext);
                    return;
                }

                // Acquire wake lock
                WakeUtil.acquireTemporary(appContext);
                Log.d(TAG, "WakeLock acquired");

                // Launch overlay via foreground service.
                // Starting an Activity directly from a background receiver is blocked on modern Android.
                try {
                    OverlayService.triggerOverlay(appContext);
                    Log.d(TAG, "OverlayService trigger sent successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to trigger OverlayService", e);
                }

                AlarmScheduler.schedule(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Alarm processing failed", e);
                AlarmScheduler.schedule(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
