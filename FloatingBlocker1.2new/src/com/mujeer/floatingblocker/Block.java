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
