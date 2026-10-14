package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small per-alarm bookkeeping, separate from the user-edited Alarm config
 * itself (AlarmsStorage): which occurrence (if any) is currently ringing,
 * and the most recent occurrence that's already been resolved (dismissed in
 * time, or punished for being missed) - so a occurrence is never
 * double-handled, and so a catch-up scan (see BlockEnforcer) can tell which
 * past occurrences were never resolved at all, e.g. because the phone was
 * powered off through the whole 10-minute ring window.
 */
public class AlarmRuntimeStorage {

    private final SharedPreferences prefs;

    public AlarmRuntimeStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_alarm_runtime_prefs");
    }

    public long getLastHandledOccurrence(String alarmId) {
        return prefs.getLong("last_handled_" + alarmId, 0);
    }

    public void setLastHandledOccurrence(String alarmId, long occurrenceMillis) {
        prefs.edit().putLong("last_handled_" + alarmId, occurrenceMillis).apply();
    }

    /** 0 means no occurrence of this alarm is currently ringing/awaiting a scan. */
    public long getRingingOccurrence(String alarmId) {
        return prefs.getLong("ringing_" + alarmId, 0);
    }

    public void setRingingOccurrence(String alarmId, long occurrenceMillis) {
        prefs.edit().putLong("ringing_" + alarmId, occurrenceMillis).apply();
    }

    public void clearRinging(String alarmId) {
        prefs.edit().remove("ringing_" + alarmId).apply();
    }
}
