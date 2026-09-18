package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The one-time-use "break glass" safety valve.
 *  - engaged = true  -> ALL enforcement is suspended (Blocks, Settings-lock,
 *    everything), regardless of any other setting, until switched back off.
 *  - deletedForever = true -> the safety valve can never be engaged again.
 *    This is permanent and irreversible by design.
 */
public class MasterSafetyStorage {

    private static final String PREFS_NAME = "floating_blocker_safety_prefs";
    private static final String KEY_ENGAGED = "safety_engaged";
    private static final String KEY_DELETED_FOREVER = "safety_deleted_forever";

    private final SharedPreferences prefs;

    public MasterSafetyStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public boolean isEngaged() {
        return prefs.getBoolean(KEY_ENGAGED, false);
    }

    public boolean isDeletedForever() {
        return prefs.getBoolean(KEY_DELETED_FOREVER, false);
    }

    /** Returns false (refuses) if already permanently deleted. */
    public boolean setEngaged(boolean engaged) {
        if (isDeletedForever()) {
            return false;
        }
        prefs.edit().putBoolean(KEY_ENGAGED, engaged).apply();
        return true;
    }

    /** Permanently disables the safety valve. Only allowed while not currently engaged. */
    public boolean deleteForever() {
        if (isEngaged()) {
            return false;
        }
        prefs.edit().putBoolean(KEY_DELETED_FOREVER, true).apply();
        return true;
    }
}
