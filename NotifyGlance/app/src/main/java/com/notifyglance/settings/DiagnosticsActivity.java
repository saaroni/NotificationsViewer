package com.notifyglance.settings;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.notifyglance.R;
import com.notifyglance.db.AppDatabase;
import com.notifyglance.overlay.OverlayService;
import com.notifyglance.util.AlarmScheduler;
import com.notifyglance.util.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsActivity extends AppCompatActivity {

    private TextView tvServiceStatus;
    private TextView tvLastOverlay;
    private TextView tvNotifCount;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);
        executor = Executors.newSingleThreadExecutor();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvLastOverlay   = findViewById(R.id.tv_last_overlay);
        tvNotifCount    = findViewById(R.id.tv_notif_count);

        Button btnTest = findViewById(R.id.btn_test_overlay);
        btnTest.setOnClickListener(v -> {
            OverlayService.testOverlay(this);
            android.widget.Toast.makeText(this, "Test card triggered!", android.widget.Toast.LENGTH_SHORT).show();
        });

        Button btnShowOverlay = findViewById(R.id.btn_show_overlay);
        btnShowOverlay.setOnClickListener(v -> {
            OverlayService.triggerOverlay(this);
            android.widget.Toast.makeText(this, "Timed session overlay triggered!", android.widget.Toast.LENGTH_SHORT).show();
        });

        Button btnClearDb = findViewById(R.id.btn_clear_db);
        btnClearDb.setOnClickListener(v -> clearDatabase());

        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
    }

    private void loadStats() {
        Prefs prefs = new Prefs(this);
        tvServiceStatus.setText("Master On: " + prefs.isMasterOn()
                + "\nTimed Mode: " + prefs.getTimedSession()
                + "\nQuiet Now: " + prefs.isQuietNow());

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault());

        long last = prefs.getLastOverlayTime();
        String lastOverlayText = last == 0 ? "Never" : dateFormat.format(new Date(last));

        long nextScheduled = AlarmScheduler.getNextScheduledTriggerAtMillis(this);
        String nextOverlayText = nextScheduled <= 0 ? "Not scheduled" : dateFormat.format(new Date(nextScheduled));

        tvLastOverlay.setText("Last overlay: " + lastOverlayText
                + "\nNext scheduled overlay: " + nextOverlayText);

        executor.execute(() -> {
            int total = AppDatabase.getInstance(this).notificationDao().countAll();
            int unpresented = AppDatabase.getInstance(this).notificationDao().countUnpresented();
            long lookbackThreshold = System.currentTimeMillis()
                    - (prefs.getOverlayLookbackMinutes() * 60L * 1000L);
            int eligibleInLookback = AppDatabase.getInstance(this).notificationDao().countAllSince(lookbackThreshold);
            int maxCards = prefs.getMaxCards();
            int perSessionCount = Math.min(eligibleInLookback, maxCards);
            if (!prefs.isMasterOn() || Prefs.TIMED_OFF.equals(prefs.getTimedSession()) || prefs.isQuietNow()) {
                perSessionCount = 0;
            }
            final int shouldShowCount = perSessionCount;
            runOnUiThread(() -> tvNotifCount.setText(
                "Stored notifications: " + total + "\n" +
                "Unread (not yet shown): " + unpresented + "\n" +
                "Should show in timed session now: " + shouldShowCount
            ));
        });
    }

    private void clearDatabase() {
        executor.execute(() -> {
            AppDatabase.getInstance(this).notificationDao().clearAll();
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, "Database cleared", android.widget.Toast.LENGTH_SHORT).show();
                loadStats();
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
