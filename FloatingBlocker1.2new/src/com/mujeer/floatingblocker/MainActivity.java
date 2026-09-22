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
    private Button btnClearPunishment;
    private Button btnPauseAllOneHour;
    private Button btnPauseUntil11pm;
    private Button btnExcludeBusinessErp;
    private BlockPunishmentStorage punishmentStorage;
    private PermanentAppExclusionStorage permanentExclusionStorage;

    private static final String BUSINESS_ERP_PACKAGE = "com.mujeer.businesserp";

    private final Handler ringingAlarmHandler = new Handler();
    private final Runnable ringingAlarmTick = new Runnable() {
        @Override
        public void run() {
            refreshRingingAlarmButton();
            refreshPauseAllButton();
            refreshPauseUntil11pmButton();
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
        punishmentStorage = new BlockPunishmentStorage(this);
        permanentExclusionStorage = new PermanentAppExclusionStorage(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FloatingBlockerDeviceAdminReceiver.class);

        txtDeviceOwnerStatus = (TextView) findViewById(R.id.txtDeviceOwnerStatus);
        btnBlocksPause = (Button) findViewById(R.id.btnBlocksPause);
        btnMasterSafety = (Button) findViewById(R.id.btnMasterSafety);
        btnDeleteSafetyForever = (Button) findViewById(R.id.btnDeleteSafetyForever);
        btnBatteryExemption = (Button) findViewById(R.id.btnBatteryExemption);
        btnScanRingingAlarm = (Button) findViewById(R.id.btnScanRingingAlarm);
        btnClearPunishment = (Button) findViewById(R.id.btnClearPunishment);
        btnPauseAllOneHour = (Button) findViewById(R.id.btnPauseAllOneHour);
        btnPauseUntil11pm = (Button) findViewById(R.id.btnPauseUntil11pm);
        btnExcludeBusinessErp = (Button) findViewById(R.id.btnExcludeBusinessErp);
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

        btnClearPunishment.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onClearPunishmentClicked();
            }
        });

        btnPauseAllOneHour.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onPauseAllOneHourClicked();
            }
        });

        btnPauseUntil11pm.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onPauseUntil11pmClicked();
            }
        });

        btnExcludeBusinessErp.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onExcludeBusinessErpClicked();
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
        // Show what we already have immediately, rather than making the
        // whole window wait on the enforcement pass below.
        refreshUi();
        // Ticks the ringing-alarm button's countdown once a second while
        // this screen is visible - also means the button appears/updates/
        // disappears live if an Alarm starts or resolves while already
        // sitting on this screen, not just on the next resume.
        ringingAlarmHandler.postDelayed(ringingAlarmTick, 1000);

        // AlarmScheduler.rescheduleAll() stays on the MAIN thread,
        // deliberately - it's cheap (just AlarmManager calls, no
        // DevicePolicyManager Binder overhead), and more importantly,
        // AlarmRingReceiver ALSO reschedules this same alarm's next
        // occurrence when it fires, on the main thread (BroadcastReceivers
        // run there by default). Keeping both on the same thread means
        // Android's single-threaded main Looper serializes them - they can
        // never truly overlap. Running this on a background thread (tried
        // briefly) opened a real race: a background call could read "now"
        // just before an alarm's exact trigger time, compute that trigger
        // as still-upcoming, and then - if its own am.setExactAndAllowWhileIdle()
        // call executed even slightly late, after the real alarm had
        // already fired and been correctly rescheduled for its next
        // occurrence - overwrite that correct future schedule with an
        // already-past timestamp, which Android fires again almost
        // immediately. That's consistent with a reported spurious second
        // ring a few minutes off from the real trigger time.
        AlarmScheduler.rescheduleAll(this);

        // BlockEnforcer.applyNow() makes well over a dozen synchronous
        // DevicePolicyManager Binder calls back-to-back. Logged evidence
        // (from a white-screen-on-open report) showed a single one of
        // those calls occasionally taking 1+ second on at least one OEM
        // build, and a separate attempt going more than 18 seconds with
        // zero log output before being killed - consistent with OEM-side
        // Binder/system_server latency, not a deterministic bug in this
        // code. Running it on a background thread means a slow Binder
        // round-trip can no longer block the window from ever becoming
        // visible/responsive - the small risk of showing briefly-stale
        // status (e.g. Device Owner state) until refreshUi() re-runs at
        // the end is a clearly worthwhile trade against a frozen app. This
        // one doesn't share a PendingIntent with any receiver that also
        // runs on the main thread the way AlarmScheduler does, so it
        // doesn't carry the same race risk.
        new Thread(new Runnable() {
            @Override
            public void run() {
                BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshUi();
                    }
                });
            }
        }).start();
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

        btnClearPunishment.setVisibility(punishmentStorage.hasUsedOneTimeClear()
                ? android.view.View.GONE : android.view.View.VISIBLE);

        refreshPauseAllButton();
        refreshPauseUntil11pmButton();

        btnExcludeBusinessErp.setVisibility(permanentExclusionStorage.isExcluded(BUSINESS_ERP_PACKAGE)
                ? android.view.View.GONE : android.view.View.VISIBLE);

        refreshRingingAlarmButton();
    }

    /**
     * A one-time-only escape hatch for Alarm-miss punishment (Block
     * widening) that wasn't actually deserved - e.g. a bug fabricating
     * missed occurrences that never really happened. Deliberately NOT
     * gated on Lock Schedule's unlocked state like every other
     * weakening action in this app: being usable exactly once, ever,
     * already prevents it from becoming a repeatable loophole, and
     * gating it to unlocked-only could make it unusable exactly when
     * it's needed (right after the punishment was wrongly applied,
     * while still locked). Once used, the button is gone forever.
     */
    private void onClearPunishmentClicked() {
        if (punishmentStorage.hasUsedOneTimeClear()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_punishment_title)
                .setMessage(R.string.confirm_clear_punishment_message)
                .setPositiveButton(R.string.confirm_clear_punishment_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        punishmentStorage.clearAll();
                        punishmentStorage.markOneTimeClearUsed();
                        Toast.makeText(MainActivity.this, R.string.msg_punishment_cleared, Toast.LENGTH_LONG).show();
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    /**
     * A one-time-only emergency unsuspend for every currently-blocked app,
     * for exactly 1 hour, then Blocks resume automatically on their own -
     * meant for a situation where Blocks enforcement is itself getting in
     * the way of fixing a real problem (e.g. needing AIDE/other tools to
     * debug this app while a Lock Schedule sleep window is about to
     * suspend them). Not gated on Lock Schedule's unlocked state, same
     * reasoning as Clear Current Punishment above - usable exactly once,
     * ever, already prevents it being a repeatable loophole, and it needs
     * to actually work while locked to be useful at all. Auto-expiring
     * (via BlocksPauseStorage's temporary-override window) rather than a
     * plain pause specifically so it can't get stuck "paused forever"
     * behind a locked schedule with no way to manually resume it.
     */
    private void onPauseAllOneHourClicked() {
        if (blocksPauseStorage.hasUsedOneTimePause()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_pause_all_title)
                .setMessage(R.string.confirm_pause_all_message)
                .setPositiveButton(R.string.confirm_pause_all_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        blocksPauseStorage.setTemporaryOverrideUntilMillis(
                                System.currentTimeMillis() + (60L * 60L * 1000L));
                        blocksPauseStorage.markOneTimePauseUsed();
                        BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    /**
     * Visible and clickable before first use; visible-but-informational
     * with a live countdown while the 1-hour window is active; gone for
     * good once that window has passed - it can never be triggered again.
     */
    private void refreshPauseAllButton() {
        if (!blocksPauseStorage.hasUsedOneTimePause()) {
            btnPauseAllOneHour.setVisibility(android.view.View.VISIBLE);
            btnPauseAllOneHour.setEnabled(true);
            btnPauseAllOneHour.setText(R.string.pause_all_button_activate);
            return;
        }
        long remainingMillis = blocksPauseStorage.getTemporaryOverrideUntilMillis() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            btnPauseAllOneHour.setVisibility(android.view.View.GONE);
            return;
        }
        btnPauseAllOneHour.setVisibility(android.view.View.VISIBLE);
        btnPauseAllOneHour.setEnabled(false);
        btnPauseAllOneHour.setText(getString(R.string.pause_all_button_active_format, formatRemaining(remainingMillis)));
    }

    /**
     * A second, independent one-time emergency unsuspend - same mechanism
     * as Pause All Blocks for 1 Hour (the shared temporary-override window
     * in BlocksPauseStorage, which only ever extends, never shortens), but
     * targeting a fixed clock time (11 PM) rather than a fixed duration.
     * If it's already past 11 PM when this is used, targets 11 PM
     * tomorrow instead, so "until 11 PM" always means something.
     */
    private void onPauseUntil11pmClicked() {
        if (blocksPauseStorage.hasUsedOneTimePauseUntil11pm()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_pause_until_11pm_title)
                .setMessage(R.string.confirm_pause_until_11pm_message)
                .setPositiveButton(R.string.confirm_pause_until_11pm_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
                        cal.set(java.util.Calendar.MINUTE, 0);
                        cal.set(java.util.Calendar.SECOND, 0);
                        cal.set(java.util.Calendar.MILLISECOND, 0);
                        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                        }
                        blocksPauseStorage.setTemporaryOverrideUntilMillis(cal.getTimeInMillis());
                        blocksPauseStorage.markOneTimePauseUntil11pmUsed();
                        BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void refreshPauseUntil11pmButton() {
        if (!blocksPauseStorage.hasUsedOneTimePauseUntil11pm()) {
            btnPauseUntil11pm.setVisibility(android.view.View.VISIBLE);
            btnPauseUntil11pm.setEnabled(true);
            btnPauseUntil11pm.setText(R.string.pause_until_11pm_button_activate);
            return;
        }
        long remainingMillis = blocksPauseStorage.getTemporaryOverrideUntilMillis() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            btnPauseUntil11pm.setVisibility(android.view.View.GONE);
            return;
        }
        btnPauseUntil11pm.setVisibility(android.view.View.VISIBLE);
        btnPauseUntil11pm.setEnabled(false);
        btnPauseUntil11pm.setText(getString(R.string.pause_until_11pm_button_active_format, formatRemaining(remainingMillis)));
    }

    /**
     * One-time-only: permanently excludes BUSINESS_ERP_PACKAGE from ever
     * being suspended by any Block, current or future - the enforcement
     * filter in BlockEnforcer.applyNow() is the actual guarantee (it
     * strips excluded packages out right before suspending anything,
     * regardless of what any Block's own stored list says), this also
     * does a one-time cleanup pass removing it from every current Block's
     * list, purely so it isn't confusingly still shown as "in" a Block it
     * will never actually be blocked by. The button disappears for good
     * once used - there's no in-app way to reverse this.
     */
    private void onExcludeBusinessErpClicked() {
        if (permanentExclusionStorage.isExcluded(BUSINESS_ERP_PACKAGE)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_exclude_business_erp_title)
                .setMessage(R.string.confirm_exclude_business_erp_message)
                .setPositiveButton(R.string.confirm_exclude_business_erp_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        permanentExclusionStorage.addExcludedPackage(BUSINESS_ERP_PACKAGE);
                        removePackageFromAllBlocks(BUSINESS_ERP_PACKAGE);
                        BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                        Toast.makeText(MainActivity.this, R.string.msg_business_erp_excluded, Toast.LENGTH_LONG).show();
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void removePackageFromAllBlocks(String packageName) {
        BlocksStorage blocksStorage = new BlocksStorage(this);
        List<Block> blocks = blocksStorage.loadBlocks();
        boolean changed = false;
        for (Block b : blocks) {
            if (b.blockedPackages.remove(packageName)) {
                changed = true;
            }
        }
        if (changed) {
            blocksStorage.saveBlocks(blocks);
        }
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
