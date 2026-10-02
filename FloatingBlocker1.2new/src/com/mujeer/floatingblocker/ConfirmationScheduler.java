package com.mujeer.floatingblocker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Schedules/cancels the AlarmManager entries behind Confirmations - both the firing itself and its 10-minute punishment deadline. Mirrors AlarmScheduler exactly. */
public class ConfirmationScheduler {

    // Same race this guards against as AlarmScheduler.SCHEDULE_LOCK - see
    // that class's comment for the full explanation.
    private static final Object SCHEDULE_LOCK = new Object();

    /** Re-schedules every enabled Confirmation's next occurrence. Call after any Confirmation is added, edited, or deleted. */
    public static void rescheduleAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        List<Confirmation> confirmations = new ConfirmationsStorage(context).loadConfirmations();
        for (Confirmation c : confirmations) {
            scheduleNextForConfirmation(context, am, c);
        }
    }

    public static void scheduleNextForConfirmation(Context context, Confirmation confirmation) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            scheduleNextForConfirmation(context, am, confirmation);
        }
    }

    private static void scheduleNextForConfirmation(Context context, AlarmManager am, Confirmation confirmation) {
        synchronized (SCHEDULE_LOCK) {
            long next = confirmation.nextOccurrenceAfter(System.currentTimeMillis());
            PendingIntent pi = firePendingIntent(context, confirmation.id);
            if (next <= 0) {
                am.cancel(pi);
                return;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /**
     * Use this instead of scheduleNextForConfirmation(context, confirmation)
     * right after a Confirmation has actually fired, passing the occurrence
     * time it fired for (see ConfirmationFireReceiver, which snaps this to
     * the trigger's true nominal time via Confirmation.nominalOccurrenceNear
     * before calling here) - same reasoning as AlarmScheduler.scheduleNextAfterFiring.
     */
    public static void scheduleNextAfterFiring(Context context, Confirmation confirmation, long occurrenceMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        synchronized (SCHEDULE_LOCK) {
            long next = confirmation.nextOccurrenceAfter(occurrenceMillis + Confirmation.EARLY_DELIVERY_TOLERANCE_MILLIS);
            PendingIntent pi = firePendingIntent(context, confirmation.id);
            if (next <= 0) {
                am.cancel(pi);
                return;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    public static void schedulePunishmentDeadline(Context context, String confirmationId, long occurrenceMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long deadline = occurrenceMillis + (Confirmation.CONFIRM_MINUTES * 60L * 1000L);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, deadlinePendingIntent(context, confirmationId, occurrenceMillis));
    }

    public static void cancelPunishmentDeadline(Context context, String confirmationId, long occurrenceMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(deadlinePendingIntent(context, confirmationId, occurrenceMillis));
        }
    }

    private static PendingIntent firePendingIntent(Context context, String confirmationId) {
        Intent intent = new Intent(context, ConfirmationFireReceiver.class);
        intent.putExtra(ConfirmationFireReceiver.EXTRA_CONFIRMATION_ID, confirmationId);
        int requestCode = ("confirm_fire_" + confirmationId).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent deadlinePendingIntent(Context context, String confirmationId, long occurrenceMillis) {
        Intent intent = new Intent(context, ConfirmationDeadlineReceiver.class);
        intent.putExtra(ConfirmationFireReceiver.EXTRA_CONFIRMATION_ID, confirmationId);
        intent.putExtra(ConfirmationFireReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceMillis);
        int requestCode = ("confirm_deadline_" + confirmationId + "_" + occurrenceMillis).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
