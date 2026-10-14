package com.mujeer.floatingblocker;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The automatically-pinned Kiosk lockdown screen (decisions #2/#12/#18) -
 * forced to the foreground the instant Lock Schedule locks (see
 * KioskForceEntry/BlockEnforcer.applyKioskLockdown), and automatically
 * released the instant it's no longer supposed to be pinned (decision
 * #26 - Lock Schedule unlocking, Master Safety, or the Kiosk Safety
 * toggle), with no manual exit button needed.
 *
 * One Activity, three tabs (decision #18): Apps (the allowed-apps grid,
 * the main surface), Recent, and Notifications - all reading from the
 * same KioskAllowList.computeCurrentAllowList() so they can never
 * disagree about what's actually reachable right now.
 */
public class KioskSessionActivity extends Activity {

    private static final long TICK_INTERVAL_MILLIS = 1000;
    private static final int TAB_APPS = 0;
    private static final int TAB_RECENT = 1;
    private static final int TAB_NOTIFICATIONS = 2;

    private LockScheduleStorage lockScheduleStorage;

    private TextView txtCountdown;
    private LinearLayout blockEndTimesContainer;

    private Button btnTabApps;
    private Button btnTabRecent;
    private Button btnTabNotifications;
    private LinearLayout tabContentApps;
    private View tabContentRecent;
    private LinearLayout tabContentNotifications;

    private Button btnToggleWifi;
    private Button btnFixDefaultDialer;
    private Button btnFixDefaultSms;
    private LinearLayout kioskAppsContainer;

    private LinearLayout recentAppsContainer;

    private Button btnGrantNotificationAccess;
    private LinearLayout notificationsContainer;

    private WifiManager wifiManager;
    private int currentTab = TAB_APPS;

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

        lockScheduleStorage = new LockScheduleStorage(this);

        if (!KioskAllowList.computeCurrentAllowList(this).kioskLayerActive) {
            // Not actually supposed to be pinned right now (e.g. a stale
            // notification tap after Lock Schedule already changed back,
            // or Kiosk Safety got engaged in the meantime) - nothing to show.
            finish();
            return;
        }

        setContentView(R.layout.activity_kiosk_session);
        setTitle(R.string.kiosk_session_title);
        KioskForceEntry.clearNotification(this);

        txtCountdown = (TextView) findViewById(R.id.txtKioskCountdown);
        blockEndTimesContainer = (LinearLayout) findViewById(R.id.blockEndTimesContainer);

        btnTabApps = (Button) findViewById(R.id.btnTabApps);
        btnTabRecent = (Button) findViewById(R.id.btnTabRecent);
        btnTabNotifications = (Button) findViewById(R.id.btnTabNotifications);
        tabContentApps = (LinearLayout) findViewById(R.id.tabContentApps);
        tabContentRecent = findViewById(R.id.tabContentRecent);
        tabContentNotifications = (LinearLayout) findViewById(R.id.tabContentNotifications);

        btnToggleWifi = (Button) findViewById(R.id.btnToggleWifi);
        btnFixDefaultDialer = (Button) findViewById(R.id.btnFixDefaultDialer);
        btnFixDefaultSms = (Button) findViewById(R.id.btnFixDefaultSms);
        kioskAppsContainer = (LinearLayout) findViewById(R.id.kioskAppsContainer);

        recentAppsContainer = (LinearLayout) findViewById(R.id.recentAppsContainer);

        btnGrantNotificationAccess = (Button) findViewById(R.id.btnGrantNotificationAccess);
        notificationsContainer = (LinearLayout) findViewById(R.id.notificationsContainer);

