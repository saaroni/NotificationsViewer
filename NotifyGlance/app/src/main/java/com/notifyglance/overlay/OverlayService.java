package com.notifyglance.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.notifyglance.MainActivity;
import com.notifyglance.R;
import com.notifyglance.db.AppDatabase;
import com.notifyglance.model.NotificationEntity;
import com.notifyglance.util.Prefs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {

    private static final String TAG = "OverlayService";
    private static final String CHANNEL_ID = "overlay_service_channel";
    private static final String LOCKSCREEN_CHANNEL_ID = "overlay_lockscreen_channel";
    private static final int FG_NOTIF_ID = 1001;
    private static final int LOCKSCREEN_NOTIF_ID = 1002;
    public static final String ACTION_TRIGGER = "com.notifyglance.TRIGGER_OVERLAY";
    public static final String ACTION_TRIGGER_CARD = "com.notifyglance.TRIGGER_OVERLAY_CARD";
    public static final String ACTION_STOP = "com.notifyglance.STOP_OVERLAY";
    public static final String ACTION_TEST = "com.notifyglance.TEST_OVERLAY";
    public static final String EXTRA_SKIP_LOCK_COUNTDOWN = "com.notifyglance.EXTRA_SKIP_LOCK_COUNTDOWN";

    private WindowManager windowManager;
    private View overlayView;
    private ScrollView overlayScrollView;
    private Handler handler;
    private Runnable advanceRunnable;
    private ExecutorService executor;
    private Prefs prefs;

    private List<NotificationEntity> queue = new ArrayList<>();
    private int scrollIndex = 0;
    private boolean overlayShowing = false;

    public static void triggerOverlay(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class);
        i.setAction(ACTION_TRIGGER);
        ctx.startForegroundService(i);
    }

    public static void stopOverlay(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    public static void triggerOverlayCard(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class);
        i.setAction(ACTION_TRIGGER_CARD);
        ctx.startForegroundService(i);
    }

    public static void testOverlay(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class);
        i.setAction(ACTION_TEST);
        ctx.startForegroundService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        prefs = new Prefs(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotificationChannel();
        createLockScreenChannel();
        startForeground(FG_NOTIF_ID, buildForegroundNotification());

        Log.d(TAG, "OverlayService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (action == null) action = ACTION_TRIGGER;

        switch (action) {
            case ACTION_STOP:
                hideOverlay();
                stopSelf();
                break;
            case ACTION_TEST:
                try {
                    showTestCard();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to show test overlay", e);
                    stopSelf();
                }
                break;
            case ACTION_TRIGGER_CARD:
                if (isDeviceLocked()) {
                    launchLockScreenFlow(true);
                } else {
                    loadQueueAndShow(true);
                }
                break;
            case ACTION_TRIGGER:
            default:
                if (isDeviceLocked()) {
                    launchLockScreenFlow(false);
                } else {
                    loadQueueAndShow(false);
                }
                break;
        }
        return START_STICKY;
    }

    private boolean isDeviceLocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    private void launchLockScreenFlow(boolean skipCountdown) {
        Log.d(TAG, "Device locked - attempting lock-screen activity launch flow");
        if (tryLaunchLockScreenActivity(skipCountdown)) {
            return;
        }
        Log.d(TAG, "Direct launch unavailable - using full-screen notification fallback");
        postLockScreenFullScreenNotification(skipCountdown);
    }

    private boolean tryLaunchLockScreenActivity(boolean skipCountdown) {
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.putExtra(EXTRA_SKIP_LOCK_COUNTDOWN, skipCountdown);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            startActivity(lockIntent);
            Log.d(TAG, "Lock screen is active - launched LockScreenActivity directly");
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Direct LockScreenActivity launch failed, falling back to full-screen notification", e);
            return false;
        }
    }

    private void postLockScreenFullScreenNotification(boolean skipCountdown) {
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.putExtra(EXTRA_SKIP_LOCK_COUNTDOWN, skipCountdown);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this,
                2001,
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, LOCKSCREEN_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_overlay_notification)
                .setContentTitle("NotifyGlance")
                .setContentText("Showing lock-screen notifications")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenPi, true)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(LOCKSCREEN_NOTIF_ID, notification);
            Log.d(TAG, "Lock screen is active - posted full-screen notification intent");
        } else {
            Log.w(TAG, "NotificationManager unavailable; could not post full-screen lock-screen notification");
        }
    }

    private void loadQueueAndShow(boolean cardMode) {
        executor.execute(() -> {
            long lookbackThreshold = System.currentTimeMillis()
                    - (prefs.getOverlayLookbackMinutes() * 60L * 1000L);

            List<NotificationEntity> allWithinLookback =
                    AppDatabase.getInstance(this).notificationDao().getAllForCycleSince(lookbackThreshold);

            int max = prefs.getMaxCards();
            List<NotificationEntity> toShow = allWithinLookback.size() > max
                    ? allWithinLookback.subList(0, max)
                    : new ArrayList<>(allWithinLookback);

            if (toShow.isEmpty()) {
                Log.d(TAG, "No notifications to show in lookback window");
                return;
            }

            for (NotificationEntity item : toShow) {
                AppDatabase.getInstance(this).notificationDao().markPresented(item.id);
            }

            List<NotificationEntity> finalList = new ArrayList<>(toShow);
            handler.post(() -> {
                if (cardMode) {
                    showOverlayCards(finalList);
                } else {
                    showCountdownThenList(finalList);
                }
            });
        });
    }

    private void showCountdownThenList(List<NotificationEntity> items) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission");
            return;
        }

        hideOverlayView();
        cancelAdvance();

        LayoutInflater inflater = LayoutInflater.from(this);
        View countdownView = inflater.inflate(R.layout.overlay_countdown, null);
        TextView tvCountdown = countdownView.findViewById(R.id.tv_countdown);

        WindowManager.LayoutParams params = buildOverlayLayoutParams(false);
        overlayView = countdownView;
        if (!safeAddOverlayView(overlayView, params)) {
            overlayView = null;
            return;
        }
        overlayShowing = true;
        acquireWakeLock();

        final int[] remaining = {5};
        Runnable countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!overlayShowing || overlayView == null) return;
                tvCountdown.setText(String.valueOf(remaining[0]));
                if (remaining[0] == 0) {
                    showOverlayList(items);
                    return;
                }
                remaining[0]--;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(countdownRunnable);
    }

    private void showOverlayList(List<NotificationEntity> items) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission");
            return;
        }

        hideOverlayView();
        cancelAdvance();

        queue = new ArrayList<>(items);
        scrollIndex = 0;

        LayoutInflater inflater = LayoutInflater.from(this);
        View panel = inflater.inflate(R.layout.overlay_list, null);
        overlayScrollView = panel.findViewById(R.id.sv_notifications);
        View closeButton = panel.findViewById(R.id.btn_close_overlay);
        LinearLayout listContainer = panel.findViewById(R.id.ll_notifications);
        closeButton.setOnClickListener(v -> hideOverlay());

        for (NotificationEntity n : queue) {
            View item = inflater.inflate(R.layout.overlay_list_item, listContainer, false);
            bindListItem(item, n);
            applyTheme(item);
            listContainer.addView(item);
        }

        overlayView = panel;
        if (!safeAddOverlayView(overlayView, buildOverlayLayoutParams(false))) {
            overlayView = null;
            return;
        }
        overlayShowing = true;
        prefs.setLastOverlayTime(System.currentTimeMillis());
        acquireWakeLock();

        scheduleAutoScroll();
    }

    private void showOverlayCards(List<NotificationEntity> items) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission");
            return;
        }

        hideOverlayView();
        cancelAdvance();

        queue = new ArrayList<>(items);
        scrollIndex = 0;
        showCurrentOverlayCard();
    }

    private void showCurrentOverlayCard() {
        if (scrollIndex >= queue.size()) {
            hideOverlay();
            return;
        }

        hideOverlayView();
        LayoutInflater inflater = LayoutInflater.from(this);
        View card = inflater.inflate(R.layout.overlay_card, null);

        bindOverlayCard(card, queue.get(scrollIndex));
        applyTheme(card);

        View closeButton = card.findViewById(R.id.btn_close_overlay);
        closeButton.setOnClickListener(v -> hideOverlay());

        overlayView = card;
        if (!safeAddOverlayView(overlayView, buildOverlayLayoutParams(true))) {
            overlayView = null;
            return;
        }
        overlayShowing = true;
        prefs.setLastOverlayTime(System.currentTimeMillis());
        acquireWakeLock();

        long delayMs = (long) (prefs.getCardDisplaySec() * 1000);
        advanceRunnable = () -> {
            scrollIndex++;
            showCurrentOverlayCard();
        };
        handler.postDelayed(advanceRunnable, delayMs);
    }

    private void bindOverlayCard(View card, NotificationEntity n) {
        TextView tvApp = card.findViewById(R.id.tv_app_name);
        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvText = card.findViewById(R.id.tv_text);
        TextView tvTime = card.findViewById(R.id.tv_time);
        TextView tvIndex = card.findViewById(R.id.tv_index);

        tvApp.setText(n.appLabel != null ? n.appLabel : n.packageName);
        tvTitle.setText(n.title != null ? n.title : "");
        tvText.setText(n.text != null ? n.text : "");
        tvTime.setText(formatTime(n.postedAt));
        tvIndex.setText((scrollIndex + 1) + " / " + queue.size());

        float sp = fontSizeSp();
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        tvText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp - 2);
    }

    private WindowManager.LayoutParams buildOverlayLayoutParams(boolean cardMode) {
        int overlayHeight;
        if (cardMode) {
            overlayHeight = WindowManager.LayoutParams.WRAP_CONTENT;
        } else {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            overlayHeight = (int) (screenHeight * 0.75f);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 60;
        return params;
    }

    private void bindListItem(View card, NotificationEntity n) {
        TextView tvApp = card.findViewById(R.id.tv_app_name);
        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvText = card.findViewById(R.id.tv_text);
        TextView tvTime = card.findViewById(R.id.tv_time);

        tvApp.setText(n.appLabel != null ? n.appLabel : n.packageName);
        tvTitle.setText(n.title != null ? n.title : "");
        tvText.setText(n.text != null ? n.text : "");
        tvTime.setText("Captured " + formatDateTime(n.capturedAt));

        float sp = fontSizeSp();
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp - 2);
        tvText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp - 5);
    }

    private void scheduleAutoScroll() {
        long delayMs = (long) (prefs.getCardDisplaySec() * 1000);
        advanceRunnable = new Runnable() {
            @Override
            public void run() {
                if (!overlayShowing || overlayScrollView == null || overlayView == null) {
                    return;
                }

                LinearLayout listContainer = overlayView.findViewById(R.id.ll_notifications);
                scrollIndex++;
                if (listContainer == null || scrollIndex >= listContainer.getChildCount()) {
                    hideOverlay();
                    return;
                }

                View next = listContainer.getChildAt(scrollIndex);
                overlayScrollView.smoothScrollTo(0, next.getTop());
                handler.postDelayed(this, delayMs);
            }
        };

        handler.postDelayed(advanceRunnable, delayMs);
    }

    private void applyTheme(View card) {
        if (prefs.isHighContrast()) {
            card.setBackgroundColor(Color.BLACK);
            setTextColor(card, Color.WHITE);
        }
    }

    private void setTextColor(View root, int color) {
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView) ((TextView) child).setTextColor(color);
                else setTextColor(child, color);
            }
        }
    }

    private float fontSizeSp() {
        switch (prefs.getFontSize()) {
            case "large":
                return 16f;
            case "xxl":
                return 24f;
            case "xl":
            default:
                return 20f;
        }
    }

    private void showTestCard() {
        long now = System.currentTimeMillis();
        NotificationEntity newest = new NotificationEntity(
                "com.example.chat", "Messages",
                "Dinner at 8?", "Let's meet near the station. I'll be there in 15.",
                "", now + 1000, now + 1000, false, 0);
        NotificationEntity demo = new NotificationEntity(
                "com.example.demo", "Demo App",
                "Test Notification", "This is a test overlay card from NotifyGlance.",
                "", now, now, false, 0);
        newest.id = -1;
        demo.id = -2;
        List<NotificationEntity> list = new ArrayList<>();
        list.add(newest);
        list.add(demo);
        handler.post(() -> showCountdownThenList(list));
    }

    private boolean safeAddOverlayView(View view, WindowManager.LayoutParams params) {
        if (windowManager == null) {
            Log.e(TAG, "WindowManager unavailable; cannot show overlay");
            return false;
        }
        try {
            windowManager.addView(view, params);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view", e);
            return false;
        }
    }

    private void hideOverlay() {
        cancelAdvance();
        hideOverlayView();
        releaseWakeLock();
        overlayShowing = false;
    }

    private void hideOverlayView() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
            overlayScrollView = null;
        }
    }

    private void cancelAdvance() {
        if (advanceRunnable != null) {
            handler.removeCallbacks(advanceRunnable);
            advanceRunnable = null;
        }
    }

    private void acquireWakeLock() {
        com.notifyglance.util.WakeUtil.acquireTemporary(this);
    }

    private void releaseWakeLock() {
        com.notifyglance.util.WakeUtil.release();
    }

    private String formatDateTime(long ts) {
        return new SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(new Date(ts));
    }

    private String formatTime(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "NotifyGlance Service",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps overlay service running");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private void createLockScreenChannel() {
        NotificationChannel channel = new NotificationChannel(
                LOCKSCREEN_CHANNEL_ID,
                "NotifyGlance Lock Screen",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Launches lock-screen notification view");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildForegroundNotification() {
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NotifyGlance Active")
                .setContentText("Hands-free overlay is running")
                .setSmallIcon(R.drawable.ic_overlay_notification)
                .setContentIntent(openPi)
                .addAction(R.drawable.ic_stop, "Stop", stopPi)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        if (executor != null) executor.shutdown();
        super.onDestroy();
        Log.d(TAG, "OverlayService destroyed");
    }
}
