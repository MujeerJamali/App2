package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * One-way, irreversible opt-out of HomeGatekeeperActivity's forced
 * CATEGORY_HOME takeover (decisions #48-50) - the reboot-resilience
 * mechanism that makes this app the unchangeable default launcher so
 * Kiosk can re-engage even if BOOT_COMPLETED/AlarmManager never get a
 * chance to run. Checked by BlockEnforcer.applyHomeGatekeeperProtection,
 * which simply stops (re-)registering HomeGatekeeperActivity once this
 * is set - the actual removal (DevicePolicyManager.clearPackagePersistentPreferredActivities)
 * is a one-time action fired from MainActivity when the user chooses
 * this, same irreversible-by-design pattern as Master Safety/Kiosk
 * Safety/Custom Pause's own "delete forever" buttons.
 */
public class HomeGatekeeperStorage {

    private final SharedPreferences prefs;

    public HomeGatekeeperStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_home_gatekeeper_prefs");
    }

    public boolean isPermanentlyRemovedAsLauncher() {
        return prefs.getBoolean("permanently_removed_as_launcher", false);
    }

    public void markPermanentlyRemovedAsLauncher() {
        prefs.edit().putBoolean("permanently_removed_as_launcher", true).apply();
    }
}
