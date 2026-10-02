package com.mujeer.floatingblocker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A lighter-weight sibling of Alarm: same recurring time+days triggers,
 * same missed-occurrence punishment (widening the chosen Blocks), but
 * nothing rings and there's no barcode scan to dismiss it. Firing just
 * posts a notification reminder; the only thing that resolves it is
 * actually opening this app at all (see ConfirmationPunisher.
 * confirmAllPending, called from MainActivity.onResume) within
 * CONFIRM_MINUTES of it firing.
 */
public class Confirmation {

    public static final int CONFIRM_MINUTES = 10;

    public String id;
    public String name;
    public boolean enabled = true;
    public List<AlarmTrigger> triggers = new ArrayList<AlarmTrigger>();
    public Set<String> affectedBlockIds = new LinkedHashSet<String>();

    /** Next absolute time (millis) any of this Confirmation's triggers fires strictly after afterMillis. -1 if none. */
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

    // Same OEM early-delivery quirk Alarm.java guards against (see its own
    // comment for the full explanation) - a Confirmation's trigger can just
    // as easily be delivered a few minutes early on this device, with the
    // same two failure modes (a phantom second firing, and a false "missed"
    // punishment) if the firing time were used as-is instead of snapped
    // back to the trigger's true scheduled time.
    public static final long EARLY_DELIVERY_TOLERANCE_MILLIS = 5 * 60L * 1000L;

    /**
     * Best-effort nominal trigger time for a firing that actually happened
     * at actualMillis - see Alarm.nominalOccurrenceNear, same logic.
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
        JSONArray blockArr = new JSONArray();
        for (String id : affectedBlockIds) blockArr.put(id);
        o.put("blocks", blockArr);
        return o;
    }

    public static Confirmation fromJson(JSONObject o) throws JSONException {
        Confirmation c = new Confirmation();
        c.id = o.getString("id");
        c.name = o.optString("name", "");
        c.enabled = o.optBoolean("enabled", true);
        JSONArray triggerArr = o.optJSONArray("triggers");
        if (triggerArr != null) {
            for (int i = 0; i < triggerArr.length(); i++) {
                c.triggers.add(AlarmTrigger.fromJson(triggerArr.getJSONObject(i)));
            }
        }
        JSONArray blockArr = o.optJSONArray("blocks");
        if (blockArr != null) {
            for (int i = 0; i < blockArr.length(); i++) {
                c.affectedBlockIds.add(blockArr.getString(i));
            }
        }
        return c;
    }
}
