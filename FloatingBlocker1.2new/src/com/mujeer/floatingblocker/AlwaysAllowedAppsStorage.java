package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * Tier-2 of the Kiosk allow-list ("Always Allowed Apps"): a user-managed
 * whitelist, separate from any individual Block's own whitelist, used as
 * the fallback/floor allow-list whenever Lock Schedule is locked (combined
 * with whatever Block, if any, happens to be active - see
 * KioskAllowList). Unlike Tier-1 (KioskAllowList's hardcoded set), this is
 * editable, and editing it that WEAKENS restriction (adding an app) is
 * gated to unlocked Lock Schedule time, same as every other
 * restriction-weakening edit elsewhere in this app. Removing an app only
 * ever strengthens restriction, so it's never gated.
 */
public class AlwaysAllowedAppsStorage {

    private static final String PREFS_NAME = "floating_blocker_always_allowed_apps_prefs";
    private static final String KEY_PACKAGES = "packages_json";

    private final Context context;
    private final SharedPreferences prefs;

    public AlwaysAllowedAppsStorage(Context context) {
        this.context = context;
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    /**
     * Loads the stored list, auto-pruning (and persisting the prune) any
     * package that's no longer installed - see decision #31. A
     * reinstalled app has to be added back through the picker rather than
     * silently reappearing.
     */
    public Set<String> loadApps() {
        Set<String> stored = loadRaw();
        Set<String> pruned = new HashSet<String>();
        PackageManager pm = context.getPackageManager();
        boolean changed = false;
        for (String pkg : stored) {
            if (isInstalled(pm, pkg)) {
                pruned.add(pkg);
            } else {
                changed = true;
            }
        }
        if (changed) {
            saveRaw(pruned);
        }
        return pruned;
    }

    private boolean isInstalled(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private Set<String> loadRaw() {
        Set<String> result = new HashSet<String>();
        String json = prefs.getString(KEY_PACKAGES, "[]");
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

    private void saveRaw(Set<String> packages) {
        JSONArray arr = new JSONArray();
        for (String pkg : packages) {
            arr.put(pkg);
        }
        prefs.edit().putString(KEY_PACKAGES, arr.toString()).apply();
    }

    public void saveApps(Set<String> packages) {
        saveRaw(packages);
    }
}
