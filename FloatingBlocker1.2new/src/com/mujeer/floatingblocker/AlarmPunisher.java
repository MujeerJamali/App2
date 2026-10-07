package com.mujeer.floatingblocker;

import android.content.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single place that decides what happens to one Alarm occurrence:
 * either it was dismissed in time (markDismissed) or it wasn't
 * (resolveMissed, which applies the Block-widening punishment). Both the
 * live 10-minute deadline (AlarmPunishmentDeadlineReceiver /
 * AlarmRingService's own timer) and the catch-up scan for occurrences
 * missed while the phone was off (BlockEnforcer) funnel through here, and
 * both paths are safe to call more than once for the same occurrence -
 * AlarmRuntimeStorage's lastHandledOccurrence guard makes resolution
 * idempotent.
 */
public class AlarmPunisher {

    public static void markDismissed(Context context, String alarmId, long occurrenceMillis) {
        AlarmRuntimeStorage runtime = new AlarmRuntimeStorage(context);
        if (occurrenceMillis > runtime.getLastHandledOccurrence(alarmId)) {
            runtime.setLastHandledOccurrence(alarmId, occurrenceMillis);
        }
        runtime.clearRinging(alarmId);
        AlarmScheduler.cancelPunishmentDeadline(context, alarmId, occurrenceMillis);
        AlarmRingService.stopIfRingingFor(context, alarmId, occurrenceMillis);
    }

    /**
     * Emergency, one-time-only escape hatch: force-stops every Alarm that's
     * currently ringing (sound, vibration, the full-screen ring activity)
     * WITHOUT scanning a barcode - resolved as dismissed, not missed, so it
     * doesn't punish anything either. Meant for a ring that's genuinely
     * stuck (e.g. lost barcodes, a broken camera) with no other way out -
     * see MainActivity's Stop All Ringing Alarms button, which is the only
     * caller and enforces the one-time-only part via
     * AlarmRuntimeStorage.hasUsedOneTimeStopAllRinging(). Returns how many
     * Alarms were actually stopped.
     */
    public static int stopAllRinging(Context context) {
        AlarmRuntimeStorage runtime = new AlarmRuntimeStorage(context);
        int stopped = 0;
        for (Alarm alarm : new AlarmsStorage(context).loadAlarms()) {
            long ringingOccurrence = runtime.getRingingOccurrence(alarm.id);
            if (ringingOccurrence != 0) {
                markDismissed(context, alarm.id, ringingOccurrence);
                stopped++;
            }
        }
        return stopped;
    }

    /**
     * True if a Holiday Break that specifically lists this Alarm (via
     * affectedAlarmIds) is active at the given moment - a Break that's
     * active but doesn't list this Alarm has no effect on it, exactly
     * like a Break not listing a given Block leaves that Block enforced.
     * An Alarm never rings while this is true (see AlarmRingReceiver and
     * BlockEnforcer.checkForMissedAlarms): being listed on an active Break
     * means "leave me alone during this window", and that includes not
     * being woken up or forced to scan a barcode at all, not just not
     * being punished.
     */
    public static boolean isSuppressedByHolidayBreak(Context context, String alarmId, long atMillis) {
        for (HolidayBreak h : new HolidayBreaksStorage(context).loadBreaks()) {
            if (h.isActiveNow(atMillis) && h.affectedAlarmIds.contains(alarmId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything that stops an Alarm from ringing at all: being listed on
     * an active Holiday Break at the given time, OR being far enough from
     * a configured home location that restrictions don't apply right now
     * (see HomeLocationChecker). Unlike the Holiday Break check, the
     * location check has no history to look back on - it only ever
     * reflects the CURRENT position, regardless of what atMillis is, so a
     * past occurrence being resolved after the fact uses today's current
     * location as a best-effort stand-in.
     */
    public static boolean isSuppressed(Context context, String alarmId, long atMillis) {
        return isSuppressedByHolidayBreak(context, alarmId, atMillis) || HomeLocationChecker.isFarFromHome(context);
    }

    public static void resolveMissed(Context context, Alarm alarm, long occurrenceMillis) {
        AlarmRuntimeStorage runtime = new AlarmRuntimeStorage(context);
        if (occurrenceMillis <= runtime.getLastHandledOccurrence(alarm.id)) {
            // Already resolved - dismissed in time, or already punished by the other path racing this one.
            return;
        }
        runtime.setLastHandledOccurrence(alarm.id, occurrenceMillis);
        runtime.clearRinging(alarm.id);
        AlarmScheduler.cancelPunishmentDeadline(context, alarm.id, occurrenceMillis);
        AlarmRingService.stopIfRingingFor(context, alarm.id, occurrenceMillis);

        long now = System.currentTimeMillis();
        boolean currentlyUnlocked = !new LockScheduleStorage(context).isCurrentlyLocked();
        if (currentlyUnlocked || HomeLocationChecker.isFarFromHome(context)) {
            return; // no punishment while the schedule is currently unlocked, or while far enough from home
        }

        Set<String> blockIdsOnBreak = new HashSet<String>();
        for (HolidayBreak h : new HolidayBreaksStorage(context).loadBreaks()) {
            if (h.isActiveNow(now)) {
                blockIdsOnBreak.addAll(h.affectedBlockIds);
            }
        }

        BlockPunishmentStorage punishmentStorage = new BlockPunishmentStorage(context);
        BlocksStorage blocksStorage = new BlocksStorage(context);
        List<Block> allBlocks = blocksStorage.loadBlocks();
        for (String blockId : alarm.affectedBlockIds) {
            if (blockIdsOnBreak.contains(blockId)) {
                continue; // this Block is on an active Holiday Break right now - punishment doesn't apply to it
            }
            Block block = findBlock(allBlocks, blockId);
            if (block != null) {
                punishmentStorage.applyPunishment(block, now);
            }
        }
    }

    private static Block findBlock(List<Block> blocks, String id) {
        for (Block b : blocks) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }
}
