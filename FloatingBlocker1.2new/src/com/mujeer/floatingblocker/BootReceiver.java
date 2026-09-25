package com.mujeer.floatingblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Handles both boot broadcasts (LOCKED_BOOT_COMPLETED fires before first
 * unlock; BOOT_COMPLETED is a fallback). Since enforcement is now handled
 * by Device Owner directly suspending apps - not a continuously-running
 * service - all boot needs to do is catch up on anything that changed
 * while the phone was off, and schedule the next alarm going forward.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        BlockEnforcer.reapplyAndReschedule(context);
        AlarmScheduler.rescheduleAll(context);
    }
}
