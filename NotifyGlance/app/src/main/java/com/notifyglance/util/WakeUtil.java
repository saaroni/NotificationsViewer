package com.notifyglance.util;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

public class WakeUtil {

    private static final String TAG = "WakeUtil";
    private static PowerManager.WakeLock wakeLock;

    /**
     * Acquires a full wake lock for 30 seconds.
     * This wakes the screen even when the device is in Doze/sleep.
     */
    public static void acquireTemporary(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE,
                "NotifyGlance::ScreenWake"
            );
            wakeLock.acquire(30_000L); // auto release after 30s
            Log.d(TAG, "WakeLock acquired - screen should be on");
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire wake lock", e);
        }
    }

    public static void release() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }
    }
}