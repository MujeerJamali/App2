package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * A simple list of blocked domains (not full URLs - whole domains only,
 * per design: never block a specific page, always the entire domain).
 * Enforcement itself isn't built yet - this is just the storage +
 * management UI, ready for whatever enforcement mechanism gets added.
 */
public class BlockedWebsitesStorage {

    private static final String PREFS_NAME = "floating_blocker_websites_prefs";
    private static final String KEY_DOMAINS = "blocked_domains_json";

    private final SharedPreferences prefs;

    public BlockedWebsitesStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public Set<String> loadDomains() {
        Set<String> result = new HashSet<String>();
        String json = prefs.getString(KEY_DOMAINS, "[]");
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

    public void saveDomains(Set<String> domains) {
        JSONArray arr = new JSONArray();
        for (String d : domains) {
            arr.put(d);
        }
        prefs.edit().putString(KEY_DOMAINS, arr.toString()).apply();
    }

    /** Lowercases and strips a leading "www." so different forms of the same domain match as one entry. */
    public static String normalize(String domain) {
        if (domain == null) {
            return null;
        }
        String d = domain.trim().toLowerCase();
        if (d.startsWith("www.")) {
            d = d.substring(4);
        }
        return d;
    }
}
