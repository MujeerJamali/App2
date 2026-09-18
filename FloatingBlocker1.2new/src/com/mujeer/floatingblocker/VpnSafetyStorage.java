package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The VPN's own one-time-use "break glass" safety valve - separate from
 * Master Safety, since this governs a different subsystem.
 *
 * IMPORTANT: engaging this does NOT just adjust filtering - it's wired up
 * (see BlockEnforcer) to fully stop the VPN service and release the
 * always-on assignment at the OS level, so internet works exactly as if
 * this app had no VPN at all. (Lockdown is deliberately never enabled for
 * this VPN in the first place - see BlockEnforcer for why - so there's no
 * "Android blocks all internet without us" fail-safe being undone here;
 * this is simply the real kill switch.)
 */
public class VpnSafetyStorage {

    private static final String PREFS_NAME = "floating_blocker_vpn_safety_prefs";
    private static final String KEY_ENGAGED = "vpn_safety_engaged";
    private static final String KEY_DELETED_FOREVER = "vpn_safety_deleted_forever";

    private final SharedPreferences prefs;

    public VpnSafetyStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public boolean isEngaged() {
        return prefs.getBoolean(KEY_ENGAGED, false);
    }

    public boolean isDeletedForever() {
        return prefs.getBoolean(KEY_DELETED_FOREVER, false);
    }

    public boolean setEngaged(boolean engaged) {
        if (isDeletedForever()) {
            return false;
        }
        prefs.edit().putBoolean(KEY_ENGAGED, engaged).apply();
        return true;
    }

    public boolean deleteForever() {
        if (isEngaged()) {
            return false;
        }
        prefs.edit().putBoolean(KEY_DELETED_FOREVER, true).apply();
        return true;
    }
}
