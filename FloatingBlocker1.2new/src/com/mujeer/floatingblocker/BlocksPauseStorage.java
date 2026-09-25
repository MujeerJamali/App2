package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/** Global pause for Blocks enforcement only - independent of the Protection switch. */
public class BlocksPauseStorage {

    private static final String PREFS_NAME = "floating_blocker_blocks_pause_prefs";
    private static final String KEY_PAUSED = "blocks_paused";

    private final SharedPreferences prefs;

    public BlocksPauseStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public boolean isPaused() {
        return prefs.getBoolean(KEY_PAUSED, false);
    }

    public void setPaused(boolean paused) {
        prefs.edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    /**
     * A separate, self-expiring override on top of the manual pause above -
     * used by the one-time "Pause All Blocks for 1 Hour" action (see
     * MainActivity). Kept as its own absolute [now, untilMillis) window
     * rather than reusing the manual paused flag, specifically so it
     * resumes Blocks on its own once the hour is up - the manual flag has
     * no auto-expiry and can only be un-set during an unlocked Lock
     * Schedule time, which would defeat the entire point of a temporary
     * override meant to work while locked.
     */
    public long getTemporaryOverrideUntilMillis() {
        return prefs.getLong("temp_override_until", 0);
    }

    /**
     * Only ever EXTENDS the override, never shortens it - if more than one
     * one-time pause action exists (e.g. "for 1 hour" and "until 11pm"),
     * using them in either order just extends coverage rather than the
     * second one accidentally cutting the first one short.
     */
    public void setTemporaryOverrideUntilMillis(long untilMillis) {
        if (untilMillis > getTemporaryOverrideUntilMillis()) {
            prefs.edit().putLong("temp_override_until", untilMillis).apply();
        }
    }

    public boolean isTemporaryOverrideActive(long nowMillis) {
        return nowMillis < getTemporaryOverrideUntilMillis();
    }

    /** Whether the one-time "Pause All Blocks for 1 Hour" action has already been used - it can only ever be used once. */
    public boolean hasUsedOneTimePause() {
        return prefs.getBoolean("one_time_pause_used", false);
    }

    public void markOneTimePauseUsed() {
        prefs.edit().putBoolean("one_time_pause_used", true).apply();
    }

    /** Whether the one-time "Pause All Blocks Until 11 PM" action has already been used - it can only ever be used once. */
    public boolean hasUsedOneTimePauseUntil11pm() {
        return prefs.getBoolean("one_time_pause_until_11pm_used", false);
    }

    public void markOneTimePauseUntil11pmUsed() {
        prefs.edit().putBoolean("one_time_pause_until_11pm_used", true).apply();
    }
}
