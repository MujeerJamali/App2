package com.mujeer.floatingblocker;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Safety-net for a Kiosk Mode session's auto-end. Normally
 * KioskSessionActivity ends the session itself (calls stopLockTask() from
 * within the pinned Activity - the only place that call can be made from).
 * If that Activity died mid-session before doing so, this still fires at
 * the same end time and clears the DPC's lock task allowlist, which forces
 * the system to exit lock task mode for whatever's currently pinned even
 * without an explicit stopLockTask() call.
 */
public class KioskTimeoutReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        KioskModeStorage kioskModeStorage = new KioskModeStorage(context);
        if (!kioskModeStorage.isSessionActive()) {
            // KioskSessionActivity already ended it normally - nothing to do.
            return;
        }
        if (System.currentTimeMillis() < kioskModeStorage.getSessionEndMillis()) {
            // Session end time was pushed back after this was scheduled - not due yet.
            return;
        }

        kioskModeStorage.endSession();

        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(context, FloatingBlockerDeviceAdminReceiver.class);
        try {
            devicePolicyManager.setLockTaskPackages(adminComponent, new String[0]);
        } catch (Exception e) {
            // Device Owner no longer active, or some other transient failure - nothing more we can do here.
        }
    }
}
