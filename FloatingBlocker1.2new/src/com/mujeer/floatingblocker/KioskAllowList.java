package com.mujeer.floatingblocker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for Tier-1 and for the two independent things
 * driven by Blocks/Lock Schedule under blocklist semantics (decisions
 * #42-47, reversing an earlier whitelist attempt - see
 * KIOSK_MODE_PLAN.md for the full history):
 *
 *  - SUSPENSION (BlockEnforcer): direct blocklist - suspend exactly the
 *    union of every currently-active Block's blockedPackages (decision
 *    #46: union, not intersection). Tier-1 and the live-detected default
 *    launcher are never suspended, regardless of any Block's list.
 *  - KIOSK PINNING (KioskSessionActivity, lock task mode): Android's
 *    lock-task API has no "pin to everything except these" mode, so this
 *    still needs a positive allow-list (decision #44) - computed live as
 *    Tier-1 union (every installed launchable app NOT in that same
 *    active-blocked union). With no Block active, that's simply every
 *    installed app (decision #45) - Kiosk still pins, but nothing is
 *    actually restricted.
 *
 * Both read from the same computeActiveBlocks()/computeActiveBlockedUnion()
 * so suspension and the pinned grid can never disagree about what's
 * currently blocked.
 */
public class KioskAllowList {

    public static final String BUSINESS_ERP_PACKAGE = "com.mujeer.businesserp";

    public static class Result {
        /** Union of every currently-active Block's blockedPackages - empty if blockLayerActive is false. */
        public final Set<String> activeBlockedPackages;
        /** Tier-1 union (everything not currently blocked) - only meaningful if kioskLayerActive is true. */
        public final Set<String> pinList;
        public final boolean blockLayerActive;
        public final boolean kioskLayerActive;

        Result(Set<String> activeBlockedPackages, Set<String> pinList, boolean blockLayerActive, boolean kioskLayerActive) {
            this.activeBlockedPackages = activeBlockedPackages;
            this.pinList = pinList;
            this.blockLayerActive = blockLayerActive;
            this.kioskLayerActive = kioskLayerActive;
        }
    }

    /** Tier 1 only - the hardcoded, unconditional set. */
    public static Set<String> computeTier1(Context context) {
        Set<String> result = new HashSet<String>();
        result.add(context.getPackageName());
        result.add(BUSINESS_ERP_PACKAGE);
        String dialer = getDefaultDialerPackage(context);
        if (dialer != null) {
            result.add(dialer);
        }
        String sms = getDefaultSmsPackage(context);
        if (sms != null) {
            result.add(sms);
        }
        return result;
    }

    /** True if no default Dialer app is currently set on this device (decision #32). */
    public static boolean isMissingDefaultDialer(Context context) {
        return getDefaultDialerPackage(context) == null;
    }

    /** True if no default SMS app is currently set on this device (decision #32). */
    public static boolean isMissingDefaultSms(Context context) {
        return getDefaultSmsPackage(context) == null;
    }

    private static String getDefaultDialerPackage(Context context) {
        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            return tm != null ? tm.getDefaultDialerPackage() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getDefaultSmsPackage(Context context) {
        try {
            return Telephony.Sms.getDefaultSmsPackage(context);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The device's REAL default Home/launcher package - never suspended,
     * regardless of any Block's blocklist. A pure safety guard, not one
     * of the decided tiers: blocklisting the launcher itself
     * (deliberately or by mistake) would otherwise strand the user with
     * no way back to a home screen while that Block is active.
     *
     * Checks RealLauncherStorage first: once HomeGatekeeperActivity is
     * registered as the forced CATEGORY_HOME answer (see
     * BlockEnforcer.applyHomeGatekeeperProtection), a live resolveActivity
     * query would just answer with our OWN gatekeeper, not the device's
     * actual launcher - RealLauncherStorage is what still remembers who
     * that really is. Falls back to the live query only if that capture
     * hasn't happened yet (e.g. right after a fresh install, before the
     * first enforcement cycle has run).
     */
    public static String getCurrentLauncherPackage(Context context) {
        ComponentName stored = new RealLauncherStorage(context).loadRealLauncher();
        if (stored != null) {
            return stored.getPackageName();
        }
        try {
            PackageManager pm = context.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = pm.resolveActivity(homeIntent, 0);
            return (info != null && info.activityInfo != null) ? info.activityInfo.packageName : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Every installed, launchable app except our own - the same
     * universe shown in the Add App picker, and what BlockEnforcer
     * enumerates to compute the Kiosk pin-list's complement.
     */
    public static Set<String> getAllInstalledLaunchablePackages(Context context) {
        Set<String> result = new HashSet<String>();
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        String ownPackage = context.getPackageName();
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(ownPackage)) {
                result.add(pkg);
            }
        }
        return result;
    }

    /**
     * Every Block that's currently active right now - either its own
     * schedule is active, or it's still inside a one-time punishment
     * widen window (BlockPunishmentStorage) - skipping any Block
     * currently covered by an active Holiday Break. Does NOT itself
     * account for Master Safety/Pause/far-from-home - see
     * isBlockLayerSuppressed().
     */
    public static List<Block> computeActiveBlocks(Context context) {
        List<Block> result = new ArrayList<Block>();
        List<Block> blocks = new BlocksStorage(context).loadBlocks();
        List<HolidayBreak> holidayBreaks = new HolidayBreaksStorage(context).loadBreaks();
        long now = System.currentTimeMillis();

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
                continue;
            }
            if (b.isActiveNow(nowMinutes, nowDay) || punishmentStorage.isWidenedActive(b.id, now)) {
                result.add(b);
            }
        }
        return result;
    }

    /** Union of every currently-active Block's blockedPackages (decision #46) - empty if activeBlocks is empty. */
    public static Set<String> computeActiveBlockedUnion(List<Block> activeBlocks) {
        Set<String> union = new HashSet<String>();
        for (Block b : activeBlocks) {
            union.addAll(b.blockedPackages);
        }
        return union;
    }

    /**
     * True if the BLOCK layer is suppressed entirely right now - Master
     * Safety engaged, the Blocks Pause override active, or far enough
     * from home - all three are already scoped to "Blocks enforcement"
     * specifically (see MasterSafetyStorage/BlocksPauseStorage/
     * HomeLocationChecker's own documented scope).
     */
    public static boolean isBlockLayerSuppressed(Context context) {
        long now = System.currentTimeMillis();
        return new MasterSafetyStorage(context).isEngaged()
                || new BlocksPauseStorage(context).isPaused()
                || new BlocksPauseStorage(context).isTemporaryOverrideActive(now)
                || HomeLocationChecker.isFarFromHome(context);
    }

    /**
     * True if the KIOSK layer (the pinning trigger itself) is suppressed
     * entirely right now - Master Safety, or the separate Kiosk Safety
     * toggle (decision #9/#20, deliberately NOT suppressed by Pause or
     * far-from-home, which are Blocks-scoped only).
     */
    public static boolean isKioskLayerSuppressed(Context context) {
        return new MasterSafetyStorage(context).isEngaged()
                || new KioskSafetyStorage(context).isEngaged();
    }

    /**
     * The full picture right now: which packages are actively blocked
     * (for suspension), and the derived Kiosk pin-list (for lock task
     * mode), plus which layer(s) are actually active.
     */
    public static Result computeCurrentAllowList(Context context) {
        List<Block> activeBlocks = computeActiveBlocks(context);
        boolean blockLayerActive = !activeBlocks.isEmpty() && !isBlockLayerSuppressed(context);
        Set<String> activeBlockedPackages = blockLayerActive
                ? computeActiveBlockedUnion(activeBlocks)
                : new HashSet<String>();

        boolean kioskLayerActive = !isKioskLayerSuppressed(context)
                && new LockScheduleStorage(context).isCurrentlyLocked();

        Set<String> pinList = new HashSet<String>();
        if (kioskLayerActive) {
            pinList.addAll(computeTier1(context));
            for (String pkg : getAllInstalledLaunchablePackages(context)) {
                if (!activeBlockedPackages.contains(pkg)) {
                    pinList.add(pkg);
                }
            }
        }

        return new Result(activeBlockedPackages, pinList, blockLayerActive, kioskLayerActive);
    }
}
