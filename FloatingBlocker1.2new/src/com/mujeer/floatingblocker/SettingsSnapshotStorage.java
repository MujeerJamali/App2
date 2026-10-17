package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Snapshots everything Lock Schedule normally protects - Blocks, the
 * schedule itself, Holiday Breaks, Blocked Websites, Registered Barcodes,
 * Alarms, and the Blocks-paused toggle - the moment the location override
 * activates (see BlockEnforcer.applyHomeLocationTransition), and restores
 * it the moment the override deactivates. So any change made during that
 * "unlocked because far from home" window is temporary: it reverts the
 * instant you're back in range, exactly as if it had never happened. The
 * whole point of the override is to not be stuck locked out while away -
 * not to let changes made in that window stick without ever actually
 * passing through a real unlocked period at home.
 *
 * Goes through each storage class's own public load/save methods and each
 * model's own toJson/fromJson, rather than reaching into their private
 * SharedPreferences keys directly.
 */
public class SettingsSnapshotStorage {

    private static final String PREFS_NAME = "floating_blocker_settings_snapshot_prefs";
    private static final String KEY_HAS_SNAPSHOT = "has_snapshot";
    private static final String KEY_BLOCKS = "blocks_json";
    private static final String KEY_LOCK_RANGES = "lock_ranges_json";
    private static final String KEY_HOLIDAY_BREAKS = "holiday_breaks_json";
    private static final String KEY_WEBSITES = "websites_json";
    private static final String KEY_BARCODES = "barcodes_json";
    private static final String KEY_ALARMS = "alarms_json";
    private static final String KEY_BLOCKS_PAUSED = "blocks_paused";

    public static boolean hasSnapshot(Context context) {
        return prefs(context).getBoolean(KEY_HAS_SNAPSHOT, false);
    }

    public static void saveSnapshot(Context context) {
        try {
            JSONArray blocksArr = new JSONArray();
            for (Block b : new BlocksStorage(context).loadBlocks()) {
                blocksArr.put(b.toJson());
            }

            JSONArray rangesArr = new JSONArray();
            for (TimeRange r : new LockScheduleStorage(context).loadRanges()) {
                rangesArr.put(r.toJson());
            }

            JSONArray breaksArr = new JSONArray();
            for (HolidayBreak h : new HolidayBreaksStorage(context).loadBreaks()) {
                breaksArr.put(h.toJson());
            }

            JSONArray websitesArr = new JSONArray();
            for (String domain : new BlockedWebsitesStorage(context).loadDomains()) {
                websitesArr.put(domain);
            }

            JSONArray barcodesArr = new JSONArray();
            for (RegisteredBarcode b : new RegisteredBarcodesStorage(context).loadBarcodes()) {
                barcodesArr.put(b.toJson());
            }

            JSONArray alarmsArr = new JSONArray();
            for (Alarm a : new AlarmsStorage(context).loadAlarms()) {
                alarmsArr.put(a.toJson());
            }

            prefs(context).edit()
                    .putBoolean(KEY_HAS_SNAPSHOT, true)
                    .putString(KEY_BLOCKS, blocksArr.toString())
                    .putString(KEY_LOCK_RANGES, rangesArr.toString())
                    .putString(KEY_HOLIDAY_BREAKS, breaksArr.toString())
                    .putString(KEY_WEBSITES, websitesArr.toString())
                    .putString(KEY_BARCODES, barcodesArr.toString())
                    .putString(KEY_ALARMS, alarmsArr.toString())
                    .putBoolean(KEY_BLOCKS_PAUSED, new BlocksPauseStorage(context).isPaused())
                    .apply();
        } catch (JSONException e) {
            // Best effort - if this fails, there's simply nothing for restore to work from later.
        }
    }

    public static void restoreSnapshot(Context context) {
        if (!hasSnapshot(context)) {
            return;
        }
        SharedPreferences p = prefs(context);
        try {
            List<Block> blocks = new ArrayList<Block>();
            JSONArray blocksArr = new JSONArray(p.getString(KEY_BLOCKS, "[]"));
            for (int i = 0; i < blocksArr.length(); i++) {
                blocks.add(Block.fromJson(blocksArr.getJSONObject(i)));
            }
            new BlocksStorage(context).saveBlocks(blocks);

            List<TimeRange> ranges = new ArrayList<TimeRange>();
            JSONArray rangesArr = new JSONArray(p.getString(KEY_LOCK_RANGES, "[]"));
            for (int i = 0; i < rangesArr.length(); i++) {
                ranges.add(TimeRange.fromJson(rangesArr.getJSONObject(i)));
            }
            new LockScheduleStorage(context).saveRanges(ranges);

            List<HolidayBreak> breaks = new ArrayList<HolidayBreak>();
            JSONArray breaksArr = new JSONArray(p.getString(KEY_HOLIDAY_BREAKS, "[]"));
            for (int i = 0; i < breaksArr.length(); i++) {
                breaks.add(HolidayBreak.fromJson(breaksArr.getJSONObject(i)));
            }
            new HolidayBreaksStorage(context).saveBreaks(breaks);

            Set<String> domains = new HashSet<String>();
            JSONArray websitesArr = new JSONArray(p.getString(KEY_WEBSITES, "[]"));
            for (int i = 0; i < websitesArr.length(); i++) {
                domains.add(websitesArr.getString(i));
            }
            new BlockedWebsitesStorage(context).saveDomains(domains);

            List<RegisteredBarcode> barcodes = new ArrayList<RegisteredBarcode>();
            JSONArray barcodesArr = new JSONArray(p.getString(KEY_BARCODES, "[]"));
            for (int i = 0; i < barcodesArr.length(); i++) {
                barcodes.add(RegisteredBarcode.fromJson(barcodesArr.getJSONObject(i)));
            }
            new RegisteredBarcodesStorage(context).saveBarcodes(barcodes);

            List<Alarm> alarms = new ArrayList<Alarm>();
            JSONArray alarmsArr = new JSONArray(p.getString(KEY_ALARMS, "[]"));
            for (int i = 0; i < alarmsArr.length(); i++) {
                alarms.add(Alarm.fromJson(alarmsArr.getJSONObject(i)));
            }
            new AlarmsStorage(context).saveAlarms(alarms);

            new BlocksPauseStorage(context).setPaused(p.getBoolean(KEY_BLOCKS_PAUSED, false));
        } catch (JSONException e) {
            // Best effort - whatever was restored before the failure still applies.
        }

        AlarmScheduler.rescheduleAll(context);
    }

    private static SharedPreferences prefs(Context context) {
        return DeviceProtectedPrefs.get(context, PREFS_NAME);
    }
}
