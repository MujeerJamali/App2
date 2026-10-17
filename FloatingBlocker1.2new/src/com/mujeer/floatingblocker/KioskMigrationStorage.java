package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A permanent, one-way flag for the fresh-start reset (decision #27) -
 * set once the wipe has run and never cleared. Fires exactly once, ever,
 * on the first run after this update - a future update that needs its
 * own reset would need its own separate flag, not a reuse of this one.
 */
public class KioskMigrationStorage {

    private final SharedPreferences prefs;

    public KioskMigrationStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_migration_prefs");
    }

    public boolean hasRunFreshStartReset() {
        return prefs.getBoolean("fresh_start_reset_v1_done", false);
    }

    public void markFreshStartResetDone() {
        prefs.edit().putBoolean("fresh_start_reset_v1_done", true).apply();
    }
}
