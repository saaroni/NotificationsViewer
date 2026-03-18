package com.notifyglance.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import com.notifyglance.service.GlanceNotificationListenerService;

public final class NotificationListenerHelper {

    private static final String TAG = "NlsHelper";
    private static final long REBIND_COOLDOWN_MS = 15_000L;
    private static long lastRebindAttemptElapsed;

    private NotificationListenerHelper() {
    }

    public static boolean isNotificationAccessGranted(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        ComponentName componentName = new ComponentName(context, GlanceNotificationListenerService.class);
        return flat != null && flat.contains(componentName.flattenToString());
    }

    public static synchronized void requestRebindIfPermitted(Context context) {
        if (!isNotificationAccessGranted(context)) {
            Log.d(TAG, "Notification access not granted; skipping listener reset");
            return;
        }

        long nowElapsed = SystemClock.elapsedRealtime();
        if (lastRebindAttemptElapsed != 0
                && nowElapsed - lastRebindAttemptElapsed < REBIND_COOLDOWN_MS) {
            Log.d(TAG, "Skipping listener reset; last attempt was "
                    + (nowElapsed - lastRebindAttemptElapsed) + "ms ago");
            return;
        }
        lastRebindAttemptElapsed = nowElapsed;

        ComponentName componentName = new ComponentName(context, GlanceNotificationListenerService.class);
        PackageManager pm = context.getPackageManager();

        try {
            pm.setComponentEnabledSetting(componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Log.d(TAG, "Reset notification listener component state");
        } catch (Exception e) {
            Log.w(TAG, "Failed to reset notification listener component state", e);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        try {
            NotificationListenerService.requestRebind(componentName);
            Log.d(TAG, "Requested notification listener rebind after component reset");
        } catch (Exception e) {
            Log.w(TAG, "Failed to request notification listener rebind", e);
        }
    }
}
