package com.mujeer.floatingblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Fires Confirmation.CONFIRM_MINUTES after a Confirmation's notification
 * was posted, if it's still pending at that point. Mirrors
 * AlarmPunishmentDeadlineReceiver - there's no in-process timer to race
 * against here (no ring service), so this is the only path that resolves
 * a missed Confirmation.
 */
public class ConfirmationDeadlineReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String confirmationId = intent.getStringExtra(ConfirmationFireReceiver.EXTRA_CONFIRMATION_ID);
        long occurrenceMillis = intent.getLongExtra(ConfirmationFireReceiver.EXTRA_OCCURRENCE_MILLIS, 0);
        if (confirmationId == null || occurrenceMillis == 0) {
            return;
        }
        List<Confirmation> confirmations = new ConfirmationsStorage(context).loadConfirmations();
        for (Confirmation c : confirmations) {
            if (c.id.equals(confirmationId)) {
                ConfirmationPunisher.resolveMissed(context, c, occurrenceMillis);
                return;
            }
        }
    }
}
