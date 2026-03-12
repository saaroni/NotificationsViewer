package com.notifyglance;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.notifyglance.settings.SettingsActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatusNls;
    private TextView tvStatusOverlay;
    private TextView tvMasterStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatusNls     = findViewById(R.id.tv_status_nls);
        tvStatusOverlay = findViewById(R.id.tv_status_overlay);
        tvMasterStatus  = findViewById(R.id.tv_master_status);

        Button btnGrantNls     = findViewById(R.id.btn_grant_nls);
        Button btnGrantOverlay = findViewById(R.id.btn_grant_overlay);
        Button btnSettings     = findViewById(R.id.btn_settings);

        btnGrantNls.setOnClickListener(v -> openNlsSettings());
        btnGrantOverlay.setOnClickListener(v -> openOverlaySettings());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusViews();
    }

    private void updateStatusViews() {
        boolean nlsGranted     = isNotificationListenerGranted();
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean masterOn       = new com.notifyglance.util.Prefs(this).isMasterOn();

        tvStatusNls.setText("Notification Access: " + (nlsGranted ? "✅ Granted" : "❌ Not Granted"));
        tvStatusOverlay.setText("Overlay Permission: " + (overlayGranted ? "✅ Granted" : "❌ Not Granted"));
        tvMasterStatus.setText("Hands-Free Overlay: " + (masterOn ? "🟢 ON" : "🔴 OFF"));
    }

    private boolean isNotificationListenerGranted() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(
                new ComponentName(this,
                        com.notifyglance.service.GlanceNotificationListenerService.class)
                        .flattenToString());
    }

    private void openNlsSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Notification Access")
                .setMessage("NotifyGlance needs notification access to read your notifications. " +
                        "On the next screen, find 'NotifyGlance' and enable it.")
                .setPositiveButton("Open Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
