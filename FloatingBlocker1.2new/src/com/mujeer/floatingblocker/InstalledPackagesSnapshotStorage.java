package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/** Remembers which launchable apps were installed as of the last check, so new-install detection can diff against it. */
public class InstalledPackagesSnapshotStorage {

    private static final String PREFS_NAME = "floating_blocker_install_snapshot_prefs";
    private static final String KEY_SNAPSHOT = "snapshot_json";
    private static final String KEY_HAS_EVER_SNAPSHOTTED = "has_ever_snapshotted";

    private final SharedPreferences prefs;

    public InstalledPackagesSnapshotStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public boolean hasEverSnapshotted() {
        return prefs.getBoolean(KEY_HAS_EVER_SNAPSHOTTED, false);
    }

    public Set<String> loadSnapshot() {
        Set<String> result = new HashSet<String>();
        String json = prefs.getString(KEY_SNAPSHOT, "[]");
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

    public void saveSnapshot(Set<String> packages) {
        JSONArray arr = new JSONArray();
        for (String pkg : packages) {
            arr.put(pkg);
        }
        prefs.edit()
                .putString(KEY_SNAPSHOT, arr.toString())
                .putBoolean(KEY_HAS_EVER_SNAPSHOTTED, true)
                .apply();
    }
}
