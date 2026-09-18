package com.mujeer.floatingblocker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Schedules/cancels the AlarmManager entries behind Alarms - both the ring itself and its 10-minute punishment deadline. */
public class AlarmScheduler {

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
        long next = alarm.nextOccurrenceAfter(System.currentTimeMillis());
        PendingIntent pi = ringPendingIntent(context, alarm.id);
        if (next <= 0) {
            am.cancel(pi);
            return;
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
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
