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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_EXPORT_BACKUP = 801;
    private static final int REQUEST_IMPORT_BACKUP = 802;

    private LockScheduleStorage lockScheduleStorage;
    private BlocksPauseStorage blocksPauseStorage;
    private LockScheduleBreakerStorage lockScheduleBreakerStorage;
    private MasterSafetyStorage masterSafetyStorage;
    private KioskSafetyStorage kioskSafetyStorage;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    private TextView txtDeviceOwnerStatus;
    private Button btnBlocksPause;
    private Button btnMasterSafety;
    private Button btnDeleteSafetyForever;
    private Button btnKioskSafety;
    private Button btnDeleteKioskSafetyForever;
    private Button btnBatteryExemption;
    private Button btnScanRingingAlarm;
    private EditText editCustomPauseMinutes;
    private Button btnCustomPause;
    private Button btnDeleteCustomPauseForever;
    private EditText editLockScheduleBreakerMinutes;
    private Button btnLockScheduleBreaker;
    private Button btnExcludeBusinessErp;
    private PermanentAppExclusionStorage permanentExclusionStorage;

    private static final String BUSINESS_ERP_PACKAGE = "com.mujeer.businesserp";

    private final Handler ringingAlarmHandler = new Handler();
    private final Runnable ringingAlarmTick = new Runnable() {
        @Override
        public void run() {
            refreshRingingAlarmButton();
            refreshCustomPauseButton();
            refreshLockScheduleBreakerButton();
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
        lockScheduleBreakerStorage = new LockScheduleBreakerStorage(this);
        masterSafetyStorage = new MasterSafetyStorage(this);
        kioskSafetyStorage = new KioskSafetyStorage(this);
        permanentExclusionStorage = new PermanentAppExclusionStorage(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FloatingBlockerDeviceAdminReceiver.class);

        txtDeviceOwnerStatus = (TextView) findViewById(R.id.txtDeviceOwnerStatus);
        btnBlocksPause = (Button) findViewById(R.id.btnBlocksPause);
        btnMasterSafety = (Button) findViewById(R.id.btnMasterSafety);
        btnDeleteSafetyForever = (Button) findViewById(R.id.btnDeleteSafetyForever);
        btnKioskSafety = (Button) findViewById(R.id.btnKioskSafety);
        btnDeleteKioskSafetyForever = (Button) findViewById(R.id.btnDeleteKioskSafetyForever);
        btnBatteryExemption = (Button) findViewById(R.id.btnBatteryExemption);
        btnScanRingingAlarm = (Button) findViewById(R.id.btnScanRingingAlarm);
        editCustomPauseMinutes = (EditText) findViewById(R.id.editCustomPauseMinutes);
        btnCustomPause = (Button) findViewById(R.id.btnCustomPause);
        btnDeleteCustomPauseForever = (Button) findViewById(R.id.btnDeleteCustomPauseForever);
        editLockScheduleBreakerMinutes = (EditText) findViewById(R.id.editLockScheduleBreakerMinutes);
        btnLockScheduleBreaker = (Button) findViewById(R.id.btnLockScheduleBreaker);
        btnExcludeBusinessErp = (Button) findViewById(R.id.btnExcludeBusinessErp);
        btnDeleteSafetyForever.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_danger)));
        btnDeleteKioskSafetyForever.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_danger)));
        btnScanRingingAlarm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.color_danger)));

        Button btnBlocks = (Button) findViewById(R.id.btnBlocks);
        Button btnLockSchedule = (Button) findViewById(R.id.btnLockSchedule);
        Button btnHolidayBreaks = (Button) findViewById(R.id.btnHolidayBreaks);
        Button btnWebsites = (Button) findViewById(R.id.btnWebsites);
        Button btnAlarms = (Button) findViewById(R.id.btnAlarms);
        Button btnConfirmations = (Button) findViewById(R.id.btnConfirmations);
        Button btnBarcodes = (Button) findViewById(R.id.btnBarcodes);
        Button btnHomeLocation = (Button) findViewById(R.id.btnHomeLocation);
        Button btnViewPunishments = (Button) findViewById(R.id.btnViewPunishments);
        Button btnExportBackup = (Button) findViewById(R.id.btnExportBackup);
        Button btnImportBackup = (Button) findViewById(R.id.btnImportBackup);

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

        btnKioskSafety.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onKioskSafetyClicked();
            }
        });

        btnDeleteKioskSafetyForever.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onDeleteKioskSafetyForeverClicked();
            }
        });

        btnScanRingingAlarm.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onScanRingingAlarmClicked();
            }
        });

        btnCustomPause.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onCustomPauseClicked();
            }
        });

        btnDeleteCustomPauseForever.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onDeleteCustomPauseForeverClicked();
            }
        });

        btnLockScheduleBreaker.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onLockScheduleBreakerClicked();
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

        btnConfirmations.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, ConfirmationsListActivity.class));
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

        btnViewPunishments.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, PunishmentStatusActivity.class));
            }
        });

        btnExportBackup.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onExportBackupClicked();
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
        // Same main-thread reasoning as AlarmScheduler.rescheduleAll() right
        // above - ConfirmationFireReceiver also reschedules its own
        // Confirmation's next occurrence on the main thread when it fires.
        ConfirmationScheduler.rescheduleAll(this);

        // Simply having gotten this far means the app was just opened -
        // that's the entire "task" for a Confirmation (see Confirmation.java),
        // so resolve every one currently pending right here. Cheap (no
        // DevicePolicyManager calls), so no need to background it.
        ConfirmationPunisher.confirmAllPending(this);

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

        boolean kioskSafetyEngaged = kioskSafetyStorage.isEngaged();
        boolean kioskSafetyDeleted = kioskSafetyStorage.isDeletedForever();
        if (kioskSafetyDeleted) {
            btnKioskSafety.setText(R.string.kiosk_safety_deleted_button);
            btnKioskSafety.setEnabled(false);
            btnKioskSafety.setBackgroundTintList(null);
            btnDeleteKioskSafetyForever.setEnabled(false);
        } else {
            btnKioskSafety.setText(kioskSafetyEngaged ? R.string.kiosk_safety_disengage_button : R.string.kiosk_safety_engage_button);
            btnKioskSafety.setEnabled(true);
            btnKioskSafety.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    kioskSafetyEngaged ? getColor(R.color.color_danger) : getColor(R.color.primary)));
            btnDeleteKioskSafetyForever.setEnabled(true);
        }

        refreshCustomPauseButton();
        refreshLockScheduleBreakerButton();

        btnExcludeBusinessErp.setVisibility(permanentExclusionStorage.isExcluded(BUSINESS_ERP_PACKAGE)
                ? android.view.View.GONE : android.view.View.VISIBLE);

        refreshRingingAlarmButton();
    }

    /**
     * Unlike the old one-time pauses (deleted - see KIOSK_MODE_PLAN.md
     * decision #16), this one never uses itself up -
     * it can be triggered again and again, for whatever duration is typed
     * in each time, extending the same shared temporary-override window
     * (BlocksPauseStorage.setTemporaryOverrideUntilMillis - extend-only, so
     * this composes safely with the one-time pauses too). The only thing
     * that ever turns it off is deleteCustomPauseForever() below, which is
     * irreversible by design - not gated on Lock Schedule's unlocked
     * state, same reasoning as every other safety valve in this app.
     */
    private void onCustomPauseClicked() {
        if (blocksPauseStorage.isCustomPauseDeletedForever()) {
            return;
        }
        final int minutes;
        try {
            minutes = Integer.parseInt(editCustomPauseMinutes.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.msg_enter_custom_pause_minutes, Toast.LENGTH_SHORT).show();
            return;
        }
        if (minutes <= 0) {
            Toast.makeText(this, R.string.msg_enter_custom_pause_minutes, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_custom_pause_title)
                .setMessage(getString(R.string.confirm_custom_pause_message, minutes))
                .setPositiveButton(getString(R.string.confirm_custom_pause_yes, minutes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        blocksPauseStorage.setTemporaryOverrideUntilMillis(
                                System.currentTimeMillis() + (minutes * 60L * 1000L));
                        BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    /**
     * Irreversible: once deleted, onCustomPauseClicked() refuses to do
     * anything ever again, and this button (plus the minutes field above
     * it) disappears for good. Not gated on whether a pause window is
     * currently active - any already-running override still finishes on
     * its own either way, since deleting this just stops FUTURE uses.
     */
    private void onDeleteCustomPauseForeverClicked() {
        if (blocksPauseStorage.isCustomPauseDeletedForever()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_custom_pause_title)
                .setMessage(R.string.confirm_delete_custom_pause_message)
                .setPositiveButton(R.string.confirm_delete_custom_pause_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        blocksPauseStorage.deleteCustomPauseForever();
                        Toast.makeText(MainActivity.this, R.string.msg_custom_pause_deleted, Toast.LENGTH_LONG).show();
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void refreshCustomPauseButton() {
        if (blocksPauseStorage.isCustomPauseDeletedForever()) {
            editCustomPauseMinutes.setVisibility(android.view.View.GONE);
            btnCustomPause.setVisibility(android.view.View.GONE);
            btnDeleteCustomPauseForever.setVisibility(android.view.View.GONE);
            return;
        }
        editCustomPauseMinutes.setVisibility(android.view.View.VISIBLE);
        btnDeleteCustomPauseForever.setVisibility(android.view.View.VISIBLE);
        btnCustomPause.setVisibility(android.view.View.VISIBLE);
        long remainingMillis = blocksPauseStorage.getTemporaryOverrideUntilMillis() - System.currentTimeMillis();
        if (remainingMillis > 0) {
            btnCustomPause.setText(getString(R.string.custom_pause_button_active_format, formatRemaining(remainingMillis)));
        } else {
            btnCustomPause.setText(R.string.custom_pause_button);
        }
    }

    /**
     * One button, three states, same toggle idiom as btnBlocksPause:
     * nothing armed -> reads the minutes field and arms a new breaker
     * (always allowed, any time, since it can never take effect before
     * tomorrow - see LockScheduleBreakerStorage); pending or active ->
     * tapping instead offers to cancel it (also always allowed, any
     * time, since cancelling only ever restores restriction sooner).
     */
    private void onLockScheduleBreakerClicked() {
        long now = System.currentTimeMillis();
        if (lockScheduleBreakerStorage.hasPendingOrActiveBreaker(now)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_cancel_lock_schedule_breaker_title)
                    .setMessage(R.string.confirm_cancel_lock_schedule_breaker_message)
                    .setPositiveButton(R.string.confirm_cancel_lock_schedule_breaker_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            lockScheduleBreakerStorage.cancelBreaker();
                            BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                            Toast.makeText(MainActivity.this, R.string.msg_lock_schedule_breaker_cancelled, Toast.LENGTH_LONG).show();
                            refreshUi();
                        }
                    })
                    .setNegativeButton(R.string.cancel_button, null)
                    .show();
            return;
        }

        final int minutes;
        try {
            minutes = Integer.parseInt(editLockScheduleBreakerMinutes.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.msg_enter_lock_schedule_breaker_minutes, Toast.LENGTH_SHORT).show();
            return;
        }
        if (minutes <= 0) {
            Toast.makeText(this, R.string.msg_enter_lock_schedule_breaker_minutes, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_lock_schedule_breaker_title)
                .setMessage(getString(R.string.confirm_lock_schedule_breaker_message, minutes))
                .setPositiveButton(R.string.confirm_lock_schedule_breaker_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        long nowMillis = System.currentTimeMillis();
                        boolean armed = lockScheduleBreakerStorage.armStartingTomorrow(minutes * 60L * 1000L, nowMillis);
                        if (armed) {
                            BlockEnforcer.reapplyAndReschedule(MainActivity.this);
                            Toast.makeText(MainActivity.this, R.string.msg_lock_schedule_breaker_armed, Toast.LENGTH_LONG).show();
                        }
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void refreshLockScheduleBreakerButton() {
        long now = System.currentTimeMillis();
        long start = lockScheduleBreakerStorage.getStartMillis();
        long end = lockScheduleBreakerStorage.getEndMillis();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault());
        if (now < end && now >= start) {
            editLockScheduleBreakerMinutes.setVisibility(android.view.View.GONE);
            btnLockScheduleBreaker.setText(getString(R.string.lock_schedule_breaker_button_active_format, fmt.format(new java.util.Date(end))));
        } else if (now < end) {
            editLockScheduleBreakerMinutes.setVisibility(android.view.View.GONE);
            btnLockScheduleBreaker.setText(getString(R.string.lock_schedule_breaker_button_pending_format,
                    fmt.format(new java.util.Date(start)), formatDuration(end - start)));
        } else {
            editLockScheduleBreakerMinutes.setVisibility(android.view.View.VISIBLE);
            btnLockScheduleBreaker.setText(R.string.lock_schedule_breaker_button);
        }
    }

    private String formatDuration(long millis) {
        long totalMinutes = millis / (60L * 1000L);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
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

    /**
     * No-op: this used to remove packageName from every Block's
     * blockedPackages so it could never be blocked. Now that Business ERP
     * is unconditionally Tier-1 (see KioskAllowList), it's never suspended
     * regardless of what any Block's blockedPackages says, so there's
     * nothing left for this to meaningfully do - kept only so the existing
     * one-time button's call site still compiles, not because it still
     * matters.
     */
    private void removePackageFromAllBlocks(String packageName) {
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
     * Decisions #9/#19/#20: a separate safety valve specifically for the
     * automatic Kiosk lockdown - disables ONLY that, nothing else in this
     * app. Deliberately no gating at all (unlike the Blocks Pause
     * buttons) - it's a reliable escape hatch for a genuine malfunction,
     * which is just as likely to happen during locked time as any other.
     */
    private void onKioskSafetyClicked() {
        boolean currentlyEngaged = kioskSafetyStorage.isEngaged();
        boolean ok = kioskSafetyStorage.setEngaged(!currentlyEngaged);
        if (!ok) {
            Toast.makeText(this, R.string.msg_kiosk_safety_already_deleted, Toast.LENGTH_LONG).show();
        }
        BlockEnforcer.reapplyAndReschedule(this);
        refreshUi();
    }

    private void onDeleteKioskSafetyForeverClicked() {
        if (kioskSafetyStorage.isDeletedForever()) {
            Toast.makeText(this, R.string.msg_kiosk_safety_already_deleted, Toast.LENGTH_LONG).show();
            return;
        }
        if (kioskSafetyStorage.isEngaged()) {
            Toast.makeText(this, R.string.msg_kiosk_safety_delete_blocked_engaged, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_kiosk_safety_title)
                .setMessage(R.string.confirm_delete_kiosk_safety_message)
                .setPositiveButton(R.string.confirm_delete_kiosk_safety_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        kioskSafetyStorage.deleteForever();
                        Toast.makeText(MainActivity.this, R.string.msg_kiosk_safety_deleted, Toast.LENGTH_LONG).show();
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
