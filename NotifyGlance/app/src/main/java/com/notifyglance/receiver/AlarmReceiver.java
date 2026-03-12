package com.notifyglance.receiver;

import android.app.KeyguardManager;
import android.os.PowerManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.notifyglance.overlay.LockScreenActivity;
import com.notifyglance.util.AlarmScheduler;
import com.notifyglance.util.Prefs;
import com.notifyglance.util.WakeUtil;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "=== ALARM FIRED ===");

        // Check screen state
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm.isInteractive();
        Log.d(TAG, "Screen is: " + (screenOn ? "ON" : "OFF"));

        // Check keyguard state
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km.isKeyguardLocked();
        Log.d(TAG, "Keyguard locked: " + locked);

        Prefs prefs = new Prefs(context);
        Log.d(TAG, "Master on: " + prefs.isMasterOn());
        Log.d(TAG, "Quiet now: " + prefs.isQuietNow());

        if (!prefs.isMasterOn()) return;
        if (prefs.isQuietNow())  return;

        // Acquire wake lock
        WakeUtil.acquireTemporary(context);
        Log.d(TAG, "WakeLock acquired");

        // Launch activity
        Intent activityIntent = new Intent(context, LockScreenActivity.class);
        activityIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_NO_ANIMATION
        );
        try {
            context.startActivity(activityIntent);
            Log.d(TAG, "LockScreenActivity launched successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch LockScreenActivity: " + e.getMessage());
        }

        AlarmScheduler.schedule(context);
    }
}