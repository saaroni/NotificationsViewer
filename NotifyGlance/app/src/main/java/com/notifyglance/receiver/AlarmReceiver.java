package com.notifyglance.receiver;

import android.app.KeyguardManager;
import android.os.PowerManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.notifyglance.db.AppDatabase;
import com.notifyglance.overlay.OverlayService;
import com.notifyglance.util.AlarmScheduler;
import com.notifyglance.util.Prefs;
import com.notifyglance.util.WakeUtil;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "=== ALARM FIRED ===");

        // Check screen state
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm.isInteractive();
        Log.d(TAG, "Screen is: " + (screenOn ? "ON" : "OFF"));

        // Check keyguard state
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km.isKeyguardLocked();
        Log.d(TAG, "Keyguard locked: " + locked);

        Prefs prefs = new Prefs(context);
        Log.d(TAG, "Master on: " + prefs.isMasterOn());
        Log.d(TAG, "Quiet now: " + prefs.isQuietNow());

        if (!prefs.isMasterOn()) {
            AlarmScheduler.schedule(context);
            return;
        }
        if (prefs.isQuietNow()) {
            AlarmScheduler.schedule(context);
            return;
        }

        long lookbackThreshold = System.currentTimeMillis()
                - (prefs.getOverlayLookbackMinutes() * 60L * 1000L);
        int pendingCount = AppDatabase.getInstance(context)
                .notificationDao()
                .countUnpresentedSince(lookbackThreshold);
        int totalCount = AppDatabase.getInstance(context)
                .notificationDao()
                .countAllSince(lookbackThreshold);
        Log.d(TAG, "Notifications in lookback: pending=" + pendingCount + ", total=" + totalCount);

        if (pendingCount <= 0 && totalCount <= 0) {
            Log.d(TAG, "No notifications available, skipping wake/overlay trigger");
            AlarmScheduler.schedule(context);
            return;
        }

        // Acquire wake lock
        WakeUtil.acquireTemporary(context);
        Log.d(TAG, "WakeLock acquired");

        // Launch overlay via foreground service.
        // Starting an Activity directly from a background receiver is blocked on modern Android.
        try {
            OverlayService.triggerOverlay(context);
            Log.d(TAG, "OverlayService trigger sent successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to trigger OverlayService: " + e.getMessage());
        }

        AlarmScheduler.schedule(context);
    }
}
