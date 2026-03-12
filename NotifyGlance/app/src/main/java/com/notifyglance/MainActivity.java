package com.notifyglance;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.notifyglance.settings.SettingsActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

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
                .setPositiveButton("Open Settings", (d, w) -> openNotificationAccessScreen())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openOverlaySettings() {
        Log.d(TAG, "Opening overlay permission settings");
        Intent directIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));

        if (tryOpenSettingsIntent(directIntent)) {
            return;
        }

        Intent appDetailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));

        if (tryOpenSettingsIntent(appDetailsIntent)) {
            Toast.makeText(this,
                    "Overlay settings unavailable on this device. Opening app settings instead.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this,
                "Unable to open settings on this device.",
                Toast.LENGTH_LONG).show();
    }

    private void openNotificationAccessScreen() {
        Log.d(TAG, "Opening notification access settings");
        Intent detailIntent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS")
                .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME",
                        new ComponentName(this,
                                com.notifyglance.service.GlanceNotificationListenerService.class)
                                .flattenToString());

        if (tryOpenSettingsIntent(detailIntent)) {
            return;
        }

        Intent listenerIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (tryOpenSettingsIntent(listenerIntent)) {
            return;
        }

        Intent notificationIntent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }

        if (notificationIntent != null
                && tryOpenSettingsIntent(notificationIntent)) {
            Toast.makeText(this,
                    "Notification access screen unavailable. Opening app notifications.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent appDetailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        if (tryOpenSettingsIntent(appDetailsIntent)) {
            Toast.makeText(this,
                    "Notification settings unavailable. Opening app info instead.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this,
                "Unable to open settings on this device.",
                Toast.LENGTH_LONG).show();
    }

    private boolean tryOpenSettingsIntent(Intent intent) {
        String action = intent.getAction();
        if (intent.resolveActivity(getPackageManager()) == null) {
            Log.d(TAG, "Settings intent not resolvable: " + action + ", data=" + intent.getData());
            return false;
        }

        Log.d(TAG, "Launching settings intent: " + action + ", data=" + intent.getData());
        startActivity(intent);
        return true;
    }
}
