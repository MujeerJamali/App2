package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class AlarmsStorage {

    private static final String PREFS_NAME = "floating_blocker_alarms_prefs";
    private static final String KEY_ALARMS = "alarms_json";

    private final SharedPreferences prefs;

    public AlarmsStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public List<Alarm> loadAlarms() {
        List<Alarm> result = new ArrayList<Alarm>();
        String json = prefs.getString(KEY_ALARMS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data - treat as no alarms rather than crash.
        }
        return result;
    }

    public void saveAlarms(List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : alarms) {
                arr.put(a.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_ALARMS, arr.toString()).apply();
    }
}
