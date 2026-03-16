package com.notifyglance.settings;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.notifyglance.R;
import com.notifyglance.util.Prefs;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private ListPreference timedSession;
    private EditTextPreference overlayLookback;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        timedSession    = findPreference(Prefs.KEY_TIMED_SESSION);
        overlayLookback = findPreference(Prefs.KEY_OVERLAY_LOOKBACK_MIN);

        if (timedSession != null) timedSession.setOnPreferenceChangeListener(this);
        if (overlayLookback != null) {
            overlayLookback.setOnPreferenceChangeListener(this);
            overlayLookback.setOnBindEditTextListener(et ->
                    et.setInputType(InputType.TYPE_CLASS_NUMBER));
        }

        populateAppFilter();

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

        if (Prefs.KEY_TIMED_SESSION.equals(key)) {
            String mode = (String) newValue;
            androidx.preference.PreferenceManager
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


    private void populateAppFilter() {
        MultiSelectListPreference appFilter = findPreference(Prefs.KEY_ALLOWED_APPS);
        if (appFilter == null) return;

        PackageManager pm = requireContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        List<String> labels = new ArrayList<>();
        List<String> pkgNames = new ArrayList<>();

        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || pm.getLaunchIntentForPackage(info.packageName) != null) {
                labels.add(pm.getApplicationLabel(info).toString());
                pkgNames.add(info.packageName);
            }
        }

        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            pairs.add(new String[]{labels.get(i), pkgNames.get(i)});
        }
        pairs.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));

        CharSequence[] entries = new CharSequence[pairs.size()];
        CharSequence[] values = new CharSequence[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            entries[i] = pairs.get(i)[0];
            values[i] = pairs.get(i)[1];
        }

        appFilter.setEntries(entries);
        appFilter.setEntryValues(values);
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
