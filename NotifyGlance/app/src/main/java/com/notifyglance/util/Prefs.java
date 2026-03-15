package com.notifyglance.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.Set;

public class Prefs {

    // Keys (must match preference XML)
    public static final String KEY_MASTER_TOGGLE          = "pref_master_toggle";
    public static final String KEY_TRIGGER_ON_NEW         = "pref_trigger_on_new";
    public static final String KEY_TIMED_SESSION          = "pref_timed_session";   // off / hourly / custom
    public static final String KEY_CUSTOM_INTERVAL        = "pref_custom_interval"; // minutes 5-60
    public static final String KEY_MAX_CARDS              = "pref_max_cards";       // 5/10/50/unlimited
    public static final String KEY_CARD_DISPLAY_SECONDS   = "pref_card_display_sec";// 3/4.5/6/7
    public static final String KEY_QUIET_START            = "pref_quiet_start";     // "HH:mm"
    public static final String KEY_QUIET_END              = "pref_quiet_end";       // "HH:mm"
    public static final String KEY_ALLOWED_APPS           = "pref_allowed_apps";    // Set<String>
    public static final String KEY_SUPPRESS_ONGOING       = "pref_suppress_ongoing";
    public static final String KEY_FONT_SIZE              = "pref_font_size";       // large/xl/xxl
    public static final String KEY_HIGH_CONTRAST          = "pref_high_contrast";
    public static final String KEY_LAST_OVERLAY_TIME      = "pref_last_overlay_time";
    public static final String KEY_CAPTURE_WHILE_OFF      = "pref_capture_while_off";
    public static final String KEY_OVERLAY_LOOKBACK_MIN   = "pref_overlay_lookback_minutes";

    public static final String TIMED_OFF     = "off";
    public static final String TIMED_HOURLY  = "hourly";
    public static final String TIMED_CUSTOM  = "custom";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public boolean isMasterOn()           { return sp.getBoolean(KEY_MASTER_TOGGLE, false); }
    public boolean isTriggerOnNew()       { return sp.getBoolean(KEY_TRIGGER_ON_NEW, true); }
    public String  getTimedSession()      { return sp.getString(KEY_TIMED_SESSION, TIMED_OFF); }
    public int     getCustomInterval()    { return parseInt(sp.getString(KEY_CUSTOM_INTERVAL, "15"), 15); }
    public int     getMaxCards()          {
        int value = parseInt(sp.getString(KEY_MAX_CARDS, "5"), 5);
        return value <= 0 ? Integer.MAX_VALUE : value;
    }
    public float   getCardDisplaySec()    { return parseFloat(sp.getString(KEY_CARD_DISPLAY_SECONDS, "5"), 5f); }
    public String  getQuietStart()        { return sp.getString(KEY_QUIET_START, ""); }
    public String  getQuietEnd()          { return sp.getString(KEY_QUIET_END, ""); }
    public Set<String> getAllowedApps()   { return sp.getStringSet(KEY_ALLOWED_APPS, Collections.emptySet()); }
    public boolean isSuppressOngoing()    { return sp.getBoolean(KEY_SUPPRESS_ONGOING, true); }
    public String  getFontSize()          { return sp.getString(KEY_FONT_SIZE, "xl"); }
    public boolean isHighContrast()       { return sp.getBoolean(KEY_HIGH_CONTRAST, false); }
    public long    getLastOverlayTime()   { return sp.getLong(KEY_LAST_OVERLAY_TIME, 0); }
    public boolean isCaptureWhileOff()    { return sp.getBoolean(KEY_CAPTURE_WHILE_OFF, true); }
    public int getOverlayLookbackMinutes() {
        int mins = parseInt(sp.getString(KEY_OVERLAY_LOOKBACK_MIN, "60"), 60);
        if (mins < 15) return 15;
        if (mins > 360) return 360;
        return mins;
    }

    public void setLastOverlayTime(long ts) {
        sp.edit().putLong(KEY_LAST_OVERLAY_TIME, ts).apply();
    }

    /** Returns true if current time falls in quiet hours */
    public boolean isQuietNow() {
        String start = getQuietStart();
        String end   = getQuietEnd();
        if (start.isEmpty() || end.isEmpty()) return false;
        int[] s = parseTime(start);
        int[] e = parseTime(end);
        if (s == null || e == null) return false;

        java.util.Calendar now = java.util.Calendar.getInstance();
        int nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int startMin = s[0] * 60 + s[1];
        int endMin   = e[0] * 60 + e[1];

        if (startMin <= endMin) {
            return nowMin >= startMin && nowMin < endMin;
        } else {
            // spans midnight
            return nowMin >= startMin || nowMin < endMin;
        }
    }

    private int[] parseTime(String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception ex) {
            return null;
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
}
