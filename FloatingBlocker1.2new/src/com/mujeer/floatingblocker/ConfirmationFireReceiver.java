package com.mujeer.floatingblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Fires exactly when a Confirmation's trigger time arrives. Posts the reminder notification, then reschedules this Confirmation's next occurrence. Mirrors AlarmRingReceiver, minus the ring. */
public class ConfirmationFireReceiver extends BroadcastReceiver {

    public static final String EXTRA_CONFIRMATION_ID = "confirmation_id";
    public static final String EXTRA_OCCURRENCE_MILLIS = "occurrence_millis";

    @Override
    public void onReceive(Context context, Intent intent) {
        String confirmationId = intent.getStringExtra(EXTRA_CONFIRMATION_ID);
        if (confirmationId == null) {
            return;
        }
        List<Confirmation> confirmations = new ConfirmationsStorage(context).loadConfirmations();
        Confirmation confirmation = null;
        for (Confirmation c : confirmations) {
            if (c.id.equals(confirmationId)) {
                confirmation = c;
                break;
            }
        }
        if (confirmation == null || !confirmation.enabled) {
            return;
        }

        // Same early-delivery snap as AlarmRingReceiver - see
        // Confirmation.nominalOccurrenceNear for why.
        long occurrenceMillis = confirmation.nominalOccurrenceNear(System.currentTimeMillis());

        if (ConfirmationPunisher.isSuppressed(context, confirmationId, occurrenceMillis)) {
            // A Holiday Break covering this Confirmation is active right
            // now, or the phone is far enough from a configured home
            // location - either way, no notification, and not treated as
            // missed either, so nothing gets punished once things return
            // to normal and the catch-up scan looks back at it.
            new ConfirmationRuntimeStorage(context).setLastHandledOccurrence(confirmationId, occurrenceMillis);
            ConfirmationScheduler.scheduleNextAfterFiring(context, confirmation, occurrenceMillis);
            return;
        }

        new ConfirmationRuntimeStorage(context).setPendingOccurrence(confirmationId, occurrenceMillis);
        ConfirmationNotifier.post(context, confirmationId, confirmation.name);

        ConfirmationScheduler.schedulePunishmentDeadline(context, confirmationId, occurrenceMillis);
        ConfirmationScheduler.scheduleNextAfterFiring(context, confirmation, occurrenceMillis);
    }
}
