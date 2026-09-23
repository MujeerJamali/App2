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

    /**
     * Clears one specific Block's widen window, if any. Used when a
     * Holiday Break covering this Block ends - punishment can only ever
     * be applied to a Block OUTSIDE an active Break (it's explicitly
     * skipped while one covers it), so any widen state still attached to
     * a Block when its Break expires must predate that Break. Without
     * this, that leftover state would otherwise resurface the instant
     * the Break ends - suspending the Block's apps again with no
     * currently-active schedule and no new offense, which the Break
     * should already have covered.
     */
    public void clearWidenedFor(String blockId) {
        prefs.edit().remove("start_" + blockId).remove("end_" + blockId).apply();
    }

    /**
     * Clears every Block's currently-stored widen window - meant ONLY as a
     * one-time recovery action (see hasUsedOneTimeClear/markOneTimeClearUsed
     * below, and MainActivity's Clear Current Punishment button), not a
     * repeatable way to escape a deserved punishment - that would undermine
     * the entire point of the widening in the first place. Only removes the
     * start_/end_ keys, so the one-time-used flag itself (stored in this
     * same prefs file) survives this call.
     */
    public void clearAll() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("start_") || key.startsWith("end_")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /** Whether the one-time Clear Current Punishment action has already been used - it can only ever be used once. */
    public boolean hasUsedOneTimeClear() {
        return prefs.getBoolean("one_time_clear_used", false);
    }

    public void markOneTimeClearUsed() {
        prefs.edit().putBoolean("one_time_clear_used", true).apply();
    }
}
