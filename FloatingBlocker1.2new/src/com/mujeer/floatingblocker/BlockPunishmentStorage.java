package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

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

    /** One Block's currently-stored widen window - see getAllWindows(). */
    public static class Window {
        public final String blockId;
        public final long start;
        public final long end;

        Window(String blockId, long start, long end) {
            this.blockId = blockId;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Every Block that currently has a stored widen window, regardless of
     * whether it's already started - a window can cover a not-yet-started
     * next occurrence just as easily as the current one. Used by
     * PunishmentStatusActivity to show what's actually in effect right now.
     */
    public List<Window> getAllWindows() {
        List<Window> result = new ArrayList<Window>();
        java.util.Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (!key.startsWith("start_")) {
                continue;
            }
            String blockId = key.substring("start_".length());
            long start = prefs.getLong(key, 0);
            long end = prefs.getLong("end_" + blockId, 0);
            if (end > 0) {
                result.add(new Window(blockId, start, end));
            }
        }
        return result;
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

    /**
     * A second, independent one-time Clear Current Punishment action, for
     * when the first one has already been spent but a NEW false punishment
     * shows up later (e.g. from the early-alarm-delivery bug fixed in
     * 4.74) before the fix could actually be installed. Same one-time-only
     * reasoning as hasUsedOneTimeClear/markOneTimeClearUsed above, just
     * tracked under its own flag so using the first one doesn't also use
     * this one up.
     */
    public boolean hasUsedOneTimeClear2() {
        return prefs.getBoolean("one_time_clear_used_2", false);
    }

    public void markOneTimeClearUsed2() {
        prefs.edit().putBoolean("one_time_clear_used_2", true).apply();
    }
}
