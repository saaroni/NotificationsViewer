package com.notifyglance.service;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import com.notifyglance.db.AppDatabase;
import com.notifyglance.model.NotificationEntity;
import com.notifyglance.overlay.OverlayService;
import com.notifyglance.util.Prefs;
import com.notifyglance.util.WakeUtil;

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
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification listener connected");
        ensureExecutor();
        StatusBarNotification[] activeNotifications;
        try {
            activeNotifications = getActiveNotifications();
        } catch (Exception e) {
            Log.w(TAG, "Unable to query active notifications on connect", e);
            return;
        }

        if (activeNotifications == null || activeNotifications.length == 0) {
            Log.d(TAG, "Listener connected with no active notifications to import");
            return;
        }

        Log.d(TAG, "Importing " + activeNotifications.length + " active notifications after listener connection");
        boolean masterOn = prefs != null && prefs.isMasterOn();
        if (!masterOn && !prefs.isCaptureWhileOff()) {
            Log.d(TAG, "Listener connected while capture is disabled; skipping active notification import");
            return;
        }

        for (StatusBarNotification sbn : activeNotifications) {
            executor.execute(() -> handleNotification(sbn, masterOn, false));
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        ensureExecutor();

        boolean masterOn = prefs.isMasterOn();
        if (!masterOn && !prefs.isCaptureWhileOff()) {
            Log.d(TAG, "Skipping notification capture because master is off and capture-while-off is disabled");
            return;
        }

        executor.execute(() -> handleNotification(sbn, masterOn, true));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        ensureExecutor();
        executor.execute(() -> {
            int deleted = AppDatabase.getInstance(this).notificationDao().deleteByNotificationKey(sbn.getKey());
            if (deleted > 0) {
                Log.d(TAG, "Removed stored notification for key=" + sbn.getKey());
            }
        });
    }

    private void handleNotification(StatusBarNotification sbn, boolean masterOn, boolean allowOverlayTrigger) {
        try {
            String pkg = sbn.getPackageName();

            if (pkg.equals(getPackageName())) {
                Log.d(TAG, "Skipping app's own notification");
                return;
            }

            Notification notification = sbn.getNotification();
            if (notification == null) {
                Log.d(TAG, "Skipping null notification from " + pkg);
                return;
            }

            Set<String> allowedApps = prefs.getAllowedApps();
            if (!allowedApps.isEmpty() && !allowedApps.contains(pkg)) {
                Log.d(TAG, "Skipping notification from disallowed app: " + pkg);
                return;
            }

            boolean isOngoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0
                    || (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
            if (prefs.isSuppressOngoing() && isOngoing) {
                Log.d(TAG, "Skipping ongoing/foreground notification from " + pkg);
                return;
            }

            Bundle extras = notification.extras;
            String title = extras != null ? charSeqToString(extras.getCharSequence(Notification.EXTRA_TITLE)) : "";
            String text = extras != null ? charSeqToString(extras.getCharSequence(Notification.EXTRA_TEXT)) : "";
            String sub = extras != null ? charSeqToString(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)) : "";

            if (TextUtils.isEmpty(title) && TextUtils.isEmpty(text)) {
                Log.d(TAG, "Skipping notification without visible title/text from " + pkg);
                return;
            }

            String appLabel = getAppLabel(pkg);
            int importance = notification.priority;

            long capturedAt = System.currentTimeMillis();
            long postedAt = sbn.getPostTime() > 0 ? sbn.getPostTime() : capturedAt;

            NotificationEntity entity = new NotificationEntity(
                    sbn.getKey(), pkg, appLabel, title, text, sub,
                    postedAt, capturedAt, isOngoing, importance);

            AppDatabase.getInstance(this).notificationDao().insert(entity);
            Log.d(TAG, "Stored notification key=" + sbn.getKey() + " from " + appLabel + ": " + title);

            if (allowOverlayTrigger && masterOn && prefs.isTriggerOnNew() && !prefs.isQuietNow()) {
                WakeUtil.acquireTemporary(this);
                OverlayService.triggerOverlayCard(this);
                Log.d(TAG, "Triggered overlay for newly captured notification");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling notification", e);
        }
    }

    private void ensureExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        if (prefs == null) {
            prefs = new Prefs(this);
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
