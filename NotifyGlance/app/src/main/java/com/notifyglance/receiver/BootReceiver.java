package com.notifyglance.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.notifyglance.util.AlarmScheduler;
import com.notifyglance.util.NotificationListenerHelper;
import com.notifyglance.util.Prefs;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BootReceiver", "Boot received, rescheduling alarm");
        Prefs prefs = new Prefs(context);
        NotificationListenerHelper.requestRebindIfPermitted(context);
        if (prefs.isMasterOn()) {
            AlarmScheduler.schedule(context);
        }
    }
}