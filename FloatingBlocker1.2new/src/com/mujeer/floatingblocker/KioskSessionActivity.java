package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * BETA: the pinned, active kiosk session screen - shows a countdown, lets
 * the user launch any of the allowed apps, and ends the session either by
 * hitting the time limit or by the user tapping End Session. See
 * KioskModeStorage for why this is kept separate from the main Blocks
 * enforcement engine.
 */
public class KioskSessionActivity extends Activity {

    private static final long TICK_INTERVAL_MILLIS = 1000;

    private KioskModeStorage kioskModeStorage;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    private TextView txtCountdown;
    private LinearLayout appsContainer;
    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    private Button btnToggleWifi;
    private Button btnToggleBluetooth;

    private final Handler handler = new Handler();
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk_session);
        setTitle(R.string.kiosk_session_title);

        kioskModeStorage = new KioskModeStorage(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FloatingBlockerDeviceAdminReceiver.class);

        if (!kioskModeStorage.isSessionActive()) {
            finish();
            return;
        }

        txtCountdown = (TextView) findViewById(R.id.txtKioskCountdown);
        appsContainer = (LinearLayout) findViewById(R.id.kioskAppsContainer);
        Button btnEndKioskSession = (Button) findViewById(R.id.btnEndKioskSession);
        btnEndKioskSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endSession();
            }
        });

        setUpQuickToggles();
        renderApps();

        try {
            startLockTask();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_kiosk_start_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickRunnable);
    }

    private void tick() {
        if (!kioskModeStorage.isSessionActive()) {
            finish();
            return;
        }
        long remainingMillis = kioskModeStorage.getSessionEndMillis() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            endSession();
            return;
        }
        txtCountdown.setText(getString(R.string.kiosk_time_remaining_format, formatRemaining(remainingMillis)));
        refreshQuickToggles();
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MILLIS);
    }

    private String formatRemaining(long remainingMillis) {
        long totalSeconds = remainingMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * WiFi/Bluetooth toggles right inside this screen, so a session's
     * "allowed apps only" restriction never has to be loosened to
     * LOCK_TASK_FEATURE_NOTIFICATIONS just to reach a Quick Settings tile -
     * that would open the whole notification shade (including whatever
     * OEM extras are bundled into it, e.g. a battery-saver mode that could
     * itself interfere with this app) with no way to show only some tiles.
     * These call WifiManager/BluetoothAdapter directly instead, using this
     * app's Device Owner privileges - no system panel is ever shown.
     */
    private void setUpQuickToggles() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        btnToggleWifi = (Button) findViewById(R.id.btnToggleWifi);
        btnToggleBluetooth = (Button) findViewById(R.id.btnToggleBluetooth);

        // Silently grants this app itself permission to control Bluetooth
        // (required since API 31) - a runtime permission prompt wouldn't
        // be reliably reachable from inside a pinned lock task screen
        // anyway. Same silent-grant pattern already used for Location
        // Override. Best effort - if it fails, the toggle button below
        // will just report failure when tapped rather than crash.
        // Compiled against API 29 (this project's compileSdkVersion), so
        // Build.VERSION_CODES.S (31) and Manifest.permission.BLUETOOTH_CONNECT
        // don't exist in this build's android.jar even though they exist at
        // runtime on a device running API 31+ - literal values used instead.
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                devicePolicyManager.setPermissionGrantState(adminComponent, getPackageName(),
                        "android.permission.BLUETOOTH_CONNECT",
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (Exception e) {
                // Best effort.
            }
        }

        btnToggleWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleWifiClicked();
            }
        });
        btnToggleBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleBluetoothClicked();
            }
        });

        refreshQuickToggles();
    }

    private void onToggleWifiClicked() {
        boolean accepted = false;
        try {
            if (wifiManager != null) {
                accepted = wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
            }
        } catch (Exception e) {
            // Falls through to the failure toast below.
        }
        if (!accepted) {
            Toast.makeText(this, R.string.msg_wifi_toggle_failed, Toast.LENGTH_LONG).show();
        }
        refreshQuickToggles();
    }

    private void onToggleBluetoothClicked() {
        boolean accepted = false;
        try {
            if (bluetoothAdapter != null) {
                accepted = bluetoothAdapter.isEnabled() ? bluetoothAdapter.disable() : bluetoothAdapter.enable();
            }
        } catch (Exception e) {
            // Falls through to the failure toast below.
        }
        if (!accepted) {
            Toast.makeText(this, R.string.msg_bluetooth_toggle_failed, Toast.LENGTH_LONG).show();
        }
        // Both calls are asynchronous - the state read back here may not
        // have changed yet, but the next tick (within a second) will show
        // it correctly once it actually has.
        refreshQuickToggles();
    }

    private void refreshQuickToggles() {
        if (wifiManager != null && btnToggleWifi != null) {
            btnToggleWifi.setText(wifiManager.isWifiEnabled() ? R.string.wifi_toggle_on : R.string.wifi_toggle_off);
        }
        if (bluetoothAdapter != null && btnToggleBluetooth != null) {
            btnToggleBluetooth.setText(bluetoothAdapter.isEnabled() ? R.string.bluetooth_toggle_on : R.string.bluetooth_toggle_off);
        }
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        final PackageManager pm = getPackageManager();
        List<String> packages = new java.util.ArrayList<String>(kioskModeStorage.loadAllowedPackages());
        java.util.Collections.sort(packages);
        for (final String pkg : packages) {
            Button button = new Button(this);
            button.setText(getString(R.string.kiosk_open_app_format, labelForPackage(pm, pkg)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = appsContainer.getChildCount() == 0 ? 0 : dpToPx(10);
            button.setLayoutParams(params);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                }
            });
            appsContainer.addView(button);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String labelForPackage(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void endSession() {
        handler.removeCallbacks(tickRunnable);
        kioskModeStorage.endSession();
        KioskScheduler.cancelTimeoutFallback(this);
        try {
            stopLockTask();
        } catch (Exception e) {
            // Already unpinned, or never successfully pinned - nothing more to do.
        }
        try {
            devicePolicyManager.setLockTaskPackages(adminComponent, new String[0]);
        } catch (Exception e) {
            // Non-fatal - lock task packages will just be reset next session.
        }
        finish();
    }
}
