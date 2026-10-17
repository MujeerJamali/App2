package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small runtime bookkeeping for the automatic Kiosk lockdown, separate
 * from user-facing config - same split as AlarmRuntimeStorage vs
 * AlarmsStorage. Tracks only whether Kiosk was pinned as of the last
 * enforcement cycle, so BlockEnforcer can tell a fresh
 * unlocked-to-locked transition (forces entry once - decisions #12/#21)
 * apart from "still locked from last cycle" (must NOT re-force entry
 * every single cycle, which would yank the user back into Kiosk every
 * ~1 minute even while legitimately using an allowed app inside it).
 */
public class KioskRuntimeStorage {

    private final SharedPreferences prefs;

    public KioskRuntimeStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_kiosk_runtime_prefs");
    }

    public boolean wasPinnedLastCheck() {
        return prefs.getBoolean("was_pinned_last_check", false);
    }

    public void setPinnedLastCheck(boolean pinned) {
        prefs.edit().putBoolean("was_pinned_last_check", pinned).apply();
    }
}
