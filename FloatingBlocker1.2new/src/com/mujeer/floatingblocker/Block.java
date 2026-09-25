package com.mujeer.floatingblocker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A "Block": a named set of recurring time ranges, plus a list of app
 * package names that are BLOCKED. While ANY of its time ranges is active
 * (right day+time), those specific apps get closed - everything else on
 * the phone is left alone.
 */
public class Block {

    public String id;
    public String name;
    public List<TimeRange> ranges = new ArrayList<TimeRange>();
    public Set<String> blockedPackages = new LinkedHashSet<String>();

    public boolean isActiveNow(int nowMinutes, int nowDay) {
        for (TimeRange r : ranges) {
            if (r.contains(nowMinutes, nowDay)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Absolute [start, end] millis of whichever occurrence of this Block is
     * most relevant right now: the currently-active one if one of its
     * ranges is active this instant, otherwise the soonest upcoming one.
     * Used only for Alarm punishment (BlockPunishmentStorage) to know
     * exactly which occurrence to widen. Returns null if this Block has no
     * ranges with any days selected at all.
     */
    public long[] currentOrNextOccurrence(long nowMillis) {
        long[] best = null;
        boolean bestIsCurrentlyActive = false;
        for (TimeRange r : ranges) {
            if (r.days.isEmpty() || r.startMinute == r.endMinute) {
                continue;
            }
            for (int dayOffset = -1; dayOffset <= 7; dayOffset++) {
                Calendar dayCal = Calendar.getInstance();
                dayCal.setTimeInMillis(nowMillis);
                dayCal.add(Calendar.DAY_OF_YEAR, dayOffset);
                int dow = dayCal.get(Calendar.DAY_OF_WEEK);
                if (!r.days.contains(dow)) {
                    continue;
                }
                long startAbs = atMinuteOfDay(dayCal, r.startMinute);
                boolean overnight = r.startMinute >= r.endMinute;
                Calendar endDayCal = dayCal;
                if (overnight) {
                    endDayCal = (Calendar) dayCal.clone();
                    endDayCal.add(Calendar.DAY_OF_YEAR, 1);
                }
                long endAbs = atMinuteOfDay(endDayCal, r.endMinute);

                boolean currentlyActive = nowMillis >= startAbs && nowMillis < endAbs;
                boolean upcoming = startAbs > nowMillis;
                if (!currentlyActive && !upcoming) {
                    continue;
                }
                if (currentlyActive && !bestIsCurrentlyActive) {
                    best = new long[]{startAbs, endAbs};
                    bestIsCurrentlyActive = true;
                } else if (currentlyActive == bestIsCurrentlyActive) {
                    if (best == null || startAbs < best[0]) {
                        best = new long[]{startAbs, endAbs};
                    }
                }
            }
        }
        return best;
    }

    private static long atMinuteOfDay(Calendar dayCal, int minuteOfDay) {
        Calendar c = (Calendar) dayCal.clone();
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        JSONArray rangeArr = new JSONArray();
        for (TimeRange r : ranges) rangeArr.put(r.toJson());
        o.put("ranges", rangeArr);
        JSONArray appArr = new JSONArray();
        for (String pkg : blockedPackages) appArr.put(pkg);
        o.put("apps", appArr);
        return o;
    }

    public static Block fromJson(JSONObject o) throws JSONException {
        Block b = new Block();
        b.id = o.getString("id");
        b.name = o.getString("name");
        JSONArray rangeArr = o.optJSONArray("ranges");
        if (rangeArr != null) {
            for (int i = 0; i < rangeArr.length(); i++) {
                b.ranges.add(TimeRange.fromJson(rangeArr.getJSONObject(i)));
            }
        }
        JSONArray appArr = o.optJSONArray("apps");
        if (appArr != null) {
            for (int i = 0; i < appArr.length(); i++) {
                b.blockedPackages.add(appArr.getString(i));
            }
        }
        return b;
    }
}
