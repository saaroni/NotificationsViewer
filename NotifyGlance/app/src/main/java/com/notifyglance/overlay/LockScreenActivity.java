package com.notifyglance.overlay;

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.notifyglance.R;
import com.notifyglance.db.AppDatabase;
import com.notifyglance.model.NotificationEntity;
import com.notifyglance.util.Prefs;
import com.notifyglance.util.WakeUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "LockScreenActivity";

    private Handler handler;
    private Runnable advanceRunnable;
    private ExecutorService executor;
    private Prefs prefs;

    private List<NotificationEntity> queue = new ArrayList<>();
    private int currentIndex = 0;

    private TextView tvCountdown;
    private ScrollView svNotifications;
    private LinearLayout llNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km != null) {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override
                    public void onDismissError() {
                        Log.d(TAG, "Keyguard dismiss error - showing over lock screen anyway");
                    }

                    @Override
                    public void onDismissSucceeded() {
                        Log.d(TAG, "Keyguard dismissed");
                    }

                    @Override
                    public void onDismissCancelled() {
                        Log.d(TAG, "Keyguard dismiss cancelled - showing over lock screen anyway");
                    }
                });
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );

        setContentView(R.layout.activity_lock_screen);

        tvCountdown = findViewById(R.id.tv_countdown);
        svNotifications = findViewById(R.id.sv_notifications);
        llNotifications = findViewById(R.id.ll_notifications);

        View closeButton = findViewById(R.id.btn_close_overlay);
        closeButton.setOnClickListener(v -> finishOverlay());

        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        prefs = new Prefs(this);

        loadAndShow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent - restarting lock-screen list flow");
        cancelAdvance();
        queue.clear();
        currentIndex = 0;
        loadAndShow();
    }

    private void loadAndShow() {
        executor.execute(() -> {
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            AppDatabase.getInstance(this).notificationDao().deleteOlderThan(sevenDaysAgo);

            List<NotificationEntity> unpresented =
                    AppDatabase.getInstance(this).notificationDao().getUnpresented();

            if (unpresented.isEmpty()) {
                AppDatabase.getInstance(this).notificationDao().resetAllPresented();
                unpresented = AppDatabase.getInstance(this).notificationDao().getAllForCycle();
            }

            int max = prefs.getMaxCards();
            List<NotificationEntity> toShow = unpresented.size() > max
                    ? unpresented.subList(0, max)
                    : new ArrayList<>(unpresented);

            if (toShow.isEmpty()) {
                Log.d(TAG, "No notifications - finishing lock-screen activity");
                runOnUiThread(this::finishOverlay);
                return;
            }

            for (NotificationEntity item : toShow) {
                AppDatabase.getInstance(this).notificationDao().markPresented(item.id);
            }

            List<NotificationEntity> finalList = new ArrayList<>(toShow);
            runOnUiThread(() -> {
                queue = finalList;
                currentIndex = 0;
                prefs.setLastOverlayTime(System.currentTimeMillis());
                showCountdownThenList();
            });
        });
    }

    private void showCountdownThenList() {
        cancelAdvance();
        svNotifications.setVisibility(View.GONE);
        tvCountdown.setVisibility(View.VISIBLE);

        final int[] remaining = {5};
        advanceRunnable = new Runnable() {
            @Override
            public void run() {
                tvCountdown.setText(String.valueOf(remaining[0]));
                if (remaining[0] == 0) {
                    showNotificationList();
                    return;
                }
                remaining[0]--;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(advanceRunnable);
    }

    private void showNotificationList() {
        tvCountdown.setVisibility(View.GONE);
        svNotifications.setVisibility(View.VISIBLE);
        llNotifications.removeAllViews();

        for (NotificationEntity n : queue) {
            View item = getLayoutInflater().inflate(R.layout.lock_screen_list_item, llNotifications, false);
            bindListItem(item, n);
            applyTheme(item);
            llNotifications.addView(item);
        }

        scheduleAutoScroll();
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
        currentIndex = 0;

        advanceRunnable = new Runnable() {
            @Override
            public void run() {
                currentIndex++;
                if (currentIndex >= llNotifications.getChildCount()) {
                    finishOverlay();
                    return;
                }

                View next = llNotifications.getChildAt(currentIndex);
                svNotifications.smoothScrollTo(0, next.getTop());
                handler.postDelayed(this, delayMs);
            }
        };

        handler.postDelayed(advanceRunnable, delayMs);
    }

    private void applyTheme(View card) {
        if (!prefs.isHighContrast()) return;
        findViewById(android.R.id.content).setBackgroundColor(Color.BLACK);
        ((TextView) findViewById(R.id.btn_close_overlay)).setTextColor(Color.WHITE);
        ((TextView) findViewById(R.id.tv_header)).setTextColor(Color.WHITE);
        setTextColor(card, Color.WHITE);
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

    private String formatDateTime(long ts) {
        return new SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(new Date(ts));
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

    private void cancelAdvance() {
        if (advanceRunnable != null) {
            handler.removeCallbacks(advanceRunnable);
            advanceRunnable = null;
        }
    }

    private void finishOverlay() {
        cancelAdvance();
        WakeUtil.release();
        finish();
    }

    @Override
    protected void onDestroy() {
        cancelAdvance();
        if (executor != null) executor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Keep lock-screen behavior hands-free unless user taps close button.
    }
}
