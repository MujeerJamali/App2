package com.mujeer.floatingblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Posts/cancels the plain reminder notification behind a Confirmation
 * firing - deliberately NOT a full-screen takeover like Alarm's ring
 * (AlarmRingService/AlarmRingActivity): no sound, no vibration, no
 * lock-screen interruption. Tapping it just opens this app, which is all
 * that's needed to resolve it (see ConfirmationPunisher.confirmAllPending).
 */
public class ConfirmationNotifier {

    private static final String CHANNEL_ID = "confirmation_reminder_channel";

    public static void post(Context context, String confirmationId, String confirmationName) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        context.getString(R.string.confirmation_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                }
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }
            builder.setContentTitle(context.getString(R.string.confirmation_notification_title, confirmationName))
                    .setContentText(context.getString(R.string.confirmation_notification_text))
                    .setSmallIcon(android.R.drawable.ic_menu_send)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);
            if (nm != null) {
                nm.notify(notificationId(confirmationId), builder.build());
            }
        } catch (Exception e) {
            // Best effort - the Confirmation still counts as fired and will still be caught by the punishment deadline if never opened.
        }
    }

    public static void cancel(Context context, String confirmationId) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(notificationId(confirmationId));
            }
        } catch (Exception e) {
            // Best effort.
        }
    }

    private static int notificationId(String confirmationId) {
        return confirmationId.hashCode();
    }
}
