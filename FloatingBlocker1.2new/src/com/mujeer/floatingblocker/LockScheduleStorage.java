package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The single shared "Lock Schedule": a list of recurring day+time ranges.
 * Its only job now is gating Block edits that would REDUCE restriction:
 *   - deleting a Block entirely
 *   - removing an app from a Block's list
 *   - removing a time range from a Block
 *   - editing this Lock Schedule itself
 *
 * Additions are never gated by this (creating a new Block, adding an app,
 * adding a time range) - those only ever make a Block MORE restrictive,
 * so they're always allowed regardless of the current lock state.
 *
 * If no range has EVER been saved yet, everything above is left OPEN so
 * there's a way to do first-time setup at all.
 */
public class LockScheduleStorage {

    private static final String PREFS_NAME = "floating_blocker_lock_schedule_prefs";
    private static final String KEY_RANGES = "lock_ranges_json";

    private final Context context;
    private final SharedPreferences prefs;

    public LockScheduleStorage(Context context) {
        this.context = context;
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public List<TimeRange> loadRanges() {
        List<TimeRange> result = new ArrayList<TimeRange>();
        String json = prefs.getString(KEY_RANGES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(TimeRange.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data - treat as no ranges rather than crash.
        }
        return result;
    }

    public void saveRanges(List<TimeRange> ranges) {
        JSONArray arr = new JSONArray();
        try {
            for (TimeRange r : ranges) {
                arr.put(r.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_RANGES, arr.toString()).apply();
    }

    public boolean hasAnyRangeSaved() {
        return !loadRanges().isEmpty();
    }

    public static int currentMinutesOfDay() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static int currentDayOfWeek() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.DAY_OF_WEEK);
    }

    /** True if we are currently inside one of the saved ranges (i.e. things are locked). */
    public boolean isCurrentlyLocked() {
        List<TimeRange> ranges = loadRanges();
        if (ranges.isEmpty()) {
            // Never configured yet - stay open so setup is possible at all.
            return false;
        }
        int nowMinutes = currentMinutesOfDay();
        int nowDay = currentDayOfWeek();
        for (TimeRange r : ranges) {
            if (r.contains(nowMinutes, nowDay)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Editing (this schedule, Blocks, apps within Blocks) is only allowed
     * while not locked - OR while far enough from a configured home
     * location that Lock Schedule doesn't apply at all right now (see
     * HomeLocationChecker). Deliberately NOT used by the anti-tamper
     * checks in BlockEnforcer (debugging-features lock, bootstrap-tools
     * lock) or by changing an already-set home location itself - those
     * stay governed by the true schedule via isCurrentlyLocked() directly,
     * regardless of location, so this override can never be used to
     * un-protect the app or redefine what "home" means while away.
     */
    public boolean isEditingAllowed() {
        return !isCurrentlyLocked() || HomeLocationChecker.isFarFromHome(context);
    }
}
