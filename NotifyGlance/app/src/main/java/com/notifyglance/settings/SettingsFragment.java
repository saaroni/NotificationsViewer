package com.notifyglance.settings;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.notifyglance.R;
import com.notifyglance.util.Prefs;
import com.notifyglance.worker.TimedSessionWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "SettingsFragment";

    private SwitchPreferenceCompat masterToggle;
    private ListPreference timedSession;
    private EditTextPreference customInterval;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        masterToggle    = findPreference(Prefs.KEY_MASTER_TOGGLE);
        timedSession    = findPreference(Prefs.KEY_TIMED_SESSION);
        customInterval  = findPreference(Prefs.KEY_CUSTOM_INTERVAL);

        // Wire listeners
        if (masterToggle   != null) masterToggle.setOnPreferenceChangeListener(this);
        if (timedSession   != null) timedSession.setOnPreferenceChangeListener(this);
        if (customInterval != null) {
            customInterval.setOnPreferenceChangeListener(this);
            customInterval.setOnBindEditTextListener(et ->
                    et.setInputType(InputType.TYPE_CLASS_NUMBER));
        }

        // Populate app filter
        populateAppFilter();

        // Update custom interval visibility
        updateCustomIntervalVisibility(
                timedSession != null ? timedSession.getValue() : Prefs.TIMED_OFF);

        // Diagnostics shortcut
        Preference diagPref = findPreference("pref_diagnostics_screen");
        if (diagPref != null) {
            diagPref.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(requireContext(), DiagnosticsActivity.class));
                return true;
            });
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (Prefs.KEY_MASTER_TOGGLE.equals(key)) {
            boolean on = (Boolean) newValue;
            // Save first, then reschedule
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .edit().putBoolean(key, on).apply();
            com.notifyglance.util.AlarmScheduler.schedule(requireContext());
            return true; // still let Preference save it again (harmless)
        }

        if (Prefs.KEY_TIMED_SESSION.equals(key)) {
            String mode = (String) newValue;
            updateCustomIntervalVisibility(mode);
            // Save first, then reschedule
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .edit().putString(key, mode).apply();
            com.notifyglance.util.AlarmScheduler.schedule(requireContext());
            return true;
        }

        if (Prefs.KEY_CUSTOM_INTERVAL.equals(key)) {
            String val = (String) newValue;
            try {
                int minutes = Integer.parseInt(val);
                if (minutes < 1 || minutes > 60) {
                    showToast("Interval must be between 1 and 60 minutes");
                    return false;
                }
            } catch (NumberFormatException e) {
                showToast("Please enter a valid number");
                return false;
            }
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .edit().putString(key, val).apply();
            com.notifyglance.util.AlarmScheduler.schedule(requireContext());
            return true;
        }

        return true;
    }

    private void updateCustomIntervalVisibility(String mode) {
        if (customInterval != null) {
            customInterval.setVisible(Prefs.TIMED_CUSTOM.equals(mode));
        }
    }

    private void populateAppFilter() {
        MultiSelectListPreference appFilter = findPreference(Prefs.KEY_ALLOWED_APPS);
        if (appFilter == null) return;

        PackageManager pm = requireContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        List<String> labels  = new ArrayList<>();
        List<String> pkgNames = new ArrayList<>();

        for (ApplicationInfo info : apps) {
            // Only include apps that can post notifications (have launcher or are user apps)
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || pm.getLaunchIntentForPackage(info.packageName) != null) {
                labels.add(pm.getApplicationLabel(info).toString());
                pkgNames.add(info.packageName);
            }
        }

        // Sort by label
        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            pairs.add(new String[]{labels.get(i), pkgNames.get(i)});
        }
        pairs.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));

        CharSequence[] entries = new CharSequence[pairs.size()];
        CharSequence[] values  = new CharSequence[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            entries[i] = pairs.get(i)[0];
            values[i]  = pairs.get(i)[1];
        }

        appFilter.setEntries(entries);
        appFilter.setEntryValues(values);
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
