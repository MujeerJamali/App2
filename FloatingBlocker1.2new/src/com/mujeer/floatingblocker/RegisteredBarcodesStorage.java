package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class RegisteredBarcodesStorage {

    private static final String PREFS_NAME = "floating_blocker_barcodes_prefs";
    private static final String KEY_BARCODES = "barcodes_json";

    private final SharedPreferences prefs;

    public RegisteredBarcodesStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public List<RegisteredBarcode> loadBarcodes() {
        List<RegisteredBarcode> result = new ArrayList<RegisteredBarcode>();
        String json = prefs.getString(KEY_BARCODES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(RegisteredBarcode.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data - treat as no barcodes rather than crash.
        }
        return result;
    }

    public void saveBarcodes(List<RegisteredBarcode> barcodes) {
        JSONArray arr = new JSONArray();
        try {
            for (RegisteredBarcode b : barcodes) {
                arr.put(b.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_BARCODES, arr.toString()).apply();
    }
}
