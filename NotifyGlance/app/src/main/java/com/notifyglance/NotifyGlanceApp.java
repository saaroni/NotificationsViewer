package com.notifyglance;

import android.app.Application;
import android.util.Log;

import com.notifyglance.util.Prefs;
import com.notifyglance.worker.TimedSessionWorker;

public class NotifyGlanceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("NotifyGlanceApp", "Application started");

        // Reschedule timed workers on app start
        Prefs prefs = new Prefs(this);
        if (prefs.isMasterOn()) {
            TimedSessionWorker.reschedule(this);
        }
    }
}
