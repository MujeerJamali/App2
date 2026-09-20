package com.mujeer.floatingblocker;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports/imports everything meaningful a user has configured - Blocks,
 * Lock Schedule, Holiday Breaks, Blocked Websites, Registered Barcodes,
 * Alarms (with their barcode pairs), the Blocks-paused toggle, and the
 * Home Location override - to/from a plain JSON file the user picks a
 * location for (Downloads, Google Drive, wherever), via the standard
 * Android file picker (Storage Access Framework) so no storage permission
 * is needed and the file survives this app being uninstalled.
 *
 * Deliberately NOT included:
 * - Emergency Safety's engaged/deleted-forever state - restoring
 *   "deleted forever" back to "not deleted" from an old backup would
 *   undermine the entire point of that being a one-way, irreversible
 *   choice.
 * - Runtime/operational bookkeeping (AlarmRuntimeStorage,
 *   BlockPunishmentStorage, InstalledPackagesSnapshotStorage,
 *   CrashLogStorage, the Location Override's own revert snapshot) -
 *   none of that makes sense replayed from an earlier point in time;
 *   it all rebuilds itself naturally as the app runs.
 */
public class BackupManager {

    private static final int BACKUP_FORMAT_VERSION = 1;

    public static void exportToUri(Context context, Uri uri) throws IOException, JSONException {
        JSONObject root = new JSONObject();
        root.put("backupFormatVersion", BACKUP_FORMAT_VERSION);
        root.put("exportedAtMillis", System.currentTimeMillis());

        JSONArray blocksArr = new JSONArray();
        for (Block b : new BlocksStorage(context).loadBlocks()) {
            blocksArr.put(b.toJson());
        }
        root.put("blocks", blocksArr);

        JSONArray rangesArr = new JSONArray();
        for (TimeRange r : new LockScheduleStorage(context).loadRanges()) {
            rangesArr.put(r.toJson());
        }
        root.put("lockScheduleRanges", rangesArr);

        JSONArray breaksArr = new JSONArray();
        for (HolidayBreak h : new HolidayBreaksStorage(context).loadBreaks()) {
            breaksArr.put(h.toJson());
        }
        root.put("holidayBreaks", breaksArr);

        JSONArray websitesArr = new JSONArray();
        for (String domain : new BlockedWebsitesStorage(context).loadDomains()) {
            websitesArr.put(domain);
        }
        root.put("blockedWebsites", websitesArr);

        JSONArray barcodesArr = new JSONArray();
        for (RegisteredBarcode b : new RegisteredBarcodesStorage(context).loadBarcodes()) {
            barcodesArr.put(b.toJson());
        }
        root.put("registeredBarcodes", barcodesArr);

        JSONArray alarmsArr = new JSONArray();
        for (Alarm a : new AlarmsStorage(context).loadAlarms()) {
            alarmsArr.put(a.toJson());
        }
        root.put("alarms", alarmsArr);

        root.put("blocksPaused", new BlocksPauseStorage(context).isPaused());

        HomeLocationStorage homeLocationStorage = new HomeLocationStorage(context);
        root.put("homeLocationEnabled", homeLocationStorage.isEnabled());
        root.put("homeLocationHasLocation", homeLocationStorage.hasLocation());
        if (homeLocationStorage.hasLocation()) {
            root.put("homeLocationLat", homeLocationStorage.getHomeLat());
            root.put("homeLocationLon", homeLocationStorage.getHomeLon());
        }

        ContentResolver resolver = context.getContentResolver();
        OutputStream out = resolver.openOutputStream(uri);
        if (out == null) {
            throw new IOException("Could not open output stream for " + uri);
        }
        try {
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write(root.toString(2));
            writer.flush();
        } finally {
            out.close();
        }
    }

    public static void importFromUri(Context context, Uri uri) throws IOException, JSONException {
        ContentResolver resolver = context.getContentResolver();
        InputStream in = resolver.openInputStream(uri);
        if (in == null) {
            throw new IOException("Could not open input stream for " + uri);
        }
        String content;
        try {
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            content = sb.toString();
        } finally {
            in.close();
        }

        JSONObject root = new JSONObject(content);

        List<Block> blocks = new ArrayList<Block>();
        JSONArray blocksArr = root.optJSONArray("blocks");
        if (blocksArr != null) {
            for (int i = 0; i < blocksArr.length(); i++) {
                blocks.add(Block.fromJson(blocksArr.getJSONObject(i)));
            }
        }
        new BlocksStorage(context).saveBlocks(blocks);

        List<TimeRange> ranges = new ArrayList<TimeRange>();
        JSONArray rangesArr = root.optJSONArray("lockScheduleRanges");
        if (rangesArr != null) {
            for (int i = 0; i < rangesArr.length(); i++) {
                ranges.add(TimeRange.fromJson(rangesArr.getJSONObject(i)));
            }
        }
        new LockScheduleStorage(context).saveRanges(ranges);

        List<HolidayBreak> breaks = new ArrayList<HolidayBreak>();
        JSONArray breaksArr = root.optJSONArray("holidayBreaks");
        if (breaksArr != null) {
            for (int i = 0; i < breaksArr.length(); i++) {
                breaks.add(HolidayBreak.fromJson(breaksArr.getJSONObject(i)));
            }
        }
        new HolidayBreaksStorage(context).saveBreaks(breaks);

        Set<String> domains = new HashSet<String>();
        JSONArray websitesArr = root.optJSONArray("blockedWebsites");
        if (websitesArr != null) {
            for (int i = 0; i < websitesArr.length(); i++) {
                domains.add(websitesArr.getString(i));
            }
        }
        new BlockedWebsitesStorage(context).saveDomains(domains);

        List<RegisteredBarcode> barcodes = new ArrayList<RegisteredBarcode>();
        JSONArray barcodesArr = root.optJSONArray("registeredBarcodes");
        if (barcodesArr != null) {
            for (int i = 0; i < barcodesArr.length(); i++) {
                barcodes.add(RegisteredBarcode.fromJson(barcodesArr.getJSONObject(i)));
            }
        }
        new RegisteredBarcodesStorage(context).saveBarcodes(barcodes);

        List<Alarm> alarms = new ArrayList<Alarm>();
        JSONArray alarmsArr = root.optJSONArray("alarms");
        if (alarmsArr != null) {
            for (int i = 0; i < alarmsArr.length(); i++) {
                alarms.add(Alarm.fromJson(alarmsArr.getJSONObject(i)));
            }
        }
        new AlarmsStorage(context).saveAlarms(alarms);

        new BlocksPauseStorage(context).setPaused(root.optBoolean("blocksPaused", false));

        HomeLocationStorage homeLocationStorage = new HomeLocationStorage(context);
        homeLocationStorage.setEnabled(root.optBoolean("homeLocationEnabled", false));
        if (root.optBoolean("homeLocationHasLocation", false)) {
            homeLocationStorage.setHomeLocation(
                    root.optDouble("homeLocationLat", 0),
                    root.optDouble("homeLocationLon", 0));
        }

        BlockEnforcer.reapplyAndReschedule(context);
        AlarmScheduler.rescheduleAll(context);
    }
}
