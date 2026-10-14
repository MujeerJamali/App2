package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A separate, reusable safety valve specifically for the automatic Kiosk
 * lockdown (decision #9) - independent of Master Safety (already
 * permanently deleted). Unlike Master Safety, this has NO gating: it
 * works unconditionally at any time, including while Lock Schedule is
 * locked and Kiosk is actively pinning (decision #20) - it exists to be a
 * reliable escape hatch for a genuine malfunction, which is just as
 * likely during locked time as any other time.
 *
 *  - engaged = true -> the automatic Lock-Schedule-triggered Kiosk
 *    lockdown is disabled entirely. Nothing else in this app (Blocks,
 *    Settings-lock, etc.) is affected.
 *  - deletedForever = true -> this safety valve can never be engaged
 *    again. Permanent and irreversible by design, same as Master Safety.
 */
public class KioskSafetyStorage {

    private static final String PREFS_NAME = "floating_blocker_kiosk_safety_prefs";
    private static final String KEY_ENGAGED = "kiosk_safety_engaged";
    private static final String KEY_DELETED_FOREVER = "kiosk_safety_deleted_forever";

    private final SharedPreferences prefs;

    public KioskSafetyStorage(Context context) {
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

    /** Permanently disables this safety valve. Only allowed while not currently engaged. */
    public boolean deleteForever() {
        if (isEngaged()) {
            return false;
        }
        prefs.edit().putBoolean(KEY_DELETED_FOREVER, true).apply();
        return true;
    }
}
