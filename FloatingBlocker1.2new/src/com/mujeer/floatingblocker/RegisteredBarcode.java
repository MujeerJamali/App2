package com.mujeer.floatingblocker;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A barcode/QR code registered once (scanned during setup) so it can be
 * required later to dismiss an Alarm. "value" is the exact decoded text
 * ZXing returns for that code - dismissal requires scanning a code whose
 * decoded value matches exactly.
 */
public class RegisteredBarcode {

    public String id;
    public String label;
    public String value;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label);
        o.put("value", value);
        return o;
    }

    public static RegisteredBarcode fromJson(JSONObject o) throws JSONException {
        RegisteredBarcode b = new RegisteredBarcode();
        b.id = o.getString("id");
        b.label = o.optString("label", "");
        b.value = o.getString("value");
        return b;
    }
}
