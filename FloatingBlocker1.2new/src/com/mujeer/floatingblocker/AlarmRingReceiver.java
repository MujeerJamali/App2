package com.mujeer.floatingblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

/** Fires exactly when an Alarm's trigger time arrives. Starts the ring service/screen, then reschedules this Alarm's next occurrence. */
public class AlarmRingReceiver extends BroadcastReceiver {

    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_OCCURRENCE_MILLIS = "occurrence_millis";

    @Override
    public void onReceive(Context context, Intent intent) {
        String alarmId = intent.getStringExtra(EXTRA_ALARM_ID);
        if (alarmId == null) {
            return;
        }
        List<Alarm> alarms = new AlarmsStorage(context).loadAlarms();
        Alarm alarm = null;
        for (Alarm a : alarms) {
            if (a.id.equals(alarmId)) {
                alarm = a;
                break;
            }
        }
        if (alarm == null || !alarm.enabled) {
            return;
        }

        // Snap to the trigger's true scheduled time in case the OS delivered
        // this broadcast a few minutes early (observed on this device) - see
        // Alarm.nominalOccurrenceNear for why using the raw delivery time
        // instead causes both a phantom second ring and false "missed"
        // punishment later. Everything below (suppression check, dismiss
        // bookkeeping, the ring screen's own identity, the deadline, and the
        // reschedule) is keyed off this nominal time, not the raw one.
        long occurrenceMillis = alarm.nominalOccurrenceNear(System.currentTimeMillis());

        if (AlarmPunisher.isSuppressed(context, occurrenceMillis)) {
            // A Holiday Break is active right now, or the phone is far
            // enough from a configured home location - either way, this
            // occurrence doesn't ring at all (no sound, no vibration, no
            // lock screen), and isn't treated as missed either, so nothing
            // gets punished once things return to normal and the catch-up
            // scan looks back at it.
            new AlarmRuntimeStorage(context).setLastHandledOccurrence(alarmId, occurrenceMillis);
            AlarmScheduler.scheduleNextAfterFiring(context, alarm, occurrenceMillis);
            return;
        }

        new AlarmRuntimeStorage(context).setRingingOccurrence(alarmId, occurrenceMillis);

        Intent serviceIntent = new Intent(context, AlarmRingService.class);
        serviceIntent.putExtra(EXTRA_ALARM_ID, alarmId);
        serviceIntent.putExtra(EXTRA_OCCURRENCE_MILLIS, occurrenceMillis);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        AlarmScheduler.schedulePunishmentDeadline(context, alarmId, occurrenceMillis);
        AlarmScheduler.scheduleNextAfterFiring(context, alarm, occurrenceMillis);
    }
}
