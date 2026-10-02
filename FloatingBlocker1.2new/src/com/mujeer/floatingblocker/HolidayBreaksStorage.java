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
    private static final String KEY_MIGRATED_ALARM_FIELD = "migrated_affected_alarms";

    private final Context context;
    private final SharedPreferences prefs;

    public HolidayBreaksStorage(Context context) {
        this.context = context;
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public List<HolidayBreak> loadBreaks() {
        runAlarmFieldMigrationIfNeeded();
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

    /**
     * Every Break used to suppress EVERY Alarm while active, with no way to
     * choose which ones - affectedAlarmIds is new, and any Break saved
     * before it existed has no "alarms" key in its stored JSON at all. The
     * very first time this runs, every such Break gets backfilled with
     * every Alarm that currently exists, so a Break someone was already
     * relying on to silence every Alarm keeps doing exactly that - rather
     * than silently starting to suppress NO Alarms the moment this update
     * installs, just because the field happened to be new and empty. Any
     * Break saved by the new Alarm-picker UI already has a real (possibly
     * empty, deliberately chosen) "alarms" array, so it's left untouched.
     * Runs exactly once, guarded by KEY_MIGRATED_ALARM_FIELD.
     */
    private void runAlarmFieldMigrationIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED_ALARM_FIELD, false)) {
            return;
        }
        String json = prefs.getString(KEY_BREAKS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONArray allAlarmIds = new JSONArray();
            for (Alarm a : new AlarmsStorage(context).loadAlarms()) {
                allAlarmIds.put(a.id);
            }
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (!o.has("alarms")) {
                    o.put("alarms", allAlarmIds);
                    changed = true;
                }
            }
            if (changed) {
                prefs.edit().putString(KEY_BREAKS, arr.toString()).apply();
            }
            prefs.edit().putBoolean(KEY_MIGRATED_ALARM_FIELD, true).apply();
        } catch (JSONException e) {
            // Leave unmigrated - will retry next load rather than risk a half-written state.
        }
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
