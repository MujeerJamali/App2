package com.mujeer.floatingblocker;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backend for the Kiosk screen's in-app "Notifications" tab (decision
 * #14) - renders real device notifications ourselves instead of exposing
 * the real system shade. Needs a ONE-TIME manual grant (Settings ->
 * Special app access -> Notification access) that Device Owner can't
 * silently grant - see decision #36/#37 for the in-app prompt that walks
 * the user through it.
 *
 * Deliberately stateless beyond what the OS already tracks: rather than
 * persisting our own copy of every notification, this just reads
 * getActiveNotifications() live off the connected service instance
 * whenever the tab wants a refresh - always reflects truth, nothing to
 * go stale. The only bookkeeping kept here is a small in-memory set of
 * locally-dismissed keys, since "dismiss" in our tab must NOT cancel the
 * real notification (decision #25) - so the real one would otherwise
 * keep reappearing on every refresh until the source app clears it
 * itself.
 */
public class KioskNotificationListenerService extends NotificationListenerService {

    private static KioskNotificationListenerService instance;
    private static final Set<String> locallyDismissedKeys = new HashSet<String>();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) {
            instance = null;
        }
    }

    /** Whether this app currently holds the one-time Notification access grant (decision #36). */
    public static boolean isConnected() {
        return instance != null;
    }

    public static class Entry {
        public final String key;
        public final String packageName;
        public final CharSequence title;
        public final CharSequence text;
        public final long postTime;

        Entry(String key, String packageName, CharSequence title, CharSequence text, long postTime) {
            this.key = key;
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.postTime = postTime;
        }
    }

    /**
     * Current notifications from apps in allowedPackages only (decision
     * #14's scope - never from a currently-blocked app), most-recent
     * first, excluding anything locally dismissed. Returns an empty list
     * if the listener isn't connected yet rather than throwing.
     */
    public static List<Entry> getCurrentEntries(Set<String> allowedPackages) {
        List<Entry> result = new ArrayList<Entry>();
        if (instance == null) {
            return result;
        }
        StatusBarNotification[] actives;
        try {
            actives = instance.getActiveNotifications();
        } catch (Exception e) {
            return result;
        }
        if (actives == null) {
            return result;
        }

        Set<String> stillPresentKeys = new HashSet<String>();
        for (StatusBarNotification sbn : actives) {
            stillPresentKeys.add(sbn.getKey());
        }
        // A locally-dismissed key whose real notification is gone too
        // (cancelled/replaced by the source app) no longer needs tracking.
        locallyDismissedKeys.retainAll(stillPresentKeys);

        for (StatusBarNotification sbn : actives) {
            if (!allowedPackages.contains(sbn.getPackageName())) {
                continue;
            }
            if (locallyDismissedKeys.contains(sbn.getKey())) {
                continue;
            }
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
            CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
            result.add(new Entry(sbn.getKey(), sbn.getPackageName(), title, text, sbn.getPostTime()));
        }

        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.postTime, a.postTime);
            }
        });
        return result;
    }

    /** Hides a notification from our own list only - does NOT cancel the real one (decision #25). */
    public static void dismissLocally(String key) {
        locallyDismissedKeys.add(key);
    }
}
