package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * BETA: sets up a multi-app kiosk session - pick which apps stay usable,
 * pick a duration, start. The actual pinned session runs in
 * KioskSessionActivity. See KioskModeStorage for why this is kept
 * separate from the main Blocks enforcement engine.
 */
public class KioskSetupActivity extends Activity {

    private KioskModeStorage kioskModeStorage;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    private LinearLayout appsContainer;
    private EditText editDurationMinutes;

    private final Set<String> selectedPackages = new LinkedHashSet<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk_setup);
        setTitle(R.string.kiosk_setup_screen_title);

        kioskModeStorage = new KioskModeStorage(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FloatingBlockerDeviceAdminReceiver.class);

        appsContainer = (LinearLayout) findViewById(R.id.appsContainer);
        editDurationMinutes = (EditText) findViewById(R.id.editDurationMinutes);
        Button btnAddApp = (Button) findViewById(R.id.btnAddApp);
        Button btnStartKiosk = (Button) findViewById(R.id.btnStartKiosk);

        selectedPackages.addAll(kioskModeStorage.loadAllowedPackages());

        btnAddApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddAppClicked();
            }
        });
        btnStartKiosk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartKioskClicked();
            }
        });

        renderApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (kioskModeStorage.isSessionActive()) {
            // A session is already running - go straight to it instead of showing setup again.
            startActivity(new Intent(this, KioskSessionActivity.class));
            finish();
        }
    }

    private void onAddAppClicked() {
        List<InstalledApp> apps = getInstalledLaunchableApps();
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
                        selectedPackages.clear();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) selectedPackages.add(packageNames[i]);
                        }
                        renderApps();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        PackageManager pm = getPackageManager();
        for (final String pkg : selectedPackages) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_app, appsContainer, false);
            TextView txtAppName = (TextView) row.findViewById(R.id.txtAppName);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveApp);
            txtAppName.setText(labelForPackage(pm, pkg));
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedPackages.remove(pkg);
                    renderApps();
                }
            });
            appsContainer.addView(row);
        }
    }

    private String labelForPackage(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void onStartKioskClicked() {
        if (devicePolicyManager == null || !devicePolicyManager.isDeviceOwnerApp(getPackageName())) {
            Toast.makeText(this, R.string.msg_kiosk_needs_device_owner, Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.msg_kiosk_select_at_least_one_app, Toast.LENGTH_LONG).show();
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(editDurationMinutes.getText().toString().trim());
        } catch (NumberFormatException e) {
            minutes = 0;
        }
        if (minutes <= 0) {
            Toast.makeText(this, R.string.msg_kiosk_enter_duration, Toast.LENGTH_LONG).show();
            return;
        }

        kioskModeStorage.saveAllowedPackages(selectedPackages);

        // This app's own package must always be included - it's the only
        // way to see the countdown, launch the allowed apps (the home
        // launcher may not be in the allowlist, so it may not be reachable
        // otherwise), or end the session early.
        Set<String> lockTaskPackages = new HashSet<String>(selectedPackages);
        lockTaskPackages.add(getPackageName());
        try {
            devicePolicyManager.setLockTaskPackages(adminComponent, lockTaskPackages.toArray(new String[0]));
            // LOCK_TASK_FEATURE_OVERVIEW is deliberately left out - Android
            // requires LOCK_TASK_FEATURE_HOME to be enabled alongside it
            // (throws IllegalArgumentException otherwise), which in turn
            // needs a designated home activity among the allowed packages -
            // more setup than this beta feature needs just to show Recents.
            devicePolicyManager.setLockTaskFeatures(adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_kiosk_start_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }

        long endMillis = System.currentTimeMillis() + (minutes * 60L * 1000L);
        kioskModeStorage.startSession(endMillis);
        KioskScheduler.scheduleTimeoutFallback(this, endMillis);

        startActivity(new Intent(this, KioskSessionActivity.class));
        finish();
    }

    private static class InstalledApp {
        String label;
        String packageName;
    }

    private List<InstalledApp> getInstalledLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        List<InstalledApp> apps = new ArrayList<InstalledApp>();
        Set<String> seenPackages = new HashSet<String>();
        String ownPackage = getPackageName();
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(ownPackage) || seenPackages.contains(pkg)) {
                continue;
            }
            seenPackages.add(pkg);
            InstalledApp app = new InstalledApp();
            app.packageName = pkg;
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
