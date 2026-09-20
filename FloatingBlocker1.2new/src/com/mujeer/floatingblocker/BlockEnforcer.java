package com.mujeer.floatingblocker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole enforcement engine, in one place. No more continuous polling -
 * apps are suspended directly at the OS level via Device Owner, and this
 * only needs to run at the exact moments something should change (a
 * Block's schedule starting/ending), woken up by AlarmManager, or right
 * after the user edits something in the app.
 *
 * Suspended apps literally cannot be opened at all while suspended -
 * Android shows its own "not available right now" message. There is no
 * detection, no back-pressing, no window to catch - the app just never
 * launches in the first place.
 *
 * Holiday Breaks (one-time, non-recurring date+time windows that pause
 * specific Blocks) are factored in here too - a Block on an active
 * Holiday Break is treated as inactive regardless of its own schedule.
 *
 * Newly installed apps are detected here too (see
 * checkForNewlyInstalledApps) via comparing installed-package snapshots
 * over time, NOT via a PACKAGE_ADDED broadcast - that broadcast cannot
 * reliably reach a manifest-registered receiver on this targetSdkVersion,
 * confirmed against Android's own documentation.
 */
public class BlockEnforcer {

    private static final String TAG = "BlockEnforcer";

    /** Re-applies the correct suspend/unsuspend state for every managed app, right now. */
    public static void applyNow(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
            return; // Device Owner isn't active - nothing we're able to enforce
        }
        ComponentName admin = new ComponentName(context, FloatingBlockerDeviceAdminReceiver.class);
        String ownPackage = context.getPackageName();

        // Breadcrumb logging through each step - if this ever hangs again,
        // whatever's the LAST line printed in LogCat before it stops
        // pinpoints exactly which step is stuck, the same way the missed-
        // alarms freeze (fixed in 4.59) was eventually found.
        Log.d(TAG, "applyNow: start");
        applyPermanentDeviceOwnerProtections(context, dpm, admin, ownPackage);
        Log.d(TAG, "applyNow: after applyPermanentDeviceOwnerProtections");
        applyLocationPermissionState(context, dpm, admin, ownPackage);
        Log.d(TAG, "applyNow: after applyLocationPermissionState");
        applyHomeLocationTransition(context);
        Log.d(TAG, "applyNow: after applyHomeLocationTransition");
        releaseStrictPrivateDnsIfLocked(context, dpm, admin);
        Log.d(TAG, "applyNow: after releaseStrictPrivateDnsIfLocked");
        applyDebuggingFeaturesLock(context, dpm, admin);
        Log.d(TAG, "applyNow: after applyDebuggingFeaturesLock");
        applyBootstrapToolsLock(context, dpm, admin);
        Log.d(TAG, "applyNow: after applyBootstrapToolsLock");
        checkForNewlyInstalledApps(context, ownPackage);
        Log.d(TAG, "applyNow: after checkForNewlyInstalledApps");
        cleanUpExpiredBreaks(new HolidayBreaksStorage(context));
        Log.d(TAG, "applyNow: after cleanUpExpiredBreaks");
        checkForMissedAlarms(context);
        Log.d(TAG, "applyNow: after checkForMissedAlarms");

        List<Block> blocks = new BlocksStorage(context).loadBlocks();
        Set<String> allManaged = new HashSet<String>();
        for (Block b : blocks) {
            allManaged.addAll(b.blockedPackages);
        }
        allManaged.remove(ownPackage);

        Set<String> desiredSuspended = computeActiveBlockedPackages(context);
        desiredSuspended.remove(ownPackage);

        Set<String> toUnsuspend = new HashSet<String>(allManaged);
        toUnsuspend.removeAll(desiredSuspended);

        if (!desiredSuspended.isEmpty()) {
            try {
                dpm.setPackagesSuspended(admin, desiredSuspended.toArray(new String[0]), true);
            } catch (Exception e) {
                // A specific package may refuse to be suspended (e.g. a protected
                // system app) - nothing more to do for that one.
            }
        }
        if (!toUnsuspend.isEmpty()) {
            try {
                dpm.setPackagesSuspended(admin, toUnsuspend.toArray(new String[0]), false);
            } catch (Exception e) {
                // Already unsuspended, or never was - fine either way.
            }
        }
        Log.d(TAG, "applyNow: after suspend/unsuspend calls");

        // Runs last, deliberately - if a user ever put one of these browser
        // packages into a Block of their own, the Block-schedule-based
        // unsuspend right above this could otherwise win the race and
        // briefly leave it unsuspended. This always has the final say.
        applyContentFilteringProtections(context, dpm, admin, ownPackage);
        Log.d(TAG, "applyNow: done");
    }

    /**
     * The set of packages that SHOULD be blocked right now, per the exact
     * same rules applyNow() uses to decide what to suspend: union of
     * currently-active Blocks' package lists, skipping any Block currently
     * on a Holiday Break, and empty entirely if Master Safety or Blocks
     * Pause is currently overriding everything. Exposed publicly so other
     * enforcement layers can check "is this package currently supposed to
     * be blocked" using the exact same logic, instead of a second,
     * possibly-inconsistent copy.
     */
    public static Set<String> computeActiveBlockedPackages(Context context) {
        List<Block> blocks = new BlocksStorage(context).loadBlocks();
        HolidayBreaksStorage holidayBreaksStorage = new HolidayBreaksStorage(context);
        List<HolidayBreak> holidayBreaks = holidayBreaksStorage.loadBreaks();
        MasterSafetyStorage safetyStorage = new MasterSafetyStorage(context);
        BlocksPauseStorage pauseStorage = new BlocksPauseStorage(context);

        long now = System.currentTimeMillis();
        Set<String> desiredSuspended = new HashSet<String>();
        boolean overridden = safetyStorage.isEngaged() || pauseStorage.isPaused()
                || pauseStorage.isTemporaryOverrideActive(now)
                || HomeLocationChecker.isFarFromHome(context);
        if (overridden) {
            return desiredSuspended;
        }
        Set<String> blockIdsOnBreak = new HashSet<String>();
        for (HolidayBreak h : holidayBreaks) {
            if (h.isActiveNow(now)) {
                blockIdsOnBreak.addAll(h.affectedBlockIds);
            }
        }

        BlockPunishmentStorage punishmentStorage = new BlockPunishmentStorage(context);
        int nowMinutes = LockScheduleStorage.currentMinutesOfDay();
        int nowDay = LockScheduleStorage.currentDayOfWeek();
        for (Block b : blocks) {
            if (blockIdsOnBreak.contains(b.id)) {
                continue; // this Block is on a Holiday Break right now - skip it
            }
            // A missed Alarm can widen this Block's current/next occurrence by
            // an hour on each side, one time only - see BlockPunishmentStorage.
            if (b.isActiveNow(nowMinutes, nowDay) || punishmentStorage.isWidenedActive(b.id, now)) {
                desiredSuspended.addAll(b.blockedPackages);
            }
        }
        return desiredSuspended;
    }

    /**
     * Catches up on any Alarm occurrence whose full 10-minute ring window
     * has already elapsed but was never resolved - most notably because the
     * phone was powered off through the whole window, so neither
     * AlarmRingReceiver nor the punishment-deadline alarm ever got to run.
     * Walks forward one occurrence at a time from each Alarm's last
     * resolved occurrence, punishing every fully-elapsed one it finds,
     * and stops as soon as it reaches one that's still within its live
     * grace period (that one is left for the normal live path to resolve).
     */
    private static void checkForMissedAlarms(Context context) {
        long now = System.currentTimeMillis();
        long ringMillis = Alarm.RING_MINUTES * 60L * 1000L;
        AlarmRuntimeStorage runtime = new AlarmRuntimeStorage(context);
        for (Alarm alarm : new AlarmsStorage(context).loadAlarms()) {
            long cursor = runtime.getLastHandledOccurrence(alarm.id);
            Log.d(TAG, "checkForMissedAlarms: alarm=" + alarm.id + " cursor=" + cursor);
            if (cursor <= 0) {
                // Never resolved even once (e.g. a brand-new Alarm that
                // hasn't had a chance to ring yet) - there's no legitimate
                // prior occurrence to catch up on, since nothing "missed"
                // before this Alarm was ever tracked. Seeding straight to
                // now avoids walking forward one occurrence at a time from
                // epoch (1970) all the way to today - which is exactly
                // what the loop below would otherwise do, treating
                // thousands of theoretical pre-creation occurrences as
                // missed and freezing the app for a very long time while
                // it wrongly punishes Blocks for alarms that never
                // actually happened.
                runtime.setLastHandledOccurrence(alarm.id, now);
                continue;
            }
            // Hard safety cap, defense-in-depth against any other edge case
            // (not just the epoch-start one already fixed above) that could
            // otherwise make this loop grind through an implausible number
            // of iterations - e.g. a corrupted/absurd stored cursor value.
            // A phone realistically never stays off long enough to need
            // anywhere close to this many catch-up occurrences for one Alarm.
            final int MAX_CATCHUP_ITERATIONS = 1000;
            int iterations = 0;
            while (true) {
                long next = alarm.nextOccurrenceAfter(cursor);
                if (next <= 0 || next > now || now < next + ringMillis) {
                    break;
                }
                if (++iterations > MAX_CATCHUP_ITERATIONS) {
                    Log.e(TAG, "checkForMissedAlarms: alarm=" + alarm.id
                            + " hit the " + MAX_CATCHUP_ITERATIONS + "-iteration safety cap - "
                            + "bailing out instead of continuing to walk forward");
                    runtime.setLastHandledOccurrence(alarm.id, now);
                    break;
                }
                // A Holiday Break active at the occurrence's own time, or
                // currently being far enough from home, means it never
                // should have rung in the first place (same rule
                // AlarmRingReceiver applies live) - not just unpunished,
                // but not counted as missed at all.
                if (AlarmPunisher.isSuppressed(context, next)) {
                    runtime.setLastHandledOccurrence(alarm.id, next);
                } else {
                    AlarmPunisher.resolveMissed(context, alarm, next);
                }
                cursor = next;
            }
        }
    }

    /**
     * One-time cleanup (idempotent, safe to run every cycle) for the old
     * "DNS Lock" feature this app used to have: strict-mode Private DNS
     * forced to family-filter-dns.cleanbrowsing.org, with the user locked
     * out of changing it via DISALLOW_CONFIG_PRIVATE_DNS. That feature is
     * gone - DnsVpnService now forwards every non-custom-blocked query to
     * that exact same CleanBrowsing resolver anyway (see UPSTREAM_DNS), so
     * it added nothing, while its strict-mode validation being dependent on
     * whatever DNS path was currently active (the VPN's own, whenever the
     * VPN is running) is what was actually causing "internet totally dead
     * while the VPN is on". Any device that had this enabled under an
     * earlier build needs it actively released, not just left alone - the
     * restriction and the strict host assignment both persist at the OS
     * level independent of this app's own code until something explicitly
     * undoes them. setGlobalPrivateDnsModeOpportunistic is the safe
     * "Automatic" mode: it still tries DNS-over-TLS to whatever the network
     * offers, but - unlike strict mode - falls back to plain DNS rather
     * than blocking resolution outright if that fails, so it can never
     * cause this specific failure again.
     */
    private static void releaseStrictPrivateDnsIfLocked(Context context, DevicePolicyManager dpm, ComponentName admin) {
        try {
            android.os.UserManager userManager = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
            if (userManager != null && userManager.hasUserRestriction(android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS)) {
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
            }
        } catch (Exception e) { /* best effort */ }
        try {
            dpm.setGlobalPrivateDnsModeOpportunistic(admin);
        } catch (Exception e) { /* best effort - if this fails, worst case Private DNS is left however the user/OS last had it */ }
    }

    /**
     * These are permanent - not tied to Lock Schedule, always applied,
     * every cycle (idempotent and self-healing if anything gets cleared
     * somehow). This is the actual point of Device Owner: making the app
     * itself un-removable and un-weakenable, which nothing else in this
     * app matters without.
     */
    private static void applyPermanentDeviceOwnerProtections(Context context, DevicePolicyManager dpm, ComponentName admin, String ownPackage) {
        try {
            dpm.setUninstallBlocked(admin, ownPackage, true);
        } catch (Exception e) { /* best effort */ }
        try {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
        } catch (Exception e) { /* best effort */ }
        try {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
        } catch (Exception e) { /* best effort */ }
        try {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
        } catch (Exception e) { /* best effort */ }
    }

    /**
     * Location access is only ever needed for the optional home-location
     * override (see HomeLocationChecker) - granted silently via Device
     * Owner (no runtime prompt needed) only while that feature is turned
     * on, and released back to the normal default otherwise, so this app
     * doesn't hold location access for no reason when the feature isn't
     * in use. ACCESS_BACKGROUND_LOCATION matters here specifically because
     * the periodic checks that need this run from a BroadcastReceiver, not
     * a foreground screen.
     */
    private static void applyLocationPermissionState(Context context, DevicePolicyManager dpm, ComponentName admin, String ownPackage) {
        boolean needed = new HomeLocationStorage(context).isEnabled();
        int state = needed ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED : DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
        try {
            dpm.setPermissionGrantState(admin, ownPackage, android.Manifest.permission.ACCESS_FINE_LOCATION, state);
        } catch (Exception e) { /* best effort */ }
        try {
            dpm.setPermissionGrantState(admin, ownPackage, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, state);
        } catch (Exception e) { /* best effort - not every OS version/OEM exposes this the same way */ }
    }

    /**
     * Detects the exact moment the location override turns on or off (see
     * HomeLocationStorage.wasOverrideActiveLastCheck) and snapshots or
     * restores everything Lock Schedule normally protects accordingly (see
     * SettingsSnapshotStorage) - so any change made while "unlocked
     * because far from home" is temporary, reverting the instant the
     * override ends. Runs early in applyNow(), before anything else in
     * this same cycle reads Blocks/Holiday Breaks/Alarms/etc., so a
     * restore takes effect immediately rather than one cycle late.
     */
    private static void applyHomeLocationTransition(Context context) {
        HomeLocationStorage storage = new HomeLocationStorage(context);
        boolean farNow = HomeLocationChecker.isFarFromHome(context);
        boolean wasFar = storage.wasOverrideActiveLastCheck();
        if (farNow && !wasFar) {
            SettingsSnapshotStorage.saveSnapshot(context);
        } else if (!farNow && wasFar) {
            SettingsSnapshotStorage.restoreSnapshot(context);
        }
        storage.setOverrideActiveLastCheck(farNow);
    }

    private static final String CHROME_PACKAGE = "com.android.chrome";

    /**
     * Known browser package names, kept as a belt-and-suspenders backup to
     * the live detection below (in case some browser's own http intent
     * filter is built unusually and doesn't get caught by that). Includes
     * Chrome's own Beta/Dev/Canary channels, since those are separate app
     * packages that would NOT receive the policy pushed to the stable
     * com.android.chrome package below.
     */
    private static final String[] KNOWN_OTHER_BROWSER_PACKAGES = {
            "org.mozilla.firefox",
            "org.mozilla.firefox.beta",
            "org.mozilla.focus",
            "org.mozilla.klar",
            "com.opera.browser",
            "com.opera.browser.beta",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.opera.touch",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.UCMobile.intl",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "com.mi.globalbrowser",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.torproject.torbrowser",
            "com.ecosia.android",
            "com.yandex.browser",
            "com.jio.web",
    };

    /**
     * Apps that are known to sometimes register themselves as able to open
     * a generic http(s) link (usually to show link previews or open pages
     * in their own embedded viewer) without actually being a standalone
     * browser - never auto-suspend these via the live detection below,
     * however it turns out to be applying: doing so could silently break
     * something the user relies on with no obvious explanation why.
     */
    private static final Set<String> BROWSER_DETECTION_EXCLUDE = new HashSet<String>(java.util.Arrays.asList(
            "com.google.android.googlequicksearchbox", // Google app / Assistant
            "com.google.android.gm",                    // Gmail
            "com.google.android.apps.docs",             // Google Drive
            "com.google.android.apps.messaging",        // Google Messages
            "com.google.android.apps.maps",             // Google Maps
            "com.android.vending"                       // Play Store
    ));

    /**
     * Adult-content blocking with none of DNS / VPN / Accessibility Service
     * / Usage Stats involved: managed policies pushed straight into Chrome
     * via setApplicationRestrictions (the same mechanism real enterprise
     * MDM apps use - Chrome itself reads and enforces these, so it keeps
     * working even inside Incognito or a tab this app never sees), plus
     * every other browser kept permanently suspended so Chrome can't just
     * be swapped out. "Every other browser" is detected live each cycle -
     * any app that resolves a plain, host-agnostic http:// link is, by
     * Android's own definition, a browser (the same mechanism behind the
     * "Open with..." chooser) - rather than relying only on a fixed list
     * of package names, so a newly installed or previously-unrecognized
     * browser from Play Store gets caught automatically too. All of this
     * is re-applied every cycle, same as the other permanent protections
     * above - cheap, and self-healing if anything ever gets cleared or a
     * new browser shows up.
     */
    private static void applyContentFilteringProtections(Context context, DevicePolicyManager dpm, ComponentName admin, String ownPackage) {
        try {
            Set<String> blockedDomains = new BlockedWebsitesStorage(context).loadDomains();
            Bundle restrictions = new Bundle();
            if (!blockedDomains.isEmpty()) {
                restrictions.putStringArray("URLBlocklist", blockedDomains.toArray(new String[0]));
            }
            restrictions.putInt("SafeSitesFilterBehavior", 1); // 1 = block mature/explicit sites
            restrictions.putBoolean("ForceGoogleSafeSearch", true);
            restrictions.putInt("ForceYouTubeRestrict", 2); // 2 = Strict restricted mode
            restrictions.putInt("IncognitoModeAvailability", 1); // 1 = disabled
            dpm.setApplicationRestrictions(admin, CHROME_PACKAGE, restrictions);
        } catch (Exception e) {
            // Best effort - Chrome may not be installed, or this OEM build may ignore these keys.
        }

        Set<String> browsersToSuspend = detectBrowserPackages(context, ownPackage);
        browsersToSuspend.addAll(java.util.Arrays.asList(KNOWN_OTHER_BROWSER_PACKAGES));
        try {
            dpm.setPackagesSuspended(admin, browsersToSuspend.toArray(new String[0]), true);
        } catch (Exception e) {
            // Best effort - fine for any package here that isn't installed.
        }
    }

    private static Set<String> detectBrowserPackages(Context context, String ownPackage) {
        Set<String> result = new HashSet<String>();
        try {
            PackageManager pm = context.getPackageManager();
            Intent probe = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com"));
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(probe, 0);
            for (ResolveInfo info : resolveInfos) {
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(ownPackage) && !pkg.equals(CHROME_PACKAGE) && !BROWSER_DETECTION_EXCLUDE.contains(pkg)) {
                    result.add(pkg);
                }
            }
        } catch (Exception e) {
            // Best effort - the known-package list above still covers the common cases either way.
        }
        return result;
    }

    /**
     * Termux and Shizuku are the specific tools that were used to grant
     * Device Owner in the first place, and could in principle be used to
     * remove it again the same way. They're auto-suspended (if ever
     * reinstalled) whenever the Lock Schedule is locked - same automatic,
     * no-button pattern as the debugging-features lock.
     */
    private static void applyBootstrapToolsLock(Context context, DevicePolicyManager dpm, ComponentName admin) {
        boolean shouldBeLocked = new LockScheduleStorage(context).isCurrentlyLocked();
        String[] tools = { "com.termux", "moe.shizuku.privileged.api" };
        try {
            dpm.setPackagesSuspended(admin, tools, shouldBeLocked);
        } catch (Exception e) { /* best effort - fine if neither is installed */ }
    }

    private static void applyDebuggingFeaturesLock(Context context, DevicePolicyManager dpm, ComponentName admin) {
        boolean shouldBeLocked = new LockScheduleStorage(context).isCurrentlyLocked();
        try {
            if (shouldBeLocked) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
            } else {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
            }
        } catch (Exception e) {
            // Best effort - if this specific restriction can't be applied, everything else still runs.
        }
    }

    /**
     * Reliable new-install detection - NOT broadcast-based. A manifest-registered
     * receiver for PACKAGE_ADDED cannot work here: Android's own official docs
     * confirm this specific broadcast is not exempted from the API 26+
     * background-execution restrictions, and our targetSdkVersion is 29. A
     * dynamically-registered receiver would only work while some part of the
     * app happens to be open, which is not reliable either since we don't run
     * a persistent background service.
     *
     * Instead, this compares "what's installed now" against "what was
     * installed last time this ran" every time the app wakes up anyway (every
     * Block/Break transition, and at minimum every ~1 minute via the
     * periodic safety net in scheduleNextAlarm, automatically throttled back
     * during genuine idle periods by Android's own Doze system). Anything new gets added to
     * every existing Block's block-list immediately.
     */
    // A real new-install event realistically adds one app, maybe two or
    // three in a burst - never dozens at once. If the diff against the
    // stored snapshot claims more than this many apps just "appeared", the
    // snapshot itself is far more likely stale or corrupted (a bad/empty
    // read at the wrong moment, JSON corruption, etc.) than that many apps
    // were genuinely installed simultaneously. This exists specifically
    // because of a real bug: right after updating this app itself, this
    // check could occasionally see a spuriously empty "previously
    // installed" snapshot, making literally every app on the phone look
    // newly installed at once - and every one of them got silently added
    // to every Block. Treating an implausibly large diff as a corrupted
    // snapshot (re-baseline silently, block nothing) instead of trusting
    // it outright closes that off regardless of what exactly corrupted it.
    private static final int MAX_PLAUSIBLE_NEW_INSTALLS_PER_CHECK = 5;

    private static void checkForNewlyInstalledApps(Context context, String ownPackage) {
        InstalledPackagesSnapshotStorage snapshotStorage = new InstalledPackagesSnapshotStorage(context);
        Set<String> currentlyInstalled = getInstalledLaunchablePackages(context, ownPackage);

        if (!snapshotStorage.hasEverSnapshotted()) {
            // First run ever - just record the baseline. Don't treat every
            // already-installed app as "newly installed".
            snapshotStorage.saveSnapshot(currentlyInstalled);
            return;
        }

        Set<String> previouslyInstalled = snapshotStorage.loadSnapshot();
        Set<String> newlyInstalled = new HashSet<String>(currentlyInstalled);
        newlyInstalled.removeAll(previouslyInstalled);

        if (newlyInstalled.size() > MAX_PLAUSIBLE_NEW_INSTALLS_PER_CHECK) {
            // Implausible - see the comment above. Treat this exactly like a
            // first run: re-baseline against what's actually installed right
            // now, but don't touch any Block's app list.
            snapshotStorage.saveSnapshot(currentlyInstalled);
            return;
        }

        if (!newlyInstalled.isEmpty()) {
            BlocksStorage blocksStorage = new BlocksStorage(context);
            List<Block> blocks = blocksStorage.loadBlocks();
            boolean changed = false;
            for (Block b : blocks) {
                for (String pkg : newlyInstalled) {
                    if (b.blockedPackages.add(pkg)) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                blocksStorage.saveBlocks(blocks);
            }
        }

        snapshotStorage.saveSnapshot(currentlyInstalled);
    }

    private static Set<String> getInstalledLaunchablePackages(Context context, String ownPackage) {
        Set<String> result = new HashSet<String>();
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(ownPackage)) {
                result.add(pkg);
            }
        }
        return result;
    }

    /** Removes any Holiday Break whose end time has already passed, and returns what's left. */
    private static List<HolidayBreak> cleanUpExpiredBreaks(HolidayBreaksStorage storage) {
        List<HolidayBreak> all = storage.loadBreaks();
        long now = System.currentTimeMillis();
        List<HolidayBreak> stillValid = new ArrayList<HolidayBreak>();
        boolean anyExpired = false;
        for (HolidayBreak h : all) {
            if (h.endMillis <= now) {
                anyExpired = true;
            } else {
                stillValid.add(h);
            }
        }
        if (anyExpired) {
            storage.saveBreaks(stillValid);
        }
        return stillValid;
    }

    /** Schedules exactly one future wake-up, at the next moment any Block's schedule changes. */
    public static void scheduleNextAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0,
                new Intent(context, BlockAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);

        List<Block> blocks = new BlocksStorage(context).loadBlocks();
        List<HolidayBreak> holidayBreaks = new HolidayBreaksStorage(context).loadBreaks();
        long nextTransition = computeNextTransitionMillis(blocks, holidayBreaks);

        // Guarantee a check at minimum every ~1 minute - for new-install
        // detection when there's at least one Block, and unconditionally
        // for the permanent, not-tied-to-any-Block protections (Device
        // Owner restrictions, Chrome content policy, other-browser lock)
        // so those stay self-healing even for a user with zero Blocks
        // configured. 1 minute is Android's own documented ceiling for how
        // often setExactAndAllowWhileIdle can fire during normal
        // (screen-on) use - asking for less than that wouldn't get
        // delivered any faster anyway. While the phone is genuinely idle
        // (Doze), Android automatically throttles this back to roughly
        // every 15 minutes on its own regardless of what we request here -
        // that protection is built into the OS, not something we need to
        // manage ourselves.
        long periodicCheck = System.currentTimeMillis() + (60 * 1000L);
        long nextAlarm = (nextTransition > 0) ? Math.min(nextTransition, periodicCheck) : periodicCheck;

        if (nextAlarm > 0) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarm, pi);
        }
    }

    /** The one entry point most callers should use: fix the current state, then schedule the next check. */
    public static void reapplyAndReschedule(Context context) {
        applyNow(context);
        scheduleNextAlarm(context);
    }

    /** Finds the soonest upcoming Block-schedule or Holiday-Break boundary. Returns -1 if there's nothing scheduled at all. */
    private static long computeNextTransitionMillis(List<Block> blocks, List<HolidayBreak> holidayBreaks) {
        long now = System.currentTimeMillis();
        long best = -1;

        for (HolidayBreak h : holidayBreaks) {
            if (h.startMillis > now && (best == -1 || h.startMillis < best)) {
                best = h.startMillis;
            }
            if (h.endMillis > now && (best == -1 || h.endMillis < best)) {
                best = h.endMillis;
            }
        }

        for (Block b : blocks) {
            for (TimeRange r : b.ranges) {
                for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
                    Calendar dayCal = Calendar.getInstance();
                    dayCal.add(Calendar.DAY_OF_YEAR, dayOffset);
                    int dow = dayCal.get(Calendar.DAY_OF_WEEK);
                    if (!r.days.contains(dow)) {
                        continue;
                    }

                    boolean overnight = r.startMinute >= r.endMinute;

                    long startMillis = atMinuteOfDay(dayCal, r.startMinute);
                    if (startMillis > now && (best == -1 || startMillis < best)) {
                        best = startMillis;
                    }

                    Calendar endDayCal = dayCal;
                    if (overnight) {
                        endDayCal = (Calendar) dayCal.clone();
                        endDayCal.add(Calendar.DAY_OF_YEAR, 1);
                    }
                    long endMillis = atMinuteOfDay(endDayCal, r.endMinute);
                    if (endMillis > now && (best == -1 || endMillis < best)) {
                        best = endMillis;
                    }
                }
            }
        }
        return best;
    }

    private static long atMinuteOfDay(Calendar dayCal, int minuteOfDay) {
        Calendar c = (Calendar) dayCal.clone();
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
