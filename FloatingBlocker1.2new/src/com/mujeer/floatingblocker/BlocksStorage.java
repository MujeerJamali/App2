package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class BlocksStorage {

    private static final String PREFS_NAME = "floating_blocker_blocks_prefs";
    private static final String KEY_BLOCKS = "blocks_json";

    private final SharedPreferences prefs;

    public BlocksStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public List<Block> loadBlocks() {
        List<Block> result = new ArrayList<Block>();
        String json = prefs.getString(KEY_BLOCKS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(Block.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data - treat as no blocks rather than crash.
        }
        return result;
    }

    public void saveBlocks(List<Block> blocks) {
        JSONArray arr = new JSONArray();
        try {
            for (Block b : blocks) {
                arr.put(b.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_BLOCKS, arr.toString()).apply();
    }
}
