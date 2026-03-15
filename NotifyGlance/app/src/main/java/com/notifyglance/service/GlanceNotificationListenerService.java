package com.notifyglance.service;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import com.notifyglance.overlay.OverlayService;
import com.notifyglance.util.WakeUtil;

import com.notifyglance.db.AppDatabase;
import com.notifyglance.model.NotificationEntity;
import com.notifyglance.util.Prefs;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GlanceNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "GlanceNLS";
    private ExecutorService executor;
    private Prefs prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        prefs = new Prefs(this);
        Log.d(TAG, "NotificationListenerService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        // Check capture allowed
        boolean masterOn = prefs.isMasterOn();
        if (!masterOn && !prefs.isCaptureWhileOff()) return;

        executor.execute(() -> handleNotification(sbn, masterOn));
    }

    private void handleNotification(StatusBarNotification sbn, boolean masterOn) {
        try {
            String pkg = sbn.getPackageName();

            // Skip our own package
            if (pkg.equals(getPackageName())) return;

            Notification notification = sbn.getNotification();
            if (notification == null) return;

            // Filter allowed apps (empty set = all allowed)
            Set<String> allowedApps = prefs.getAllowedApps();
            if (!allowedApps.isEmpty() && !allowedApps.contains(pkg)) return;

            // Suppress ongoing
            if (prefs.isSuppressOngoing()) {
                boolean isOngoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0
                        || (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
                if (isOngoing) return;
            }

            // Extract text
            Bundle extras = notification.extras;
            String title = extras != null ? charSeqToString(extras.getCharSequence(Notification.EXTRA_TITLE)) : "";
            String text  = extras != null ? charSeqToString(extras.getCharSequence(Notification.EXTRA_TEXT)) : "";
            String sub   = extras != null ? charSeqToString(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)) : "";

            if (TextUtils.isEmpty(title) && TextUtils.isEmpty(text)) return;

            // App label
            String appLabel = getAppLabel(pkg);

            boolean isOngoing2 = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
            int importance = sbn.getNotification().priority;

            long capturedAt = System.currentTimeMillis();
            long postedAt = sbn.getPostTime() > 0 ? sbn.getPostTime() : capturedAt;

            NotificationEntity entity = new NotificationEntity(
                    pkg, appLabel, title, text, sub,
                    postedAt, capturedAt, isOngoing2, importance);

            AppDatabase.getInstance(this).notificationDao().insert(entity);
            Log.d(TAG, "Stored notification from " + appLabel + ": " + title);

            // Trigger overlay if master is on, trigger-on-new is enabled, and not quiet hours
            if (masterOn && prefs.isTriggerOnNew() && !prefs.isQuietNow()) {
                WakeUtil.acquireTemporary(this);
                OverlayService.triggerOverlay(this);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling notification", e);
        }
    }

    private String getAppLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String charSeqToString(CharSequence cs) {
        return cs != null ? cs.toString() : "";
    }
}