        btnTabApps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectTab(TAB_APPS); }
        });
        btnTabRecent.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectTab(TAB_RECENT); }
        });
        btnTabNotifications.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectTab(TAB_NOTIFICATIONS); }
        });

        setUpWifiToggle();
        setUpDefaultAppFixButtons();
        setUpNotificationAccessButton();

        selectTab(TAB_APPS);

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
        KioskAllowList.Result result = KioskAllowList.computeCurrentAllowList(this);
        if (!result.kioskLayerActive) {
            exitKiosk();
            return;
        }
        updateCountdown();
        refreshWifiToggle();
        if (currentTab == TAB_APPS) {
            renderAppsTab(result);
        } else if (currentTab == TAB_NOTIFICATIONS) {
            renderNotificationsTab(result);
        }
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MILLIS);
    }

    /** Decision #26: fully automatic, symmetric with automatic entry - no manual exit button needed. */
    private void exitKiosk() {
        handler.removeCallbacks(tickRunnable);
        try {
            stopLockTask();
        } catch (Exception e) {
            // Already unpinned, or never successfully pinned - nothing more to do.
        }
        finish();
    }

    private void selectTab(int tab) {
        currentTab = tab;
        tabContentApps.setVisibility(tab == TAB_APPS ? View.VISIBLE : View.GONE);
        tabContentRecent.setVisibility(tab == TAB_RECENT ? View.VISIBLE : View.GONE);
        tabContentNotifications.setVisibility(tab == TAB_NOTIFICATIONS ? View.VISIBLE : View.GONE);

        KioskAllowList.Result result = KioskAllowList.computeCurrentAllowList(this);
        if (tab == TAB_APPS) {
            renderAppsTab(result);
        } else if (tab == TAB_RECENT) {
            renderRecentTab(result);
        } else {
            renderNotificationsTab(result);
        }
    }

    // ---- Timing display (decisions #34/#35) ----

    private void updateCountdown() {
        long nextUnlockMillis = computeNextUnlockMillis();
        if (nextUnlockMillis > 0) {
            txtCountdown.setText(getString(R.string.kiosk_unlocks_in_format, formatRemaining(nextUnlockMillis - System.currentTimeMillis())));
        } else {
            txtCountdown.setText(R.string.kiosk_unlock_time_unknown);
        }

        blockEndTimesContainer.removeAllViews();
        List<Block> activeBlocks = KioskAllowList.computeActiveBlocks(this);
        for (Block b : activeBlocks) {
            long[] occurrence = b.currentOrNextOccurrence(System.currentTimeMillis());
            if (occurrence == null) {
                continue;
            }
            TextView row = new TextView(this);
            row.setTextColor(getResources().getColor(R.color.text_secondary));
            row.setTextSize(13);
            row.setText(getString(R.string.kiosk_block_ends_in_format, b.name, formatRemaining(occurrence[1] - System.currentTimeMillis())));
            blockEndTimesContainer.addView(row);
        }
    }

    /** Best-effort: walks forward a few days to find when Lock Schedule's current locked window ends. */
    private long computeNextUnlockMillis() {
        long now = System.currentTimeMillis();
        for (long t = now; t <= now + (8L * 24 * 60 * 60 * 1000); t += 60L * 1000) {
            if (!isLockedAt(t)) {
                return t;
            }
        }
        return -1;
    }

    private boolean isLockedAt(long millis) {
        // Re-checks against the live schedule at an arbitrary instant -
        // intentionally approximate (1-minute steps) since this only
        // drives a human-readable countdown, not any enforcement.
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        int minutesOfDay = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        if (minutesOfDay >= 120 && minutesOfDay < 130) {
            return false;
        }
        int dayOfWeek = c.get(java.util.Calendar.DAY_OF_WEEK);
        for (TimeRange r : lockScheduleStorage.loadRanges()) {
            if (r.contains(minutesOfDay, dayOfWeek)) {
                return true;
            }
        }
        return false;
    }

    private String formatRemaining(long remainingMillis) {
        if (remainingMillis < 0) {
            remainingMillis = 0;
        }
        long totalSeconds = remainingMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    // ---- Apps tab (decisions #28/#32/#33/#37/#40) ----

    private void renderAppsTab(KioskAllowList.Result result) {
        btnFixDefaultDialer.setVisibility(KioskAllowList.isMissingDefaultDialer(this) ? View.VISIBLE : View.GONE);
        btnFixDefaultSms.setVisibility(KioskAllowList.isMissingDefaultSms(this) ? View.VISIBLE : View.GONE);

        kioskAppsContainer.removeAllViews();
        Set<String> tier1 = KioskAllowList.computeTier1(this);
        List<String> tier1Sorted = new ArrayList<String>(tier1);
        Collections.sort(tier1Sorted, labelComparator());

        List<String> restSorted = new ArrayList<String>(result.allow);
        restSorted.removeAll(tier1);
        Collections.sort(restSorted, labelComparator());

        for (String pkg : tier1Sorted) {
            addAppButton(pkg);
        }
        for (String pkg : restSorted) {
            addAppButton(pkg);
        }
    }

    private Comparator<String> labelComparator() {
        final PackageManager pm = getPackageManager();
        return new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return labelForPackage(pm, a).compareToIgnoreCase(labelForPackage(pm, b));
            }
        };
    }

    private void addAppButton(String pkg) {
        final String packageName = pkg;
        Button button = new Button(this);
        button.setText(getString(R.string.kiosk_open_app_format, labelForPackage(getPackageManager(), pkg)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = kioskAppsContainer.getChildCount() == 0 ? 0 : dpToPx(8);
        button.setLayoutParams(params);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(packageName);
            }
        });
        kioskAppsContainer.addView(button);
    }

    private void launchApp(String pkg) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launchIntent != null) {
            new RecentAppsStorage(this).recordLaunch(pkg);
            startActivity(launchIntent);
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

    private void setUpDefaultAppFixButtons() {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Decision #37 (extended to #33's correction): this is a
                // system-level UI outside our own app, which lock task
                // mode won't let us launch while actually pinned.
                if (lockScheduleStorage.isCurrentlyLocked()) {
                    Toast.makeText(KioskSessionActivity.this, R.string.msg_kiosk_system_ui_needs_unlocked, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(KioskSessionActivity.this, R.string.msg_kiosk_system_ui_needs_unlocked, Toast.LENGTH_LONG).show();
                }
            }
        };
        btnFixDefaultDialer.setOnClickListener(listener);
        btnFixDefaultSms.setOnClickListener(listener);
    }

    private void setUpWifiToggle() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        btnToggleWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleWifiClicked();
            }
        });
        refreshWifiToggle();
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
        refreshWifiToggle();
    }

    private void refreshWifiToggle() {
        if (wifiManager != null && btnToggleWifi != null) {
            btnToggleWifi.setText(wifiManager.isWifiEnabled() ? R.string.wifi_toggle_on : R.string.wifi_toggle_off);
        }
    }

    // ---- Recent tab (decisions #22/#23) ----

    private void renderRecentTab(KioskAllowList.Result result) {
        recentAppsContainer.removeAllViews();
        List<String> recent = new RecentAppsStorage(this).loadRecentPackages();
        for (final String pkg : recent) {
            if (!result.allow.contains(pkg)) {
                continue; // no longer reachable - hidden, not shown greyed-out (decision #23)
            }
            Button button = new Button(this);
            button.setText(getString(R.string.kiosk_open_app_format, labelForPackage(getPackageManager(), pkg)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = recentAppsContainer.getChildCount() == 0 ? 0 : dpToPx(8);
            button.setLayoutParams(params);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchApp(pkg);
                }
            });
            recentAppsContainer.addView(button);
        }
        if (recentAppsContainer.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.kiosk_recent_empty);
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            recentAppsContainer.addView(empty);
        }
    }

    // ---- Notifications tab (decisions #14/#24/#25/#36/#37) ----

    private void setUpNotificationAccessButton() {
        btnGrantNotificationAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lockScheduleStorage.isCurrentlyLocked()) {
                    Toast.makeText(KioskSessionActivity.this, R.string.msg_kiosk_system_ui_needs_unlocked, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(KioskSessionActivity.this, R.string.msg_kiosk_system_ui_needs_unlocked, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void renderNotificationsTab(KioskAllowList.Result result) {
        if (!KioskNotificationListenerService.isConnected()) {
            btnGrantNotificationAccess.setVisibility(View.VISIBLE);
            notificationsContainer.removeAllViews();
            return;
        }
        btnGrantNotificationAccess.setVisibility(View.GONE);

        notificationsContainer.removeAllViews();
        List<KioskNotificationListenerService.Entry> entries = KioskNotificationListenerService.getCurrentEntries(result.allow);
        for (final KioskNotificationListenerService.Entry entry : entries) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_notification, notificationsContainer, false);
            TextView txtTitle = (TextView) row.findViewById(R.id.txtNotificationTitle);
            TextView txtText = (TextView) row.findViewById(R.id.txtNotificationText);
            Button btnOpen = (Button) row.findViewById(R.id.btnOpenNotificationApp);
            Button btnDismiss = (Button) row.findViewById(R.id.btnDismissNotification);

            String label = labelForPackage(getPackageManager(), entry.packageName);
            txtTitle.setText(entry.title != null ? entry.title.toString() : label);
            txtText.setText(entry.text != null ? entry.text.toString() : "");

            btnOpen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Decision #24: tapping opens the source app, same as a real notification would.
                    launchApp(entry.packageName);
                }
            });
            btnDismiss.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Decision #25: local-only, never cancels the real notification.
                    KioskNotificationListenerService.dismissLocally(entry.key);
                    renderNotificationsTab(KioskAllowList.computeCurrentAllowList(KioskSessionActivity.this));
                }
            });

            notificationsContainer.addView(row);
        }
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.kiosk_notifications_empty);
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            notificationsContainer.addView(empty);
        }
    }
}
