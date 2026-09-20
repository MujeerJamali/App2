package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_EXPORT_BACKUP = 801;
    private static final int REQUEST_IMPORT_BACKUP = 802;

    private LockScheduleStorage lockScheduleStorage;
    private BlocksPauseStorage blocksPauseStorage;
    private MasterSafetyStorage masterSafetyStorage;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    private TextView txtDeviceOwnerStatus;
    private Button btnBlocksPause;
    private Button btnMasterSafety;
    private Button btnDeleteSafetyForever;
    private Button btnBatteryExemption;
    private Button btnScanRingingAlarm;

    private final Handler ringingAlarmHandler = new Handler();
    private final Runnable ringingAlarmTick = new Runnable() {
        @Override
        public void run() {
            refreshRingingAlarmButton();
            ringingAlarmHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Checked first, before anything else in onCreate() that could
        // itself throw - if a bug crashes onCreate() on every launch,
        // onResume() (where this was previously shown) never gets a
        // chance to run, so the crash dialog could never surface at all.
        // Checking here means even a crash-on-every-launch situation is
        // still self-diagnosing on the very next attempt.
        showCrashReportIfAny();

        setContentView(R.layout.activity_main);

        lockScheduleStorage = new LockScheduleStorage(this);
        blocksPauseStorage = new BlocksPauseStorage(this);
        masterSafetyStorage = new MasterSafetyStorage(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FloatingBlockerDeviceAdminReceiver.class);

        txtDeviceOwnerStatus = (TextView) findViewById(R.id.txtDeviceOwnerStatus);
        btnBlocksPause = (Button) findViewById(R.id.btnBlocksPause);
        btnMasterSafety = (Button) findViewById(R.id.btnMasterSafety);
        btnDeleteSafetyForever = (Button) findViewById(R.id.btnDeleteSafetyForever);
        btnBatteryExemption = (Button) findViewById(R.id.btnBatteryExemption);
        btnScanRingingAlarm = (Button) findViewById(R.id.btnScanRingingAlarm);
        btnDeleteSafetyForever.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_danger)));
        btnScanRingingAlarm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_danger)));

        Button btnBlocks = (Button) findViewById(R.id.btnBlocks);
        Button btnLockSchedule = (Button) findViewById(R.id.btnLockSchedule);
        Button btnHolidayBreaks = (Button) findViewById(R.id.btnHolidayBreaks);
        Button btnWebsites = (Button) findViewById(R.id.btnWebsites);
        Button btnAlarms = (Button) findViewById(R.id.btnAlarms);
        Button btnBarcodes = (Button) findViewById(R.id.btnBarcodes);
        Button btnHomeLocation = (Button) findViewById(R.id.btnHomeLocation);
        Button btnExportBackup = (Button) findViewById(R.id.btnExportBackup);
        Button btnImportBackup = (Button) findViewById(R.id.btnImportBackup);
        Button btnKioskMode = (Button) findViewById(R.id.btnKioskMode);

        btnBatteryExemption.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onBatteryExemptionClicked();
            }
        });

        btnBlocksPause.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onBlocksPauseClicked();
            }
        });

        btnMasterSafety.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onMasterSafetyClicked();
            }
        });

        btnDeleteSafetyForever.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onDeleteSafetyForeverClicked();
            }
        });

        btnScanRingingAlarm.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onScanRingingAlarmClicked();
            }
        });

        btnBlocks.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, BlocksListActivity.class));
            }
        });

        btnLockSchedule.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, LockScheduleActivity.class));
            }
        });

        btnHolidayBreaks.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, HolidayBreaksListActivity.class));
            }
        });

        btnWebsites.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, WebsitesListActivity.class));
            }
        });

        btnAlarms.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, AlarmsListActivity.class));
            }
        });

        btnBarcodes.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, BarcodesListActivity.class));
            }
        });

        btnHomeLocation.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, HomeLocationActivity.class));
            }
        });

        btnExportBackup.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onExportBackupClicked();
            }
        });

        btnKioskMode.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, KioskSetupActivity.class));
            }
        });

        btnImportBackup.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onImportBackupClicked();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cheap and idempotent - makes sure anything changed in Blocks/Lock
        // Schedule screens, or elsewhere, is reflected immediately.
        BlockEnforcer.reapplyAndReschedule(this);
        AlarmScheduler.rescheduleAll(this);
        refreshUi();
        // Ticks the ringing-alarm button's countdown once a second while
        // this screen is visible - also means the button appears/updates/
        // disappears live if an Alarm starts or resolves while already
        // sitting on this screen, not just on the next resume.
        ringingAlarmHandler.postDelayed(ringingAlarmTick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ringingAlarmHandler.removeCallbacks(ringingAlarmTick);
    }

    /**
     * Shows the last uncaught crash's stack trace, if there's one not yet
     * seen - this app deliberately avoids relying on logcat (not
     * practically accessible on a non-rooted device), so this is the only
     * practical way to see what an actual crash was.
     */
    private void showCrashReportIfAny() {
        final CrashLogStorage crashLog = new CrashLogStorage(this);
        if (!crashLog.hasUnseenCrash()) {
            return;
        }
        TextView traceView = new TextView(this);
        traceView.setText(crashLog.getTrace());
        traceView.setTextIsSelectable(true);
        traceView.setPadding(32, 16, 32, 16);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(traceView);
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_report_title)
                .setView(scroll)
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        crashLog.markSeen();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private boolean isDeviceOwnerActive() {
        return devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName());
    }

    private void refreshUi() {
        boolean deviceOwnerActive = isDeviceOwnerActive();
        txtDeviceOwnerStatus.setText(deviceOwnerActive
                ? getString(R.string.device_owner_active)
                : getString(R.string.device_owner_not_active));
        txtDeviceOwnerStatus.setTextColor(deviceOwnerActive
                ? getColor(R.color.color_success)
                : getColor(R.color.color_danger));

        boolean paused = blocksPauseStorage.isPaused();
        btnBlocksPause.setText(paused ? R.string.blocks_paused : R.string.blocks_active);
        // Tint rather than replace the background outright, so the button
        // keeps its rounded shape instead of reverting to a flat rectangle.
        btnBlocksPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                paused ? getColor(R.color.color_danger) : getColor(R.color.color_success)));
        btnBlocksPause.setTextColor(Color.WHITE);

        boolean safetyEngaged = masterSafetyStorage.isEngaged();
        boolean safetyDeleted = masterSafetyStorage.isDeletedForever();

        if (safetyDeleted) {
            btnMasterSafety.setText(R.string.safety_deleted_button);
            btnMasterSafety.setEnabled(false);
            btnMasterSafety.setBackgroundTintList(null);
            btnDeleteSafetyForever.setEnabled(false);
        } else {
            btnMasterSafety.setText(safetyEngaged ? R.string.safety_disengage_button : R.string.safety_engage_button);
            btnMasterSafety.setEnabled(true);
            btnMasterSafety.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    safetyEngaged ? getColor(R.color.color_danger) : getColor(R.color.primary)));
            btnDeleteSafetyForever.setEnabled(true);
        }

        boolean batteryExempt = isBatteryExempt();
        btnBatteryExemption.setText(batteryExempt ? R.string.battery_exempt_on : R.string.battery_exempt_off);
        btnBatteryExemption.setEnabled(!batteryExempt);

        refreshRingingAlarmButton();
    }

    /**
     * Shows a prominent button whenever any Alarm currently has an
     * unresolved ringing occurrence (per AlarmRuntimeStorage's persisted
     * state, not whether AlarmRingService happens to be alive) - a manual
     * way to reach the scan-to-dismiss screen for whenever the ring
     * service/notification/full-screen alarm somehow fails to show on its
     * own, as long as the user is otherwise aware an Alarm should be
     * ringing. Hidden the rest of the time.
     */
    private void refreshRingingAlarmButton() {
        List<Alarm> ringingAlarms = findRingingAlarms();
        if (ringingAlarms.isEmpty()) {
            btnScanRingingAlarm.setVisibility(android.view.View.GONE);
            return;
        }
        btnScanRingingAlarm.setVisibility(android.view.View.VISIBLE);

        // The button doesn't grant any extra time of its own - it's just
        // another way to reach the same scan screen within the exact same
        // RING_MINUTES window the normal ring flow already uses, so the
        // countdown shown here is that same deadline, not a separate one.
        AlarmRuntimeStorage runtime = new AlarmRuntimeStorage(this);
        long soonestDeadlineMillis = Long.MAX_VALUE;
        for (Alarm a : ringingAlarms) {
            long deadline = runtime.getRingingOccurrence(a.id) + (Alarm.RING_MINUTES * 60L * 1000L);
            if (deadline < soonestDeadlineMillis) {
                soonestDeadlineMillis = deadline;
            }
        }
        long remainingMillis = Math.max(soonestDeadlineMillis - System.currentTimeMillis(), 0);
        String remainingText = formatRemaining(remainingMillis);

        if (ringingAlarms.size() == 1) {
            btnScanRingingAlarm.setText(getString(R.string.ringing_alarm_button_one_format, ringingAlarms.get(0).name, remainingText));
        } else {
            btnScanRingingAlarm.setText(getString(R.string.ringing_alarm_button_many_format, ringingAlarms.size(), remainingText));
        }
    }

    private String formatRemaining(long remainingMillis) {
        long totalSeconds = remainingMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private List<Alarm> findRingingAlarms() {
        List<Alarm> result = new ArrayList<Alarm>();
        AlarmRuntimeStorage runtime = new AlarmRuntimeStorage(this);
        for (Alarm a : new AlarmsStorage(this).loadAlarms()) {
            if (runtime.getRingingOccurrence(a.id) != 0) {
                result.add(a);
            }
        }
        return result;
    }

    private void onScanRingingAlarmClicked() {
        final List<Alarm> ringingAlarms = findRingingAlarms();
        if (ringingAlarms.isEmpty()) {
            // Resolved by some other path (e.g. the deadline just fired) between showing the button and tapping it.
            refreshUi();
            return;
        }
        if (ringingAlarms.size() == 1) {
            launchAlarmDismissScreen(ringingAlarms.get(0));
            return;
        }
        String[] labels = new String[ringingAlarms.size()];
        for (int i = 0; i < ringingAlarms.size(); i++) {
            labels[i] = ringingAlarms.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_ringing_alarm_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        launchAlarmDismissScreen(ringingAlarms.get(which));
                    }
                })
                .show();
    }

    private void launchAlarmDismissScreen(Alarm alarm) {
        long occurrenceMillis = new AlarmRuntimeStorage(this).getRingingOccurrence(alarm.id);
        Intent intent = new Intent(this, AlarmRingActivity.class);
        intent.putExtra(AlarmRingReceiver.EXTRA_ALARM_ID, alarm.id);
        intent.putExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceMillis);
        startActivity(intent);
    }

    private boolean isBatteryExempt() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    // The DNS Lock / strict Private DNS feature (forcing system-wide Private
    // DNS to family-filter-dns.cleanbrowsing.org and locking the user out of
    // Settings via DISALLOW_CONFIG_PRIVATE_DNS) was removed from here - it
    // was this app's first attempt at content filtering, before
    // DnsVpnService existed. It's now strictly redundant: DnsVpnService
    // already forwards every non-custom-blocked query to that exact same
    // CleanBrowsing family filter resolver (see UPSTREAM_DNS in
    // DnsVpnService), so anything the strict Private DNS lock filtered, the
    // VPN filters too - plus your own Blocked Websites list on top, which
    // the old system couldn't do at all. Worse, the two were actively
    // conflicting: strict-mode Private DNS validation has to succeed over
    // whatever DNS path is currently active, which - the moment the VPN is
    // running - is the VPN's own DNS server, creating a fragile dependency
    // loop between the two systems. That's what was actually causing
    // "internet totally dead while VPN is on" this whole time, not a bug in
    // the VPN pipeline itself (which always tested out fine in isolation).
    // See BlockEnforcer.releaseStrictPrivateDnsIfLocked() for the one-time
    // cleanup that undoes this on any device that had it enabled already.

    private void onBatteryExemptionClicked() {
        if (isBatteryExempt()) {
            Toast.makeText(this, R.string.msg_battery_already_exempt, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void onBlocksPauseClicked() {
        boolean currentlyPaused = blocksPauseStorage.isPaused();
        if (!currentlyPaused) {
            if (lockScheduleStorage.isCurrentlyLocked() && !HomeLocationChecker.isFarFromHome(this)) {
                Toast.makeText(this, R.string.msg_cannot_pause_now, Toast.LENGTH_LONG).show();
                return;
            }
            blocksPauseStorage.setPaused(true);
        } else {
            blocksPauseStorage.setPaused(false);
        }
        BlockEnforcer.reapplyAndReschedule(this);
        refreshUi();
    }

    private void onMasterSafetyClicked() {
        boolean currentlyEngaged = masterSafetyStorage.isEngaged();
        boolean ok = masterSafetyStorage.setEngaged(!currentlyEngaged);
        if (!ok) {
            Toast.makeText(this, R.string.msg_safety_already_deleted, Toast.LENGTH_LONG).show();
        }
        BlockEnforcer.reapplyAndReschedule(this);
        refreshUi();
    }

    private void onDeleteSafetyForeverClicked() {
        if (masterSafetyStorage.isDeletedForever()) {
            Toast.makeText(this, R.string.msg_safety_already_deleted, Toast.LENGTH_LONG).show();
            return;
        }
        if (masterSafetyStorage.isEngaged()) {
            Toast.makeText(this, R.string.msg_safety_delete_blocked_engaged, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_safety_title)
                .setMessage(R.string.confirm_delete_safety_message)
                .setPositiveButton(R.string.confirm_delete_safety_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        masterSafetyStorage.deleteForever();
                        Toast.makeText(MainActivity.this, R.string.msg_safety_deleted, Toast.LENGTH_LONG).show();
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.confirm_delete_safety_cancel, null)
                .show();
    }

    /**
     * Export/Import Backup: everything meaningful a user has configured
     * (Blocks, Lock Schedule, Holiday Breaks, Blocked Websites,
     * Registered Barcodes, Alarms, Blocks-paused, Home Location) to/from
     * a JSON file the user picks a location for via the standard Android
     * file picker (Storage Access Framework) - no storage permission
     * needed, and the file survives this app being uninstalled, unlike
     * the app's own data. See BackupManager for exactly what is and
     * isn't included and why.
     */
    private void onExportBackupClicked() {
        String filename = "self-control-backup-"
                + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(new java.util.Date())
                + ".json";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_backup_export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void onImportBackupClicked() {
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_backup_import_locked, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Backup files can show up under a few different MIME types
        // depending on how they were saved/shared, not always exactly
        // application/json - accept anything and let opening it validate.
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_backup_import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_BACKUP) {
            try {
                BackupManager.exportToUri(this, uri);
                Toast.makeText(this, R.string.msg_backup_exported, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.msg_backup_export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_IMPORT_BACKUP) {
            confirmAndImportBackup(uri);
        }
    }

    private void confirmAndImportBackup(final Uri uri) {
        // Re-checked here too - time may have passed (and the lock state
        // changed) between tapping Import and actually picking a file in
        // the system file browser.
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_backup_import_locked, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_import_backup_title)
                .setMessage(R.string.confirm_import_backup_message)
                .setPositiveButton(R.string.confirm_import_backup_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            BackupManager.importFromUri(MainActivity.this, uri);
                            Toast.makeText(MainActivity.this, R.string.msg_backup_imported, Toast.LENGTH_LONG).show();
                            refreshUi();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, getString(R.string.msg_backup_import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

}
