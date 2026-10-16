package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A single, one-time (non-recurring) full break of Lock Schedule itself -
 * not scoped to specific Blocks like BlocksPauseStorage, and not scoped to
 * specific Blocks/Alarms/Confirmations like HolidayBreak - this suspends
 * LockScheduleStorage.isCurrentlyLocked() itself for its whole window,
 * which is what Kiosk pinning, every editing gate, and the anti-tamper
 * locks in BlockEnforcer all key off directly. Blocks' own independent
 * suspension (decision #42) is untouched - this only ever affects Lock
 * Schedule/Kiosk, the same way BlocksPauseStorage only ever affects Blocks.
 *
 * The defining constraint: it can only ever be armed to START on a later
 * app-day than today (see LockScheduleStorage.appDayStartMillis) - never
 * today, regardless of what time "now" is. That's what makes it safe to
 * arm at any time, even while currently locked, without weakening
 * anything happening right now - the same escape valve HolidayBreak's
 * "later app day" gate already uses. Only one can be armed/active at a
 * time; arming while one is already pending or active is refused.
 * Cancelling - whether still pending or already active - is always
 * allowed, any time, since that only ever restores restriction sooner.
 */
public class LockScheduleBreakerStorage {

    private static final String KEY_START = "start";
    private static final String KEY_END = "end";

    private final SharedPreferences prefs;

    public LockScheduleBreakerStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_lock_schedule_breaker_prefs");
    }

    public long getStartMillis() {
        return prefs.getLong(KEY_START, 0);
    }

    public long getEndMillis() {
        return prefs.getLong(KEY_END, 0);
    }

    /** True if a breaker is armed and its window hasn't ended yet - pending (not started) or currently active. */
    public boolean hasPendingOrActiveBreaker(long nowMillis) {
        return getEndMillis() > nowMillis;
    }

    public boolean isBreakerActive(long nowMillis) {
        return nowMillis >= getStartMillis() && nowMillis < getEndMillis();
    }

    /**
     * Arms a new breaker to start at the next app-day boundary strictly
     * after today's (i.e. "tomorrow", using the 2:00 AM day boundary) and
     * run for durationMillis from there. Refuses if one is already
     * pending or active - cancelBreaker() first if the user wants to
     * replace it. Returns true if armed, false if refused.
     */
    public boolean armStartingTomorrow(long durationMillis, long nowMillis) {
        if (hasPendingOrActiveBreaker(nowMillis)) {
            return false;
        }
        long start = LockScheduleStorage.appDayStartMillis(nowMillis) + (24L * 60L * 60L * 1000L);
        prefs.edit()
                .putLong(KEY_START, start)
                .putLong(KEY_END, start + durationMillis)
                .apply();
        return true;
    }

    public void cancelBreaker() {
        prefs.edit().remove(KEY_START).remove(KEY_END).apply();
    }
}
