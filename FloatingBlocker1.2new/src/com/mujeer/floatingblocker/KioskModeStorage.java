package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * BETA: state for the multi-app kiosk mode demo - which apps are
 * currently allowed, and when the active session is due to end. This is
 * deliberately kept completely separate from BlockEnforcer/Blocks - lock
 * task mode is a fundamentally different mechanism (pin-the-whole-device
 * vs suspend-specific-packages) and mixing them into the same enforcement
 * engine would risk the well-tested existing system for the sake of an
 * experimental one.
 */
public class KioskModeStorage {

    private static final String PREFS_NAME = "floating_blocker_kiosk_mode_prefs";
    private static final String KEY_ALLOWED_PACKAGES = "allowed_packages_json";
    private static final String KEY_SESSION_ACTIVE = "session_active";
    private static final String KEY_SESSION_END_MILLIS = "session_end_millis";

    private final SharedPreferences prefs;

    public KioskModeStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public Set<String> loadAllowedPackages() {
        Set<String> result = new HashSet<String>();
        String json = prefs.getString(KEY_ALLOWED_PACKAGES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
        } catch (JSONException e) {
            // Corrupt data - treat as empty rather than crash.
        }
        return result;
    }

    public void saveAllowedPackages(Set<String> packages) {
        JSONArray arr = new JSONArray();
        for (String p : packages) arr.put(p);
        prefs.edit().putString(KEY_ALLOWED_PACKAGES, arr.toString()).apply();
    }

    public boolean isSessionActive() {
        return prefs.getBoolean(KEY_SESSION_ACTIVE, false);
    }

    public long getSessionEndMillis() {
        return prefs.getLong(KEY_SESSION_END_MILLIS, 0);
    }

    public void startSession(long endMillis) {
        prefs.edit()
                .putBoolean(KEY_SESSION_ACTIVE, true)
                .putLong(KEY_SESSION_END_MILLIS, endMillis)
                .apply();
    }

    public void endSession() {
        prefs.edit()
                .putBoolean(KEY_SESSION_ACTIVE, false)
                .remove(KEY_SESSION_END_MILLIS)
                .apply();
    }
}
