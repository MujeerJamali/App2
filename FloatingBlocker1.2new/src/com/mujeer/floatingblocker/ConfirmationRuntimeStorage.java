package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small per-confirmation bookkeeping, mirroring AlarmRuntimeStorage: which
 * occurrence (if any) is currently pending confirmation, and the most
 * recent occurrence already resolved (confirmed in time, or punished for
 * being missed) - so an occurrence is never double-handled, and so a
 * catch-up scan (see BlockEnforcer) can tell which past occurrences were
 * never resolved at all, e.g. because the phone was powered off through
 * the whole confirm window.
 */
public class ConfirmationRuntimeStorage {

    private final SharedPreferences prefs;

    public ConfirmationRuntimeStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_confirmation_runtime_prefs");
    }

    public long getLastHandledOccurrence(String confirmationId) {
        return prefs.getLong("last_handled_" + confirmationId, 0);
    }

    public void setLastHandledOccurrence(String confirmationId, long occurrenceMillis) {
        prefs.edit().putLong("last_handled_" + confirmationId, occurrenceMillis).apply();
    }

    /** 0 means no occurrence of this confirmation is currently pending. */
    public long getPendingOccurrence(String confirmationId) {
        return prefs.getLong("pending_" + confirmationId, 0);
    }

    public void setPendingOccurrence(String confirmationId, long occurrenceMillis) {
        prefs.edit().putLong("pending_" + confirmationId, occurrenceMillis).apply();
    }

    public void clearPending(String confirmationId) {
        prefs.edit().remove("pending_" + confirmationId).apply();
    }
}
