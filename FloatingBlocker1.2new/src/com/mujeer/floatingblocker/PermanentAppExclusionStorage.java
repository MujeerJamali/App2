package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * Packages that must NEVER be suspended by any Block, current or future -
 * the single source of truth BlockEnforcer.applyNow() strips out right
 * before actually suspending anything, so it doesn't matter whether some
 * Block still nominally lists an excluded package (a stale entry, a future
 * manual re-add, or new-app detection sweeping it back in) - it's simply
 * never included in what actually gets suspended, regardless.
 */
public class PermanentAppExclusionStorage {

    private static final String PREFS_NAME = "floating_blocker_permanent_exclusion_prefs";
    private static final String KEY_EXCLUDED_PACKAGES = "excluded_packages_json";

    private final SharedPreferences prefs;

    public PermanentAppExclusionStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public Set<String> loadExcludedPackages() {
        Set<String> result = new HashSet<String>();
        String json = prefs.getString(KEY_EXCLUDED_PACKAGES, "[]");
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

    public boolean isExcluded(String packageName) {
        return loadExcludedPackages().contains(packageName);
    }

    public void addExcludedPackage(String packageName) {
        Set<String> current = loadExcludedPackages();
        current.add(packageName);
        JSONArray arr = new JSONArray();
        for (String p : current) {
            arr.put(p);
        }
        prefs.edit().putString(KEY_EXCLUDED_PACKAGES, arr.toString()).apply();
    }
}
