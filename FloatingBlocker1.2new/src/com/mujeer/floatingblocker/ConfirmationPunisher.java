package com.mujeer.floatingblocker;

import android.content.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Confirmation equivalent of AlarmPunisher - same shape, same
 * punishment logic (widening the chosen Blocks), but "dismissed" here
 * means "the app was opened", not "a barcode pair was scanned".
 */
public class ConfirmationPunisher {

    public static void markConfirmed(Context context, String confirmationId, long occurrenceMillis) {
        ConfirmationRuntimeStorage runtime = new ConfirmationRuntimeStorage(context);
        if (occurrenceMillis > runtime.getLastHandledOccurrence(confirmationId)) {
            runtime.setLastHandledOccurrence(confirmationId, occurrenceMillis);
        }
        runtime.clearPending(confirmationId);
        ConfirmationScheduler.cancelPunishmentDeadline(context, confirmationId, occurrenceMillis);
        ConfirmationNotifier.cancel(context, confirmationId);
    }

    /**
     * Called from MainActivity.onResume() - simply opening this app
     * resolves every Confirmation currently pending, no per-item action
     * needed. Returns how many were actually resolved.
     */
    public static int confirmAllPending(Context context) {
        ConfirmationRuntimeStorage runtime = new ConfirmationRuntimeStorage(context);
        int confirmed = 0;
        for (Confirmation c : new ConfirmationsStorage(context).loadConfirmations()) {
            long pendingOccurrence = runtime.getPendingOccurrence(c.id);
            if (pendingOccurrence != 0) {
                markConfirmed(context, c.id, pendingOccurrence);
                confirmed++;
            }
        }
        return confirmed;
    }

    /**
     * True if a Holiday Break that specifically lists this Confirmation
     * (via affectedConfirmationIds) is active at the given moment - same
     * per-item rule as AlarmPunisher.isSuppressedByHolidayBreak.
     */
    public static boolean isSuppressedByHolidayBreak(Context context, String confirmationId, long atMillis) {
        for (HolidayBreak h : new HolidayBreaksStorage(context).loadBreaks()) {
            if (h.isActiveNow(atMillis) && h.affectedConfirmationIds.contains(confirmationId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSuppressed(Context context, String confirmationId, long atMillis) {
        return isSuppressedByHolidayBreak(context, confirmationId, atMillis) || HomeLocationChecker.isFarFromHome(context);
    }

    public static void resolveMissed(Context context, Confirmation confirmation, long occurrenceMillis) {
        ConfirmationRuntimeStorage runtime = new ConfirmationRuntimeStorage(context);
        if (occurrenceMillis <= runtime.getLastHandledOccurrence(confirmation.id)) {
            // Already resolved - confirmed in time, or already punished by the other path racing this one.
            return;
        }
        runtime.setLastHandledOccurrence(confirmation.id, occurrenceMillis);
        runtime.clearPending(confirmation.id);
        ConfirmationScheduler.cancelPunishmentDeadline(context, confirmation.id, occurrenceMillis);
        ConfirmationNotifier.cancel(context, confirmation.id);

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
        for (String blockId : confirmation.affectedBlockIds) {
            if (blockIdsOnBreak.contains(blockId)) {
                continue; // this Block is on an active Holiday Break right now - punishment doesn't apply to it
            }
            Block block = findBlock(allBlocks, blockId);
            if (block != null) {
                punishmentStorage.applyPunishment(block, now, confirmation.punishmentMinutes * 60L * 1000L);
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
