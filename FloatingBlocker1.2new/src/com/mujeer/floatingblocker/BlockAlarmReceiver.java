package com.mujeer.floatingblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at each scheduled Block transition (a start or end time). Just re-applies and reschedules. */
public class BlockAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        BlockEnforcer.reapplyAndReschedule(context);
    }
}
