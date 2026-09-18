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
}
