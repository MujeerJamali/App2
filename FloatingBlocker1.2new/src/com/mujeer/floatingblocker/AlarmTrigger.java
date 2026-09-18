package com.mujeer.floatingblocker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * One specific recurring ring moment: a single time-of-day (not a window,
 * unlike TimeRange) plus which days of the week it fires on. An Alarm can
 * have several of these, e.g. 6:00 AM on weekdays and 8:00 AM on weekends.
 */
public class AlarmTrigger {

    public int minuteOfDay;
    public Set<Integer> days = new HashSet<Integer>();

    public AlarmTrigger() {
    }

    public AlarmTrigger(int minuteOfDay, Set<Integer> days) {
        this.minuteOfDay = minuteOfDay;
        this.days = days;
    }

    /** Next absolute time (in millis) this trigger fires strictly after afterMillis. -1 if it has no days selected. */
    public long nextOccurrenceAfter(long afterMillis) {
        if (days.isEmpty()) {
            return -1;
        }
        for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(afterMillis);
            cal.add(Calendar.DAY_OF_YEAR, dayOffset);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            if (!days.contains(dow)) {
                continue;
            }
            cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
            cal.set(Calendar.MINUTE, minuteOfDay % 60);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long candidate = cal.getTimeInMillis();
            if (candidate > afterMillis) {
                return candidate;
            }
        }
        return -1;
    }

    public String formatTime() {
        int h = minuteOfDay / 60;
        int m = minuteOfDay % 60;
        String ampm = h >= 12 ? "PM" : "AM";
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return String.format("%d:%02d %s", h12, m, ampm);
    }

    public String formatDays() {
        String[] labels = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        StringBuilder sb = new StringBuilder();
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            if (days.contains(d)) {
                if (sb.length() > 0) sb.append(",");
                sb.append(labels[d]);
            }
        }
        if (sb.length() == 0) sb.append("(no days)");
        return sb.toString();
    }

    public String format() {
        return formatTime() + "  [" + formatDays() + "]";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("minute", minuteOfDay);
        JSONArray arr = new JSONArray();
        for (Integer d : days) arr.put(d);
        o.put("days", arr);
        return o;
    }

    public static AlarmTrigger fromJson(JSONObject o) throws JSONException {
        AlarmTrigger t = new AlarmTrigger();
        t.minuteOfDay = o.getInt("minute");
        Set<Integer> days = new HashSet<Integer>();
        JSONArray arr = o.optJSONArray("days");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                days.add(arr.getInt(i));
            }
        }
        t.days = days;
        return t;
    }
}
