package com.notifyglance.util;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import com.notifyglance.service.GlanceNotificationListenerService;

public final class NotificationListenerHelper {

    private static final String TAG = "NlsHelper";

    private NotificationListenerHelper() {
    }

    public static boolean isNotificationAccessGranted(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        ComponentName componentName = new ComponentName(context, GlanceNotificationListenerService.class);
        return flat != null && flat.contains(componentName.flattenToString());
    }

    public static void requestRebindIfPermitted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        if (!isNotificationAccessGranted(context)) {
            Log.d(TAG, "Notification access not granted; skipping listener rebind request");
            return;
        }

        try {
            ComponentName componentName = new ComponentName(context, GlanceNotificationListenerService.class);
            NotificationListenerService.requestRebind(componentName);
            Log.d(TAG, "Requested notification listener rebind");
        } catch (Exception e) {
            Log.w(TAG, "Failed to request notification listener rebind", e);
        }
    }
}
