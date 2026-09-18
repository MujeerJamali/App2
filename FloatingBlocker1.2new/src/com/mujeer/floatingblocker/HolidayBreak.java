package com.mujeer.floatingblocker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A one-time (non-recurring) break: a specific start date+time through a
 * specific end date+time, during which the selected Blocks are paused -
 * a scheduled-ahead-of-time version of the Pause button, but scoped to
 * only the Blocks chosen and only for that one window.
 */
public class HolidayBreak {

    public String id;
    public String name;
    public long startMillis;
    public long endMillis;
    public Set<String> affectedBlockIds = new LinkedHashSet<String>();

    public boolean isActiveNow(long nowMillis) {
        return nowMillis >= startMillis && nowMillis < endMillis;
    }

    public String format() {
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault());
        return fmt.format(new Date(startMillis)) + "  to  " + fmt.format(new Date(endMillis));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("start", startMillis);
        o.put("end", endMillis);
        JSONArray arr = new JSONArray();
        for (String blockId : affectedBlockIds) arr.put(blockId);
        o.put("blocks", arr);
        return o;
    }

    public static HolidayBreak fromJson(JSONObject o) throws JSONException {
        HolidayBreak h = new HolidayBreak();
        h.id = o.getString("id");
        h.name = o.optString("name", "");
        h.startMillis = o.getLong("start");
        h.endMillis = o.getLong("end");
        JSONArray arr = o.optJSONArray("blocks");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                h.affectedBlockIds.add(arr.getString(i));
            }
        }
        return h;
    }
}
