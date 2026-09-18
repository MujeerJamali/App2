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
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private LockScheduleStorage lockScheduleStorage;
    private BlocksPauseStorage blocksPauseStorage;
    private MasterSafetyStorage masterSafetyStorage;
    private VpnSafetyStorage vpnSafetyStorage;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    private TextView txtDeviceOwnerStatus;
    private Button btnBlocksPause;
    private Button btnMasterSafety;
    private Button btnDeleteSafetyForever;
    private Button btnBatteryExemption;
    private Button btnVpnSafety;
    private Button btnDeleteVpnSafetyForever;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lockScheduleStorage = new LockScheduleStorage(this);
        blocksPauseStorage = new BlocksPauseStorage(this);
        masterSafetyStorage = new MasterSafetyStorage(this);
        vpnSafetyStorage = new VpnSafetyStorage(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FloatingBlockerDeviceAdminReceiver.class);

        txtDeviceOwnerStatus = (TextView) findViewById(R.id.txtDeviceOwnerStatus);
        btnBlocksPause = (Button) findViewById(R.id.btnBlocksPause);
        btnMasterSafety = (Button) findViewById(R.id.btnMasterSafety);
        btnDeleteSafetyForever = (Button) findViewById(R.id.btnDeleteSafetyForever);
        btnBatteryExemption = (Button) findViewById(R.id.btnBatteryExemption);
        btnVpnSafety = (Button) findViewById(R.id.btnVpnSafety);
        btnDeleteVpnSafetyForever = (Button) findViewById(R.id.btnDeleteVpnSafetyForever);

        // Note: the VPN's actual start/stop is handled by
        // BlockEnforcer.reapplyAndReschedule() in onResume() below, which
        // correctly respects VPN Safety's current state - starting it
        // unconditionally here would silently override the kill switch
        // every time this screen opens.

        btnVpnSafety.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onVpnSafetyClicked();
            }
        });

        btnDeleteVpnSafetyForever.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onDeleteVpnSafetyForeverClicked();
            }
        });

        Button btnBlocks = (Button) findViewById(R.id.btnBlocks);
        Button btnLockSchedule = (Button) findViewById(R.id.btnLockSchedule);
        Button btnHolidayBreaks = (Button) findViewById(R.id.btnHolidayBreaks);
        Button btnWebsites = (Button) findViewById(R.id.btnWebsites);

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

        Button btnDiagnostic = (Button) findViewById(R.id.btnDiagnostic);
        btnDiagnostic.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, DiagnosticActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cheap and idempotent - makes sure anything changed in Blocks/Lock
        // Schedule screens, or elsewhere, is reflected immediately.
        BlockEnforcer.reapplyAndReschedule(this);
        refreshUi();
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
                ? Color.parseColor("#2E7D32")
                : Color.parseColor("#B71C1C"));

        boolean paused = blocksPauseStorage.isPaused();
        btnBlocksPause.setText(paused ? R.string.blocks_paused : R.string.blocks_active);
        btnBlocksPause.setBackgroundColor(paused ? Color.parseColor("#B71C1C") : Color.parseColor("#2E7D32"));
        btnBlocksPause.setTextColor(Color.WHITE);

        boolean safetyEngaged = masterSafetyStorage.isEngaged();
        boolean safetyDeleted = masterSafetyStorage.isDeletedForever();

        if (safetyDeleted) {
            btnMasterSafety.setText(R.string.safety_deleted_button);
            btnMasterSafety.setEnabled(false);
            btnDeleteSafetyForever.setEnabled(false);
        } else {
            btnMasterSafety.setText(safetyEngaged ? R.string.safety_disengage_button : R.string.safety_engage_button);
            btnMasterSafety.setEnabled(true);
            btnDeleteSafetyForever.setEnabled(true);
        }

        boolean batteryExempt = isBatteryExempt();
        btnBatteryExemption.setText(batteryExempt ? R.string.battery_exempt_on : R.string.battery_exempt_off);
        btnBatteryExemption.setEnabled(!batteryExempt);

        boolean vpnSafetyEngaged = vpnSafetyStorage.isEngaged();
        boolean vpnSafetyDeleted = vpnSafetyStorage.isDeletedForever();
        if (vpnSafetyDeleted) {
            btnVpnSafety.setText(R.string.vpn_safety_deleted_button);
            btnVpnSafety.setEnabled(false);
            btnDeleteVpnSafetyForever.setEnabled(false);
        } else {
            btnVpnSafety.setText(vpnSafetyEngaged ? R.string.vpn_safety_disengage_button : R.string.vpn_safety_engage_button);
            btnVpnSafety.setEnabled(true);
            btnDeleteVpnSafetyForever.setEnabled(true);
        }
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
            if (lockScheduleStorage.isCurrentlyLocked()) {
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

    private void onVpnSafetyClicked() {
        boolean currentlyEngaged = vpnSafetyStorage.isEngaged();
        boolean ok = vpnSafetyStorage.setEngaged(!currentlyEngaged);
        if (!ok) {
            Toast.makeText(this, R.string.msg_vpn_safety_already_deleted, Toast.LENGTH_LONG).show();
        } else {
            // Must take effect immediately, not wait for the next alarm cycle -
            // this is meant to work as a real emergency kill switch.
            BlockEnforcer.reapplyAndReschedule(this);
        }
        refreshUi();
    }

    private void onDeleteVpnSafetyForeverClicked() {
        if (vpnSafetyStorage.isDeletedForever()) {
            Toast.makeText(this, R.string.msg_vpn_safety_already_deleted, Toast.LENGTH_LONG).show();
            return;
        }
        if (vpnSafetyStorage.isEngaged()) {
            Toast.makeText(this, R.string.msg_vpn_safety_delete_blocked_engaged, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_vpn_safety_title)
                .setMessage(R.string.confirm_delete_vpn_safety_message)
                .setPositiveButton(R.string.confirm_delete_safety_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        vpnSafetyStorage.deleteForever();
                        Toast.makeText(MainActivity.this, R.string.msg_vpn_safety_deleted, Toast.LENGTH_LONG).show();
                        refreshUi();
                    }
                })
                .setNegativeButton(R.string.confirm_delete_safety_cancel, null)
                .show();
    }
}
