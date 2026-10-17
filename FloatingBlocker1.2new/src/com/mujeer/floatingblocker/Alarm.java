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

    // Some OEM battery-management stacks (confirmed on this device) can
    // deliver an "exact" AlarmManager alarm a few minutes EARLY. Treating
    // that early actual firing time as "the occurrence" breaks two things:
    // (1) rescheduling from it makes nextOccurrenceAfter() see the SAME
    // nominal trigger as still upcoming, causing a phantom second ring a
    // few minutes later, and (2) recording that early time as handled still
    // leaves it BEFORE the nominal trigger time, so a later catch-up scan
    // sees the nominal time as a distinct, still-unhandled occurrence and
    // wrongly punishes it - even though it already rang and was dealt with,
    // just a couple of minutes ahead of schedule. Snapping the firing back
    // to its true scheduled time fixes both problems at the source.
    public static final long EARLY_DELIVERY_TOLERANCE_MILLIS = 5 * 60L * 1000L;

    /**
     * Best-effort nominal trigger time for a firing that actually happened
     * at actualMillis. If a trigger was scheduled within
     * EARLY_DELIVERY_TOLERANCE_MILLIS before actualMillis, returns THAT
     * scheduled time; otherwise falls back to actualMillis itself (nothing
     * nearby to snap to).
     */
    public long nominalOccurrenceNear(long actualMillis) {
        long candidate = nextOccurrenceAfter(actualMillis - EARLY_DELIVERY_TOLERANCE_MILLIS - 1);
        if (candidate > 0 && candidate <= actualMillis + EARLY_DELIVERY_TOLERANCE_MILLIS) {
            return candidate;
        }
        return actualMillis;
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
