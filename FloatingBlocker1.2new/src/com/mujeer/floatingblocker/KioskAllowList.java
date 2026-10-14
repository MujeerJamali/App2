package com.mujeer.floatingblocker;

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
 * Single source of truth for "what's allowed right now" under the
 * redesigned, whitelist-based Kiosk Mode - used by BOTH BlockEnforcer
 * (deciding what to suspend at the OS level) and KioskSessionActivity
 * (deciding what to show/launch in the pinned grid, and whether to pin
 * at all), so the two can never disagree about what's actually
 * reachable. See KIOSK_MODE_PLAN.md for the full design discussion.
 *
 * Two independent layers feed the final allow-list, each suppressible by
 * different, pre-existing overrides:
 *  - The BLOCK layer (Tier 3): the intersection of every currently-active
 *    Block's own allowedPackages (decision #29) - active only while >=1
 *    Block is active, and suppressed by whatever already suppresses
 *    "Blocks enforcement" generally (Master Safety, the Blocks Pause
 *    override, being far enough from home) - those are explicitly scoped
 *    to Blocks already, so a Block counts as "active" for Kiosk's
 *    purposes only when it would also actually be restricting anything.
 *  - The KIOSK layer (Tier 2, "Always Allowed Apps"): active only while
 *    Lock Schedule is currently locked, and suppressed by Master Safety
 *    or the separate Kiosk Safety toggle - NOT by the Blocks Pause
 *    override or far-from-home, which are explicitly scoped to Blocks
 *    only, not to Lock Schedule's own locked state.
 *
 * Tier 1 (hardcoded, unconditional - this app, Business ERP, live default
 * Dialer/SMS) is always part of the result regardless of either layer.
 *
 * Package suspension (BlockEnforcer) fires whenever EITHER layer is
 * active. Kiosk pinning (KioskSessionActivity) fires only off the KIOSK
 * layer specifically - pinning is tied to Lock Schedule's own state
 * (decision #2), not to "is a Block active".
 */
public class KioskAllowList {

    public static final String BUSINESS_ERP_PACKAGE = "com.mujeer.businesserp";

    public static class Result {
        public final Set<String> allow;
        public final boolean blockLayerActive;
        public final boolean kioskLayerActive;

        Result(Set<String> allow, boolean blockLayerActive, boolean kioskLayerActive) {
            this.allow = allow;
            this.blockLayerActive = blockLayerActive;
            this.kioskLayerActive = kioskLayerActive;
        }

        public boolean anyRestrictionActive() {
            return blockLayerActive || kioskLayerActive;
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
     * The device's current default Home/launcher package - never
     * suspended, regardless of any allow-list. This is a pure safety
     * guard, not one of the decided tiers: under whitelist semantics,
     * suspending every non-allowed launchable app could otherwise sweep
     * up the launcher itself if it's not explicitly listed anywhere,
     * leaving no way back to a home screen at all while a Block is
     * active. Live-detected the same way Tier-1's Dialer/SMS are, so it
     * tracks whatever launcher is actually in use rather than a fixed
     * package name.
     */
    public static String getCurrentLauncherPackage(Context context) {
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

    /**
     * Intersection of every currently-active Block's allowedPackages
     * (decision #29) - null if activeBlocks is empty (distinct from an
     * empty set, which would mean "every active Block agrees on nothing
     * in common").
     */
    public static Set<String> computeActiveBlockIntersection(List<Block> activeBlocks) {
        if (activeBlocks.isEmpty()) {
            return null;
        }
        Set<String> intersection = null;
        for (Block b : activeBlocks) {
            if (intersection == null) {
                intersection = new HashSet<String>(b.allowedPackages);
            } else {
                intersection.retainAll(b.allowedPackages);
            }
        }
        return intersection;
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
     * True if the KIOSK layer (Tier-2 + the Kiosk-pinning trigger itself)
     * is suppressed entirely right now - Master Safety, or the separate
     * Kiosk Safety toggle (decision #9/#20, deliberately NOT suppressed
     * by Pause or far-from-home, which are Blocks-scoped only).
     */
    public static boolean isKioskLayerSuppressed(Context context) {
        return new MasterSafetyStorage(context).isEngaged()
                || new KioskSafetyStorage(context).isEngaged();
    }

    /** The full allow-list in effect right now, plus which layer(s) are actually contributing to it. */
    public static Result computeCurrentAllowList(Context context) {
        Set<String> allow = new HashSet<String>(computeTier1(context));

        boolean blockLayerActive = false;
        if (!isBlockLayerSuppressed(context)) {
            List<Block> activeBlocks = computeActiveBlocks(context);
            if (!activeBlocks.isEmpty()) {
                blockLayerActive = true;
                Set<String> intersection = computeActiveBlockIntersection(activeBlocks);
                if (intersection != null) {
                    allow.addAll(intersection);
                }
            }
        }

        boolean kioskLayerActive = false;
        if (!isKioskLayerSuppressed(context) && new LockScheduleStorage(context).isCurrentlyLocked()) {
            kioskLayerActive = true;
            allow.addAll(new AlwaysAllowedAppsStorage(context).loadApps());
        }

        return new Result(allow, blockLayerActive, kioskLayerActive);
    }
}
