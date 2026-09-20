package com.mujeer.floatingblocker;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Two registered barcodes that must BOTH be scanned, one after the other
 * within a short window (see AlarmRingActivity), to dismiss an Alarm.
 * Order doesn't matter - scanning barcodeIdB then barcodeIdA completes
 * the pair just as well as A then B.
 */
public class BarcodePair {

    public String id;
    public String barcodeIdA;
    public String barcodeIdB;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("a", barcodeIdA);
        o.put("b", barcodeIdB);
        return o;
    }

    public static BarcodePair fromJson(JSONObject o) throws JSONException {
        BarcodePair p = new BarcodePair();
        p.id = o.getString("id");
        p.barcodeIdA = o.getString("a");
        p.barcodeIdB = o.getString("b");
        return p;
    }
}
