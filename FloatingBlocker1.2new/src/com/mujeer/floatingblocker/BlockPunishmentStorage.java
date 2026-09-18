package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks a one-time "widen this Block's next occurrence by an hour on each
 * side" punishment per Block, for a missed Alarm (see Alarm/AlarmTrigger).
 * Deliberately does NOT touch the Block's own stored ranges - it only
 * remembers an absolute [start, end] window while it's still relevant, and
 * naturally stops mattering once "now" passes the stored end. That's what
 * makes it one-time: the next time this Block's occurrence comes around
 * after that, there's nothing left overriding it.
 */
public class BlockPunishmentStorage {

    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long DAY_MS = 24L * HOUR_MS;

    private final SharedPreferences prefs;

    public BlockPunishmentStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_block_punishment_prefs");
    }

    /**
     * Widens blockId's current-or-next occurrence (per block.currentOrNextOccurrence)
     * by 1 hour earlier on the start and 1 hour later on the end, capped so the
     * widened occurrence never spans more than 24 hours. No-op if the block has
     * no upcoming/active occurrence at all.
     */
    public void applyPunishment(Block block, long nowMillis) {
        long[] occurrence = block.currentOrNextOccurrence(nowMillis);
        if (occurrence == null) {
            return;
        }
        long widenedStart = occurrence[0] - HOUR_MS;
        long widenedEnd = occurrence[1] + HOUR_MS;
        if (widenedEnd - widenedStart > DAY_MS) {
            widenedStart = widenedEnd - DAY_MS;
        }
        prefs.edit()
                .putLong("start_" + block.id, widenedStart)
                .putLong("end_" + block.id, widenedEnd)
                .apply();
    }

    /** True if blockId currently has an active widen window covering nowMillis. */
    public boolean isWidenedActive(String blockId, long nowMillis) {
        long start = prefs.getLong("start_" + blockId, 0);
        long end = prefs.getLong("end_" + blockId, 0);
        return end > 0 && nowMillis >= start && nowMillis < end;
    }
}
