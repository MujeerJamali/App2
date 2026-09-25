package com.mujeer.floatingblocker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Schedules/cancels the AlarmManager safety-net behind a Kiosk Mode
 * session. KioskSessionActivity ends the session itself via its own
 * in-process countdown - this is the backstop for the case where that
 * Activity dies mid-session (crash, low-memory kill) before it gets the
 * chance, mirroring how AlarmPunishmentDeadlineReceiver backstops
 * AlarmRingService's in-process timer.
 */
public class KioskScheduler {

    public static void scheduleTimeoutFallback(Context context, long endMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pendingIntent(context));
    }

    public static void cancelTimeoutFallback(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pendingIntent(context));
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, KioskTimeoutReceiver.class);
        return PendingIntent.getBroadcast(context, "kiosk_timeout".hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
