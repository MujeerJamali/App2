package com.mujeer.floatingblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Forces KioskSessionActivity to the foreground the moment Lock Schedule
 * transitions to locked (decisions #12/#21), even from a background
 * BroadcastReceiver context. Two attempts, same spirit as everything
 * else in this app that's "best effort" against an uncertain platform
 * restriction:
 *
 *  1. A direct startActivity() with FLAG_ACTIVITY_NEW_TASK. Device Owner
 *     apps are commonly exempted from Android's background-activity-
 *     launch restrictions, but this isn't guaranteed across every OEM/
 *     Android version combination.
 *  2. A HIGH-importance notification with setFullScreenIntent(), the
 *     exact same proven mechanism AlarmRingService already uses to
 *     reliably bring AlarmRingActivity to the foreground on this device.
 *     Android only auto-presents a full-screen intent over the keyguard
 *     or the instant the screen is unlocked (covers decision #21's
 *     "device was asleep through the transition" case reliably) - if the
 *     screen is already ON and unlocked with another app in active use,
 *     Android 10+ shows this as a plain heads-up notification instead of
 *     an automatic takeover (the exact same documented limitation
 *     AlarmRingService's own comments already note for Alarms on this
 *     device), so it becomes a tap-to-enter fallback rather than a true
 *     force in that specific case.
 *
 * Net effect: entry is reliably forced whenever the screen is off/locked
 * at the moment Lock Schedule locks (or becomes locked while already
 * off), and best-effort - not guaranteed - while the screen is on and
 * another app is actively in use. This is the one part of the whole
 * redesign that genuinely needs confirming on the real device; if
 * attempt 1 doesn't force foreground reliably in practice, the user
 * still gets a tappable notification immediately, and worst case the
 * next natural trigger (screen off/on, app switch, or the ~1 minute
 * periodic safety net already in BlockEnforcer.scheduleNextAlarm) is
 * never more than a short delay away.
 */
public class KioskForceEntry {

    private static final int NOTIFICATION_ID = 9101;
    private static final String CHANNEL_ID = "kiosk_force_entry_channel";

    public static void forceEntry(Context context) {
        Intent intent = new Intent(context, KioskSessionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Best effort - falls through to the notification below either way.
        }

        try {
            postFullScreenNotification(context, intent);
        } catch (Exception e) {
            // Best effort - the direct startActivity attempt above may have
            // already succeeded regardless.
        }
    }

    private static void postFullScreenNotification(Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, "kiosk_force_entry".hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.kiosk_notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_MAX);
        }

        builder.setContentTitle(context.getString(R.string.kiosk_notification_title))
                .setContentText(context.getString(R.string.kiosk_notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    /** Called once KioskSessionActivity is actually open, to clear the nudge notification. */
    public static void clearNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }
}
