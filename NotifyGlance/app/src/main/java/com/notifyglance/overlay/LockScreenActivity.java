package com.notifyglance.overlay;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
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

    // Views
    private TextView tvAppName, tvTitle, tvText, tvTime, tvIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Break through lock screen ──────────────────────────
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

        // Always add these window flags regardless of API level
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_lock_screen);

        tvAppName = findViewById(R.id.tv_app_name);
        tvTitle   = findViewById(R.id.tv_title);
        tvText    = findViewById(R.id.tv_text);
        tvTime    = findViewById(R.id.tv_time);
        tvIndex   = findViewById(R.id.tv_index);

        handler  = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        prefs    = new Prefs(this);

        loadAndShow();
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent - restarting cycle");
        if (advanceRunnable != null) {
            handler.removeCallbacks(advanceRunnable);
            advanceRunnable = null;
        }
        currentIndex = 0;
        queue.clear();
        loadAndShow();
    }
    
    private void loadAndShow() {
        executor.execute(() -> {
             // Auto-cleanup notifications older than 7 days
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
                Log.d(TAG, "No notifications - finishing");
                runOnUiThread(this::finish);
                return;
            }

            final List<NotificationEntity> finalList = toShow;
            runOnUiThread(() -> {
                queue = finalList;
                currentIndex = 0;
                prefs.setLastOverlayTime(System.currentTimeMillis()); 
                showCurrentCard();
            });
        });
    }

    private void showCurrentCard() {
        if (currentIndex >= queue.size()) {
            Log.d(TAG, "All cards shown, finishing");
            WakeUtil.release();
            finish();
            return;
        }

        NotificationEntity n = queue.get(currentIndex);
        bindCard(n);

        // Mark presented
        final int idx = currentIndex;
        executor.execute(() ->
                AppDatabase.getInstance(this).notificationDao().markPresented(queue.get(idx).id));

        // Auto advance
        long delayMs = (long) (prefs.getCardDisplaySec() * 1000);
        advanceRunnable = () -> {
            currentIndex++;
            showCurrentCard();
        };
        handler.postDelayed(advanceRunnable, delayMs);
    }

    private void bindCard(NotificationEntity n) {
        tvAppName.setText(n.appLabel != null ? n.appLabel : n.packageName);
        tvTitle.setText(n.title != null ? n.title : "");
        tvText.setText(n.text  != null ? n.text  : "");
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(n.postedAt)));
        tvIndex.setText((currentIndex + 1) + " / " + queue.size());

        // Apply font size
        float sp = fontSizeSp();
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        tvText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp - 2);

        // High contrast
        if (prefs.isHighContrast()) {
            findViewById(R.id.card_root).setBackgroundColor(android.graphics.Color.BLACK);
            tvTitle.setTextColor(android.graphics.Color.WHITE);
            tvText.setTextColor(android.graphics.Color.WHITE);
            tvAppName.setTextColor(android.graphics.Color.WHITE);
            tvTime.setTextColor(android.graphics.Color.WHITE);
            tvIndex.setTextColor(android.graphics.Color.WHITE);
        }
    }

    private float fontSizeSp() {
        switch (prefs.getFontSize()) {
            case "large": return 16f;
            case "xxl":   return 24f;
            default:      return 20f;
        }
    }

    @Override
    protected void onDestroy() {
        if (advanceRunnable != null) handler.removeCallbacks(advanceRunnable);
        if (executor != null) executor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Prevent user dismissing with back button - hands free only
    }
}
