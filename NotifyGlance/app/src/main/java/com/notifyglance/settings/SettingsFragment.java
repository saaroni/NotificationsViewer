package com.notifyglance.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.notifyglance.R;
import com.notifyglance.util.Prefs;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private ListPreference timedSession;
    private EditTextPreference overlayLookback;
    private Preference allowedAppsPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        timedSession = findPreference(Prefs.KEY_TIMED_SESSION);
        overlayLookback = findPreference(Prefs.KEY_OVERLAY_LOOKBACK_MIN);
        allowedAppsPreference = findPreference(Prefs.KEY_ALLOWED_APPS);

        if (timedSession != null) timedSession.setOnPreferenceChangeListener(this);
        if (overlayLookback != null) {
            overlayLookback.setOnPreferenceChangeListener(this);
            overlayLookback.setOnBindEditTextListener(et ->
                    et.setInputType(InputType.TYPE_CLASS_NUMBER));
        }

        if (allowedAppsPreference != null) {
            allowedAppsPreference.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(requireContext(), AllowedAppsActivity.class));
                return true;
            });
        }

        Preference diagPref = findPreference("pref_diagnostics_screen");
        if (diagPref != null) {
            diagPref.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(requireContext(), DiagnosticsActivity.class));
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAllowedAppsSummary();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (Prefs.KEY_TIMED_SESSION.equals(key)) {
            String mode = (String) newValue;
            PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .edit().putString(key, mode).apply();
            com.notifyglance.util.AlarmScheduler.schedule(requireContext());
            return true;
        }

        if (Prefs.KEY_OVERLAY_LOOKBACK_MIN.equals(key)) {
            String val = (String) newValue;
            try {
                int minutes = Integer.parseInt(val);
                if (minutes < 15 || minutes > 360) {
                    showToast("Lookback must be between 15 and 360 minutes");
                    return false;
                }
            } catch (NumberFormatException e) {
                showToast("Please enter a valid number");
                return false;
            }
            return true;
        }

        return true;
    }

    private void updateAllowedAppsSummary() {
        if (allowedAppsPreference == null) return;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        Set<String> allowed = sp.getStringSet(Prefs.KEY_ALLOWED_APPS, Collections.emptySet());
        int totalApps = getEligibleInstalledAppCount();

        if (totalApps <= 0) {
            allowedAppsPreference.setSummary(getString(R.string.allowed_apps_summary_default));
            return;
        }

        if (allowed.isEmpty()) {
            allowedAppsPreference.setSummary(getString(R.string.allowed_apps_summary_all_enabled));
            return;
        }

        int enabledCount = Math.min(allowed.size(), totalApps);
        allowedAppsPreference.setSummary(getString(
                R.string.allowed_apps_summary_count,
                enabledCount,
                totalApps
        ));
    }

    private int getEligibleInstalledAppCount() {
        PackageManager pm = requireContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        int count = 0;
        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || pm.getLaunchIntentForPackage(info.packageName) != null) {
                count++;
            }
        }
        return count;
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
