package com.mujeer.floatingblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Fires Alarm.RING_MINUTES after an Alarm started ringing, if it's still
 * ringing at that point. This is the safety-net path (AlarmRingService
 * also runs its own in-process timer to the same effect) - whichever path
 * gets there first resolves it; AlarmPunisher makes the second call a
 * no-op.
 */
public class AlarmPunishmentDeadlineReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String alarmId = intent.getStringExtra(AlarmRingReceiver.EXTRA_ALARM_ID);
        long occurrenceMillis = intent.getLongExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, 0);
        if (alarmId == null || occurrenceMillis == 0) {
            return;
        }
        List<Alarm> alarms = new AlarmsStorage(context).loadAlarms();
        for (Alarm a : alarms) {
            if (a.id.equals(alarmId)) {
                AlarmPunisher.resolveMissed(context, a, occurrenceMillis);
                return;
            }
        }
    }
}
