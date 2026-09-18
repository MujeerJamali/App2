package com.mujeer.floatingblocker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * A single recurring time window: a start/end time (minutes since midnight)
 * plus which days of the week it applies to. Days use Calendar.SUNDAY(1)
 * .. Calendar.SATURDAY(7), same as java.util.Calendar.
 *
 * Supports overnight ranges (e.g. 22:00 -> 06:00) by wrapping past midnight.
 */
public class TimeRange {

    public int startMinute;
    public int endMinute;
    public Set<Integer> days = new HashSet<Integer>();

    public TimeRange() {
    }

    public TimeRange(int startMinute, int endMinute, Set<Integer> days) {
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.days = days;
    }

    /** True if nowMinutes falls inside this range AND nowDay is one of the selected days. */
    public boolean contains(int nowMinutes, int nowDay) {
        if (!days.contains(nowDay)) {
            return false;
        }
        if (startMinute == endMinute) {
            // Zero-length range never matches (avoid accidental "always on").
            return false;
        }
        if (startMinute < endMinute) {
            return nowMinutes >= startMinute && nowMinutes < endMinute;
        } else {
            // Overnight range wraps past midnight, e.g. 22:00 -> 06:00
            return nowMinutes >= startMinute || nowMinutes < endMinute;
        }
    }

    public String formatTime(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
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
        return formatTime(startMinute) + " - " + formatTime(endMinute) + "  [" + formatDays() + "]";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("start", startMinute);
        o.put("end", endMinute);
        JSONArray arr = new JSONArray();
        for (Integer d : days) arr.put(d);
        o.put("days", arr);
        return o;
    }

    public static TimeRange fromJson(JSONObject o) throws JSONException {
        TimeRange r = new TimeRange();
        r.startMinute = o.getInt("start");
        r.endMinute = o.getInt("end");
        Set<Integer> days = new HashSet<Integer>();
        JSONArray arr = o.optJSONArray("days");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                days.add(arr.getInt(i));
            }
        }
        r.days = days;
        return r;
    }
}
