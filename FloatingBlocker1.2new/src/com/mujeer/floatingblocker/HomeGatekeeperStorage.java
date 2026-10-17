package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Opt-in, reversible toggle for HomeGatekeeperActivity's forced
 * CATEGORY_HOME takeover (decisions #48-50) - the reboot-resilience
 * mechanism that makes this app the unchangeable default launcher so
 * Kiosk can re-engage even if BOOT_COMPLETED/AlarmManager never get a
 * chance to run. Defaults to OFF (decision #56): a real-device incident
 * showed this can combine badly with an OEM's own restricted-home power
 * saving mode, with no way back to Settings from either screen - turning
 * it on is now something the user explicitly chooses and can just as
 * easily turn back off, not something forced on by default. Checked by
 * BlockEnforcer.applyHomeGatekeeperProtection, which registers
 * HomeGatekeeperActivity only while this is true, and actively clears
 * any existing registration (DevicePolicyManager.clearPackagePersistentPreferredActivities)
 * whenever it's false - self-healing in both directions, so a device
 * upgrading from an older version where this was forced on gets it
 * undone automatically on the very next enforcement cycle.
 */
public class HomeGatekeeperStorage {

    private final SharedPreferences prefs;

    public HomeGatekeeperStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_home_gatekeeper_prefs");
    }

    public boolean isEnabledByUser() {
        return prefs.getBoolean("enabled_by_user", false);
    }

    public void setEnabledByUser(boolean enabled) {
        prefs.edit().putBoolean("enabled_by_user", enabled).apply();
    }
}
