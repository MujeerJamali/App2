package com.mujeer.floatingblocker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Schedules/cancels the AlarmManager entries behind Alarms - both the ring itself and its 10-minute punishment deadline. */
public class AlarmScheduler {

    // Guards the compute-then-schedule sequence below against two threads
    // racing to reschedule the SAME Alarm's ring PendingIntent at once.
    // This has already caused a real spurious double-ring: a background
    // thread reads "now" just before an Alarm's exact trigger, computes
    // that trigger as still upcoming, then - if it doesn't get to actually
    // call AlarmManager until after the real trigger has already fired and
    // been correctly rescheduled to its NEXT occurrence by
    // AlarmRingReceiver (which runs on the main thread) - overwrites that
    // correct future schedule with an already-past timestamp, which
    // Android fires again almost immediately. An earlier fix moved
    // MainActivity's own direct call back to the main thread, but missed
    // an INDIRECT path: BlockEnforcer.applyNow() (still backgrounded, for
    // its slow DevicePolicyManager calls) calls
    // applyHomeLocationTransition(), which can call
    // SettingsSnapshotStorage.restoreSnapshot(), which ALSO reschedules
    // every Alarm - from that same background thread. Rather than keep
    // chasing individual call sites one at a time, this lock makes the
    // actual compute-then-schedule operation atomic regardless of which
    // thread or code path calls it, present or future.
    private static final Object SCHEDULE_LOCK = new Object();

    /** Re-schedules every enabled Alarm's next occurrence. Call after any Alarm is added, edited, or deleted. */
    public static void rescheduleAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        List<Alarm> alarms = new AlarmsStorage(context).loadAlarms();
        for (Alarm a : alarms) {
            scheduleNextForAlarm(context, am, a);
        }
    }

    public static void scheduleNextForAlarm(Context context, Alarm alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            scheduleNextForAlarm(context, am, alarm);
        }
    }

    private static void scheduleNextForAlarm(Context context, AlarmManager am, Alarm alarm) {
        synchronized (SCHEDULE_LOCK) {
            // "now" and "next" are deliberately (re)computed INSIDE the
            // lock, not passed in from outside - a thread that was
            // blocked waiting for this lock must use a FRESH read of the
            // current time once it actually gets to run, not a stale one
            // from before it was blocked, or this synchronization
            // wouldn't actually prevent the race it's here for.
            long next = alarm.nextOccurrenceAfter(System.currentTimeMillis());
            PendingIntent pi = ringPendingIntent(context, alarm.id);
            if (next <= 0) {
                am.cancel(pi);
                return;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /**
     * Use this instead of scheduleNextForAlarm(context, alarm) right after an
     * Alarm has actually fired, passing the occurrence time it fired for
     * (see AlarmRingReceiver, which snaps this to the trigger's true nominal
     * time via Alarm.nominalOccurrenceNear before calling here). Adding
     * Alarm.EARLY_DELIVERY_TOLERANCE_MILLIS on top is extra insurance: even
     * if occurrenceMillis weren't already snapped, this still won't
     * re-schedule the very occurrence that just fired.
     */
    public static void scheduleNextAfterFiring(Context context, Alarm alarm, long occurrenceMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        synchronized (SCHEDULE_LOCK) {
            long next = alarm.nextOccurrenceAfter(occurrenceMillis + Alarm.EARLY_DELIVERY_TOLERANCE_MILLIS);
            PendingIntent pi = ringPendingIntent(context, alarm.id);
            if (next <= 0) {
                am.cancel(pi);
                return;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    public static void schedulePunishmentDeadline(Context context, String alarmId, long occurrenceMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long deadline = occurrenceMillis + (Alarm.RING_MINUTES * 60L * 1000L);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, deadlinePendingIntent(context, alarmId, occurrenceMillis));
    }

    public static void cancelPunishmentDeadline(Context context, String alarmId, long occurrenceMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(deadlinePendingIntent(context, alarmId, occurrenceMillis));
        }
    }

    private static PendingIntent ringPendingIntent(Context context, String alarmId) {
        Intent intent = new Intent(context, AlarmRingReceiver.class);
        intent.putExtra(AlarmRingReceiver.EXTRA_ALARM_ID, alarmId);
        int requestCode = ("ring_" + alarmId).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent deadlinePendingIntent(Context context, String alarmId, long occurrenceMillis) {
        Intent intent = new Intent(context, AlarmPunishmentDeadlineReceiver.class);
        intent.putExtra(AlarmRingReceiver.EXTRA_ALARM_ID, alarmId);
        intent.putExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceMillis);
        int requestCode = ("deadline_" + alarmId + "_" + occurrenceMillis).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
