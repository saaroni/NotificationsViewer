package com.notifyglance;

import android.app.Application;
import android.util.Log;

import com.notifyglance.util.AlarmScheduler;
import com.notifyglance.util.NotificationListenerHelper;

public class NotifyGlanceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("NotifyGlanceApp", "Application started");

        NotificationListenerHelper.requestRebindIfPermitted(this);

        // Ensure alarm-based timed sessions are in sync with current prefs on app start
        AlarmScheduler.schedule(this);
    }
}
