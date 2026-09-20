package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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

    private void renderApps() {
        appsContainer.removeAllViews();
        PackageManager pm = getPackageManager();
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
