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
     * used by the freely-reusable custom-duration Pause button (see
     * MainActivity). Kept as its own absolute [now, untilMillis) window
     * rather than reusing the manual paused flag, specifically so it
     * resumes Blocks on its own once the window is up - the manual flag has
     * no auto-expiry and can only be un-set during an unlocked Lock
     * Schedule time, which would defeat the entire point of a temporary
     * override meant to work while locked.
     */
    public long getTemporaryOverrideUntilMillis() {
        return prefs.getLong("temp_override_until", 0);
    }

    /** Only ever EXTENDS the override, never shortens it. */
    public void setTemporaryOverrideUntilMillis(long untilMillis) {
        if (untilMillis > getTemporaryOverrideUntilMillis()) {
            prefs.edit().putLong("temp_override_until", untilMillis).apply();
        }
    }

    public boolean isTemporaryOverrideActive(long nowMillis) {
        return nowMillis < getTemporaryOverrideUntilMillis();
    }

    /**
     * Whether the freely-reusable "Pause Lock Schedule for N minutes"
     * button (custom duration, typed in each time - see MainActivity) has
     * been permanently deleted. This button itself never goes away just
     * from being used - it can be used as many times as wanted (each use
     * only ever extends the shared temporary-override window above).
     * Deleting it forever is the only thing that ever turns it off, and
     * that's irreversible by design, same reasoning as Master Safety/Kiosk
     * Safety.
     */
    public boolean isCustomPauseDeletedForever() {
        return prefs.getBoolean("custom_pause_deleted_forever", false);
    }

    public void deleteCustomPauseForever() {
        prefs.edit().putBoolean("custom_pause_deleted_forever", true).apply();
    }
}
