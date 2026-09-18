package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class HolidayBreaksStorage {

    private static final String PREFS_NAME = "floating_blocker_holiday_breaks_prefs";
    private static final String KEY_BREAKS = "holiday_breaks_json";

    private final SharedPreferences prefs;

    public HolidayBreaksStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public List<HolidayBreak> loadBreaks() {
        List<HolidayBreak> result = new ArrayList<HolidayBreak>();
        String json = prefs.getString(KEY_BREAKS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(HolidayBreak.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data - treat as no breaks rather than crash.
        }
        return result;
    }

    public void saveBreaks(List<HolidayBreak> breaks) {
        JSONArray arr = new JSONArray();
        try {
            for (HolidayBreak h : breaks) {
                arr.put(h.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_BREAKS, arr.toString()).apply();
    }
}
