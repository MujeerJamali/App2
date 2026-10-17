package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the most recent uncaught crash's stack trace (see
 * FloatingBlockerApplication), so it can be shown on-screen the next time
 * the app is opened - this app deliberately avoids relying on logcat
 * (not practically accessible on a non-rooted device), so a real crash
 * needs to be surfaced directly in the UI to actually be diagnosable.
 */
public class CrashLogStorage {

    private static final String PREFS_NAME = "floating_blocker_crash_prefs";
    private static final String KEY_TRACE = "last_crash_trace";
    private static final String KEY_TIME = "last_crash_time";
    private static final String KEY_SEEN = "last_crash_seen";

    private final SharedPreferences prefs;

    public CrashLogStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public void recordCrash(String trace) {
        prefs.edit()
                .putString(KEY_TRACE, trace)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .putBoolean(KEY_SEEN, false)
                .apply();
    }

    public boolean hasUnseenCrash() {
        return prefs.contains(KEY_TRACE) && !prefs.getBoolean(KEY_SEEN, true);
    }

    public String getTrace() {
        return prefs.getString(KEY_TRACE, "");
    }

    public long getTimeMillis() {
        return prefs.getLong(KEY_TIME, 0);
    }

    public void markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply();
    }
}
