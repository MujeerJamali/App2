package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the Kiosk screen's "Recent" tab (decision #22): only tracks apps
 * actually launched from within the Kiosk allowed-apps grid itself - we
 * record it ourselves at the same point we fire the launch Intent, rather
 * than querying whatever happened to be in the foreground when pinning
 * kicked in. Most-recently-launched first; a package moves to the front
 * on each new launch instead of duplicating.
 *
 * Filtering out packages that have since left the current allow-list
 * (decision #23) is NOT done here - it needs live allow-list context, so
 * it's the caller's job (KioskSessionActivity) at display time. This
 * class only ever remembers "launched", not "still allowed right now".
 */
public class RecentAppsStorage {

    private static final String PREFS_NAME = "floating_blocker_kiosk_recent_apps_prefs";
    private static final String KEY_RECENT = "recent_packages_json";
    private static final int MAX_ENTRIES = 30;

    private final SharedPreferences prefs;

    public RecentAppsStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    /** Most-recently-launched first. */
    public List<String> loadRecentPackages() {
        List<String> result = new ArrayList<String>();
        String json = prefs.getString(KEY_RECENT, "[]");
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

    public void recordLaunch(String packageName) {
        List<String> current = loadRecentPackages();
        current.remove(packageName);
        current.add(0, packageName);
        while (current.size() > MAX_ENTRIES) {
            current.remove(current.size() - 1);
        }
        JSONArray arr = new JSONArray();
        for (String pkg : current) {
            arr.put(pkg);
        }
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply();
    }
}
