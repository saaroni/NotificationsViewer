package com.notifyglance.settings;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.widget.SwitchCompat;
import com.notifyglance.R;
import com.notifyglance.util.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AllowedAppsActivity extends AppCompatActivity {

    private final List<AppToggleItem> allItems = new ArrayList<>();
    private final List<AppToggleItem> filteredItems = new ArrayList<>();
    private AllowedAppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allowed_apps);

        EditText searchInput = findViewById(R.id.search_input);
        RecyclerView recyclerView = findViewById(R.id.apps_recycler);
        View saveButton = findViewById(R.id.button_save);
        View cancelButton = findViewById(R.id.button_cancel);
        View enableAllButton = findViewById(R.id.button_enable_all);
        View disableAllButton = findViewById(R.id.button_disable_all);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AllowedAppsAdapter(filteredItems);
        recyclerView.setAdapter(adapter);

        loadApps();
        applySearch("");

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        saveButton.setOnClickListener(v -> {
            persistAllowedApps();
            finish();
        });

        cancelButton.setOnClickListener(v -> finish());
        enableAllButton.setOnClickListener(v -> setAllEnabled(true));
        disableAllButton.setOnClickListener(v -> setAllEnabled(false));
    }

    private void loadApps() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> storedAllowedApps = sp.getStringSet(Prefs.KEY_ALLOWED_APPS, Collections.emptySet());

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        List<AppToggleItem> loaded = new ArrayList<>();
        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || pm.getLaunchIntentForPackage(info.packageName) != null) {
                String name = pm.getApplicationLabel(info).toString();
                loaded.add(new AppToggleItem(name, info.packageName));
            }
        }

        loaded.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

        boolean defaultAllOn = storedAllowedApps.isEmpty();
        for (AppToggleItem item : loaded) {
            item.enabled = defaultAllOn || storedAllowedApps.contains(item.packageName);
        }

        allItems.clear();
        allItems.addAll(loaded);
    }

    private void applySearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.US);
        filteredItems.clear();

        if (normalized.isEmpty()) {
            filteredItems.addAll(allItems);
        } else {
            for (AppToggleItem item : allItems) {
                if (item.label.toLowerCase(Locale.US).contains(normalized)) {
                    filteredItems.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }


    private void setAllEnabled(boolean enabled) {
        for (AppToggleItem item : allItems) {
            item.enabled = enabled;
        }
        adapter.notifyDataSetChanged();
    }

    private void persistAllowedApps() {
        Set<String> allowed = new HashSet<>();
        for (AppToggleItem item : allItems) {
            if (item.enabled) {
                allowed.add(item.packageName);
            }
        }

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putStringSet(Prefs.KEY_ALLOWED_APPS, allowed)
                .apply();
    }

    private static class AppToggleItem {
        final String label;
        final String packageName;
        boolean enabled;

        AppToggleItem(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private static class AllowedAppsAdapter extends RecyclerView.Adapter<AllowedAppsAdapter.ViewHolder> {

        private final List<AppToggleItem> items;

        AllowedAppsAdapter(List<AppToggleItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_allowed_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppToggleItem item = items.get(position);
            holder.appName.setText(item.label);
            holder.packageName.setText(item.packageName);

            holder.appToggle.setOnCheckedChangeListener(null);
            holder.appToggle.setChecked(item.enabled);
            holder.appToggle.setOnCheckedChangeListener((buttonView, isChecked) -> item.enabled = isChecked);

            holder.itemView.setOnClickListener(v -> {
                boolean next = !item.enabled;
                item.enabled = next;
                holder.appToggle.setChecked(next);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView appName;
            final TextView packageName;
            final SwitchCompat appToggle;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                appName = itemView.findViewById(R.id.app_name);
                packageName = itemView.findViewById(R.id.app_package);
                appToggle = itemView.findViewById(R.id.app_toggle);
            }
        }
    }
}
