package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A full break of Lock Schedule itself, for a user-chosen duration - not
 * scoped to specific Blocks like BlocksPauseStorage, and not scoped to
 * specific Blocks/Alarms/Confirmations like HolidayBreak - this suspends
 * LockScheduleStorage.isCurrentlyLocked() itself for its whole window,
 * which is what Kiosk pinning, every editing gate, and the anti-tamper
 * locks in BlockEnforcer all key off directly. Blocks' own independent
 * suspension (decision #42) is untouched - this only ever affects Lock
 * Schedule/Kiosk, the same way BlocksPauseStorage only ever affects Blocks.
 *
 * Takes effect immediately, same shape as BlocksPauseStorage's own
 * temporary-override window (extend-only, self-expiring, not gated on
 * Lock Schedule's state) - "not gated on Lock Schedule's unlocked state,
 * same reasoning as every other safety valve in this app" per that
 * class's own doc comment. This is the same trade-off already accepted
 * there, just extended to a second, more powerful lever.
 */
public class LockScheduleBreakerStorage {

    private static final String KEY_UNLOCKED_UNTIL = "unlocked_until";

    private final SharedPreferences prefs;

    public LockScheduleBreakerStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_lock_schedule_breaker_prefs");
    }

    public long getUnlockedUntilMillis() {
        return prefs.getLong(KEY_UNLOCKED_UNTIL, 0);
    }

    public boolean isBreakerActive(long nowMillis) {
        return nowMillis < getUnlockedUntilMillis();
    }

    /** Only ever EXTENDS the override, never shortens it - same rule as BlocksPauseStorage's. */
    public void extendUnlockedUntilMillis(long untilMillis) {
        if (untilMillis > getUnlockedUntilMillis()) {
            prefs.edit().putLong(KEY_UNLOCKED_UNTIL, untilMillis).apply();
        }
    }

    /** Always allowed, any time - ending it early only ever restores restriction sooner. */
    public void cancelBreaker() {
        prefs.edit().remove(KEY_UNLOCKED_UNTIL).apply();
    }
}
