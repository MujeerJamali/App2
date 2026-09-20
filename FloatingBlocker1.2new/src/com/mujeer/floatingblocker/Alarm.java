package com.mujeer.floatingblocker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A wake-up alarm that can ONLY be silenced by scanning BOTH barcodes of
 * one of its chosen pairs, one right after the other within a short
 * window (see AlarmRingActivity.PAIR_SCAN_WINDOW_MILLIS) - no snooze, no
 * back button, no other way out. Missing it (no valid pair scan within
 * RING_MINUTES of it firing) punishes the chosen Blocks by temporarily
 * widening their next occurrence - see BlockPunishmentStorage and
 * BlockEnforcer.
 */
public class Alarm {

    public static final int RING_MINUTES = 10;

    public String id;
    public String name;
    public boolean enabled = true;
    public List<AlarmTrigger> triggers = new ArrayList<AlarmTrigger>();
    public List<BarcodePair> barcodePairs = new ArrayList<BarcodePair>();
    public Set<String> affectedBlockIds = new LinkedHashSet<String>();

    /** Next absolute time (millis) any of this alarm's triggers fires strictly after afterMillis. -1 if none. */
    public long nextOccurrenceAfter(long afterMillis) {
        if (!enabled) {
            return -1;
        }
        long best = -1;
        for (AlarmTrigger t : triggers) {
            long candidate = t.nextOccurrenceAfter(afterMillis);
            if (candidate > 0 && (best == -1 || candidate < best)) {
                best = candidate;
            }
        }
        return best;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("enabled", enabled);
        JSONArray triggerArr = new JSONArray();
        for (AlarmTrigger t : triggers) triggerArr.put(t.toJson());
        o.put("triggers", triggerArr);
        JSONArray pairArr = new JSONArray();
        for (BarcodePair p : barcodePairs) pairArr.put(p.toJson());
        o.put("barcodePairs", pairArr);
        JSONArray blockArr = new JSONArray();
        for (String id : affectedBlockIds) blockArr.put(id);
        o.put("blocks", blockArr);
        return o;
    }

    public static Alarm fromJson(JSONObject o) throws JSONException {
        Alarm a = new Alarm();
        a.id = o.getString("id");
        a.name = o.optString("name", "");
        a.enabled = o.optBoolean("enabled", true);
        JSONArray triggerArr = o.optJSONArray("triggers");
        if (triggerArr != null) {
            for (int i = 0; i < triggerArr.length(); i++) {
                a.triggers.add(AlarmTrigger.fromJson(triggerArr.getJSONObject(i)));
            }
        }
        JSONArray pairArr = o.optJSONArray("barcodePairs");
        if (pairArr != null) {
            for (int i = 0; i < pairArr.length(); i++) {
                a.barcodePairs.add(BarcodePair.fromJson(pairArr.getJSONObject(i)));
            }
        }
        JSONArray blockArr = o.optJSONArray("blocks");
        if (blockArr != null) {
            for (int i = 0; i < blockArr.length(); i++) {
                a.affectedBlockIds.add(blockArr.getString(i));
            }
        }
        return a;
    }
}
