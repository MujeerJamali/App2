package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Always Allowed Apps" screen (Tier-2 of KioskAllowList) - decisions
 * #4/#6/#7/#15/#39. Shows all installed apps in the picker (no
 * filtering), gates ADDING to unlocked Lock Schedule time only (adding
 * weakens restriction), never gates removing, and also lists Tier-1
 * (hardcoded, always-allowed) apps for transparency - shown but not
 * removable, since they're not actually editable here at all.
 */
public class AlwaysAllowedAppsListActivity extends Activity {

    private AlwaysAllowedAppsStorage storage;
    private LockScheduleStorage lockScheduleStorage;

    private TextView txtLockedMessage;
    private Button btnAddApp;
    private LinearLayout appsContainer;
    private LinearLayout tier1Container;

    private final Set<String> selectedPackages = new HashSet<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_always_allowed_apps_list);
        setTitle(R.string.always_allowed_apps_screen_title);

        storage = new AlwaysAllowedAppsStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        btnAddApp = (Button) findViewById(R.id.btnAddApp);
        appsContainer = (LinearLayout) findViewById(R.id.appsContainer);
        tier1Container = (LinearLayout) findViewById(R.id.tier1Container);

        btnAddApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddAppClicked();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean editingAllowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(editingAllowed ? View.GONE : View.VISIBLE);
        selectedPackages.clear();
        selectedPackages.addAll(storage.loadApps());
        renderApps();
        renderTier1();
    }

    private void onAddAppClicked() {
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_always_allowed_apps_add_locked, Toast.LENGTH_LONG).show();
            return;
        }
        List<InstalledApp> apps = getAllInstalledApps();
        final String[] labels = new String[apps.size()];
        final String[] packageNames = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            packageNames[i] = apps.get(i).packageName;
            checked[i] = selectedPackages.contains(packageNames[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_app_button)
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) {
                                selectedPackages.add(packageNames[i]);
                            }
                        }
                        storage.saveApps(selectedPackages);
                        renderApps();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        final boolean editingAllowed = lockScheduleStorage.isEditingAllowed();
        PackageManager pm = getPackageManager();
        List<String> sorted = new ArrayList<String>(selectedPackages);
        Collections.sort(sorted, labelComparator(pm));
        for (final String pkg : sorted) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_app, appsContainer, false);
            TextView txtAppName = (TextView) row.findViewById(R.id.txtAppName);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveApp);
            txtAppName.setText(labelForPackage(pm, pkg));
            // Removing only ever strengthens restriction, so it's never gated (decision #6).
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedPackages.remove(pkg);
                    storage.saveApps(selectedPackages);
                    renderApps();
                }
            });
            appsContainer.addView(row);
        }
    }

    /** Tier-1 (decision #5) - listed here too for transparency (decision #7), but not removable. */
    private void renderTier1() {
        tier1Container.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> tier1 = KioskAllowList.computeTier1(this);
        List<String> sorted = new ArrayList<String>(tier1);
        Collections.sort(sorted, labelComparator(pm));
        for (String pkg : sorted) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_app, tier1Container, false);
            TextView txtAppName = (TextView) row.findViewById(R.id.txtAppName);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveApp);
            txtAppName.setText(labelForPackage(pm, pkg));
            btnRemove.setVisibility(View.GONE);
            tier1Container.addView(row);
        }
    }

    private Comparator<String> labelComparator(final PackageManager pm) {
        return new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return labelForPackage(pm, a).compareToIgnoreCase(labelForPackage(pm, b));
            }
        };
    }

    private String labelForPackage(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static class InstalledApp {
        String label;
        String packageName;
    }

    /** Decision #15: shows ALL installed apps, no filtering/restriction. */
    private List<InstalledApp> getAllInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        String ownPackage = getPackageName();

        List<InstalledApp> apps = new ArrayList<InstalledApp>();
        for (ApplicationInfo info : installed) {
            if (info.packageName.equals(ownPackage)) {
                continue;
            }
            InstalledApp app = new InstalledApp();
            app.packageName = info.packageName;
            app.label = info.loadLabel(pm).toString();
            apps.add(app);
        }
        Collections.sort(apps, new Comparator<InstalledApp>() {
            @Override
            public int compare(InstalledApp a, InstalledApp b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }
}
