# Multi-App Kiosk Mode - Design Discussion (not yet implemented)

This file tracks the design decisions made in discussion, before any of this
is coded. Nothing in this document is implemented yet - it exists so the
decisions survive even if the conversation gets summarized/compacted.

## Already shipped (context - not part of this new work)

- Blocks are currently **blocklist** semantics (list what's blocked). A full
  whitelist conversion was built once already, then fully reverted at the
  user's request ("abandon everything, I don't want kiosk mode"). That
  revert is the current baseline (v4.78+) this new round builds on top of.
- Time-lock (`DISALLOW_CONFIG_DATE_TIME`) tied to Lock Schedule's locked
  state - shipped standalone as v4.79, independent of Kiosk mode.
- USB debugging + Termux/Shizuku suspension tied to Lock Schedule's locked
  state - pre-existing, independent of this effort.
- Safe Mode boot disable, Factory Reset disable, Disallow Add User - already
  permanent/unconditional, not tied to Lock Schedule at all.
- Confirmations (non-ringing Alarm sibling), 4x one-time Clear Punishment,
  Stop All Ringing, Pause All 1hr, Pause Until 11pm, reusable custom-duration
  Pause + its own Delete Forever button - all shipped (up to v4.84).

## Decisions made this round (in order asked)

1. **Whitelist conversion**: Blocks convert from blocklist to whitelist
   semantics (a Block's app list means "only these are allowed" while that
   Block is active, not "these are blocked").

2. **Kiosk trigger signal**: automatic Kiosk lockdown is tied to **Lock
   Schedule's own locked/unlocked window** - not to "is any Block currently
   active" (which was the original proposal; rejected in favor of Lock
   Schedule specifically).

3. **When Lock Schedule is locked but no Block happens to be active**: Kiosk
   still engages, but the allow-list in that case is just the baseline
   always-allowed set (see #5) - not fully open, not fully empty either.

4. **New "Always Allowed Apps" list**: a user-managed whitelist tier,
   separate from any individual Block's own whitelist, viewable/editable
   through its own dedicated screen. Used as the fallback/floor whitelist
   whenever Lock Schedule is locked (combined with whatever Block, if any,
   is currently active).

5. **Hardcoded, unconditional tier - unlocked 24/7, nothing can block them,
   not even Lock Schedule locked time**:
   - This app itself (Self-Control / `com.mujeer.floatingblocker`)
   - Business ERP / "store app" (`com.mujeer.businesserp`)
   - The phone's current default Dialer (detected live via
     `TelecomManager.getDefaultDialerPackage()`, not a fixed package name)
   - The phone's current default SMS/messaging app (detected live via
     `Telephony.Sms.getDefaultSmsPackage()`, not a fixed package name)

   These are a separate, fixed tier from the "Always Allowed Apps" list in
   #4 - they're unconditional (true 24/7, including outside Lock Schedule's
   locked time, though outside locked time nothing is restricted anyway),
   while #4 is Lock-Schedule-locked-time-specific and user-editable.

6. **Editing the "Always Allowed Apps" list (#4)**: gated the normal way -
   only changeable during **unlocked** Lock Schedule time, matching how
   every other restriction-weakening edit works elsewhere in this app
   (adding to a whitelist weakens restriction, so it's gated; by the same
   logic removing from it is presumably always allowed, mirroring how
   Block app-list edits already work under whitelist semantics).

7. **Visibility of the hardcoded tier (#5) on the Always Allowed Apps
   screen**: yes - shown there too, listed but not removable (not actually
   editable anyway), so everything reachable is visible in one place
   instead of some of it being invisible/implicit.

8. **Manual Kiosk Mode removal**: confirmed - the existing pick-apps/
   pick-a-duration/start-a-session flow is removed entirely, replaced by
   the automatic Lock-Schedule-triggered lockdown designed here.

9. **Kiosk Safety toggle**: a new, separate reusable on/off toggle
   (independent of Master Safety, which is already permanently deleted)
   that disables *only* the automatic Kiosk lockdown if something goes
   wrong - plus its own one-way "delete this safety forever" button,
   confirmed as still wanted.

10. **New: guaranteed daily unlocked window, 2:00 AM - 2:10 AM**, overriding
    Lock Schedule's locked state **everywhere it's checked** - every Block/
    Alarm/Confirmation edit gate, the Kiosk trigger itself, the Time-lock on
    Settings, the USB-debugging/Termux/Shizuku lock - regardless of
    whatever ranges are actually configured in Lock Schedule. This is a
    hardcoded, non-configurable safety carve-out, not something the user
    sets via the normal Lock Schedule screen. Explicitly: "No matter
    whatever schedule is."

## Resolved (previously open)

11. **OS-hardening sequencing**: resolved as (a) - USB debugging/ADB stays
    exactly as it already is, tied to Lock Schedule's locked/unlocked state
    (blocked while locked, open while unlocked). It is NOT being made
    permanently always-blocked. No change needed here at all - existing
    behavior is kept as-is.

12. **Force immediate entry**: reconfirmed - yes, the moment Lock Schedule
    becomes locked, forcibly jump into the Kiosk lockdown screen, even
    mid-use of another app.

13. **Lock-task feature set** - decided per-flag:
    - `SYSTEM_INFO`: on (status bar clock/battery - no risk, baseline).
    - `HOME`: **off**. Would require designating one of our own apps as an
      actual Home/launcher activity; if that's ever even slightly
      misconfigured, pressing Home could land on the REAL system launcher
      instead of our screen - full escape. Not worth the risk.
    - `OVERVIEW`/Recents: **off** (Android requires HOME to be on before
      this can be anyway). Recents is a system UI surface with its own
      OEM-added gestures (split-screen, clear-all, etc.) that's hard to
      fully audit on ColorOS.
    - `NOTIFICATIONS` (the real system shade): **off**. Pulling down the
      real shade on ColorOS exposes whatever Quick Settings tiles are
      bundled in there, including Super Power Saving Mode (the one that
      caused the white-screen freeze bug earlier this session). Building
      a custom in-app alternative instead (see #14 below).
    - `GLOBAL_ACTIONS` (long-press power button menu - power off/restart/
      emergency call): **on**.
    - `KEYGUARD` (require the real PIN/pattern on wake before returning to
      the pinned kiosk screen): **on**. Security-only, doesn't loosen or
      tighten the kiosk itself either way.

14. **Custom in-app alternatives to the risky OS features**, instead of
    enabling them:
    - **In-app "Recent" list**: track "most recently launched" ourselves
      among the kiosk's allowed apps, show that in our own screen. No
      special permission needed - this is just our own bookkeeping.
    - **In-app "Notifications" screen**: render notifications ourselves
      using Android's Notification Listener API, instead of exposing the
      real system shade. Requires a ONE-TIME manual grant (Settings →
      Special app access → Notification access) - not something Device
      Owner can silently grant, needs doing once while unlocked, persists
      after that. **Scope: only show notifications from apps that are
      actually reachable in the kiosk** (the whitelist - Business ERP,
      Dialer, SMS, Self-Control, Always Allowed list) - not notifications
      from blocked apps, since seeing "you got a message" with no way to
      act on it would just be pointless FOMO-bait.

## Known issue to resolve (not yet decided how)

- **Bluetooth toggle doesn't actually work**, even though the WiFi toggle
  in the existing (pre-redesign) Kiosk quick-toggles does. Root cause is
  almost certainly a real Android platform restriction, not a simple bug:
  - `WifiManager.setWifiEnabled()` has a DOCUMENTED special carve-out for
    Device Owner/Profile Owner apps - it keeps working for us specifically
    because we're Device Owner, even though Android deprecated it for
    regular apps.
  - `BluetoothAdapter.enable()`/`.disable()` never got an equivalent
    Device-Owner carve-out. Since Android 13 (API 33) these are documented
    to fail (return false, or throw `SecurityException`) for any app that
    isn't holding `BLUETOOTH_PRIVILEGED` - a signature/system-only
    permission a Device Owner app cannot be granted the normal way. Our
    existing try/catch just swallows this and shows the "toggle failed"
    toast, which matches exactly what was observed.
  - **Decided: drop the Bluetooth toggle entirely.** Silent toggling isn't
    realistically possible on this Android version without a system-level
    permission a Device Owner app can't hold. The WiFi toggle stays (it
    keeps working fine via the documented Device-Owner carve-out).

15. **"Always Allowed Apps" picker scope**: shows all installed apps, no
    filtering/restriction.

16. **Migration: full app-wide fresh-start reset**, not a partial/surgical
    migration. On this update's first run:
    - Blocks, Holiday Breaks, Alarms, Confirmations, and all punishment
      history (widen windows) are wiped completely clean - not just app
      lists; the records themselves are gone. Names, schedules, everything.
    - **Lock Schedule's own configured hours are ALSO wiped** ("everything"
      - confirmed explicitly, not just the records governed by it). Until
      reconfigured, nothing is locked at all, and the new Kiosk lockdown
      has no trigger.
    - The six existing one-time/single-use safety-valve features are
      **deleted from the codebase entirely** (not reset-to-unused, actually
      removed as features): all four Clear Punishment buttons, both
      one-time Pause buttons (1 Hour, Until 11 PM), AND **Stop All Ringing**.
      The first five are genuinely superseded (the new reusable
      custom-duration Pause button already overrides any punishment
      widen-window too, for as long as it's active). Stop All Ringing is
      NOT superseded by anything - it force-stops a stuck Alarm ring
      without a barcode scan, and nothing in the new design replaces that.
      **Explicitly accepted gap**: if barcodes are ever lost or the camera
      breaks while an Alarm is ringing, there will be no way out except
      a successful barcode scan. Confirmed anyway, deliberately.

17. **Blocks remain fully independent of Lock Schedule** - two separate
    layers, not one:
    - Blocks run 24/7 on their own schedule, independent of Lock Schedule's
      locked/unlocked state. A Block suspends non-whitelisted apps whenever
      *that Block* is active, per its own configured schedule, regardless of
      whether Lock Schedule happens to be locked or unlocked at that moment.
    - Lock Schedule going locked is a separate, additional trigger: it pins
      the device into the Kiosk lockdown screen. While pinned, the allow-list
      is (active Block's whitelist, if any) union Tier-1 (#5) union Tier-2/
      Always Allowed Apps (#4).
    - Lock Schedule unlocked means no Kiosk pinning at all, regardless of
      Block state - but an independently-active Block still suspends its
      non-whitelisted apps as normal, since Blocks don't care about Lock
      Schedule. This refines/confirms #3: #3 only described the locked case;
      this makes explicit that Blocks and Lock Schedule are two independent
      layers that merely combine their effects while Lock Schedule is locked.

18. **Kiosk screen layout**: one single Activity (`KioskSessionActivity`)
    with tabs inside it - "Apps" (the allowed-apps grid, the main surface),
    "Recent", and "Notifications" - rather than three separate Activities
    navigated to from a home screen. Keeps one source of truth for "which
    apps are allowed" feeding all three tabs, and avoids Activity-transition
    edge cases on the pinned screen.

19. **Kiosk Safety toggle placement (#9)**: lives on the main screen's
    existing Safety section, alongside Battery Exemption etc. - not on a
    dedicated sub-screen. Since Self-Control itself is Tier-1 (reachable
    24/7, even while a Kiosk lockdown is actively pinning the device),
    exposure is identical either way; what differs is tap-count during an
    actual malfunction, so the main screen (fewest taps) wins as the safety
    net it's meant to be.

20. **Kiosk Safety toggle gating**: none - it works unconditionally at any
    time, including while Lock Schedule is locked and Kiosk is actively
    pinning, mirroring how the old Master Safety toggle had no gating
    either (unlike the Pause buttons, which do block while locked). This is
    deliberate: it's a safety net for a real malfunction, and a malfunction
    is exactly as likely to happen during locked time as any other time.
    Once the feature is built and tested as reliable, the existing
    one-way "delete this safety forever" button (#9) is used to remove the
    escape hatch permanently.

21. **Forced entry when the device was asleep through the transition**:
    confirmed - if Lock Schedule's locked window begins while the screen is
    off/locked, Kiosk still forces itself onto the screen the instant the
    device is unlocked (first thing the user sees), not just when a Block/
    Lock Schedule transition happens to catch an app already in the
    foreground. Reinforces #12 (force immediate entry) to cover the
    screen-off case explicitly.

22. **"Recent" tab scope**: only tracks apps launched from within the Kiosk
    grid itself (we log it ourselves at the same point we fire the launch
    Intent) - not whatever happened to be in the foreground the instant
    pinning kicked in. Simpler (no `UsageStatsManager`/task-querying needed)
    and sufficient, since forced immediate entry (#12/#21) leaves little
    room for that case to matter anyway.

23. **Recent list filtering**: an entry is hidden from the "Recent" tab the
    moment its app is no longer in the currently active allow-list -
    not shown greyed-out or tappable-with-a-toast. The Recent tab always
    reflects only what's actually reachable right now.

24. **Notifications tab tap action**: tapping a notification opens the
    source app, same as a real system notification would - not a
    read-only display.

25. **Notifications tab dismiss behavior**: dismissing/clearing a
    notification in-app only hides it from our own list - it does NOT
    cancel the real system notification. The real notification (and
    whatever the source app itself does with it) is left untouched.

26. **Exiting Kiosk pinning**: fully automatic - the moment Lock Schedule
    transitions to unlocked, `stopLockTask()` fires and the device drops
    back to the real home screen, no button/explicit action needed.
    Symmetric with how entry is automatic (#2): Lock Schedule is the one
    trigger for both directions. The Kiosk Safety toggle (#19/#20) already
    covers the "something's wrong, let me out" case, so no separate manual
    exit button is needed on the Kiosk screen itself.

27. **Fresh-start reset trigger (#16) mechanics**: a permanent "migration
    done" flag, set once the wipe has run and never cleared - not a
    version-code check. Fires exactly once, ever, on the first run after
    this update. A future update that needs its own reset would need its
    own separate flag, not a reuse of this one.

28. **Allowed-apps grid ordering**: Tier-1 (#5, the hardcoded always-on
    apps) are pinned first, in a fixed position - everything else (Tier-2
    Always Allowed Apps + the active Block's whitelist, if any) is sorted
    alphabetically after them. No usage-based/dynamic reordering.

29. **Multiple overlapping active Blocks**: when more than one Block is
    active at the same time, only apps that appear on *every* currently-
    active Block's whitelist stay allowed - not the combined/union of all
    of them. Example: Block A (9am-5pm) allows Camera + Calculator; Block
    B (1pm-6pm) allows Maps + Calculator; during the 1pm-5pm overlap, only
    Calculator is allowed from either Block (plus Tier-1/Tier-2 as always).
    This keeps the same invariant the old blocklist Blocks already had:
    stacking more active Blocks can only ever narrow access, never widen
    it. Falls back to Tier-1 union Tier-2 only if the overlapping Blocks
    share nothing in common - same floor as the "no Block active" case (#3).

30. **Overlapping Block schedules at edit time**: allowed freely, no
    warning/validation when saving a Block whose schedule overlaps
    another's - the standard approach for this kind of record (same as
    Holiday Breaks/Alarms/Confirmations: independent entries, no
    cross-record validation). No need to prevent it either, since #29
    already defines exactly what happens when schedules do overlap.

31. **Always Allowed Apps (#4) auto-pruning**: an entry is removed
    automatically once its app is uninstalled from the device - not kept
    around in case it's reinstalled later. If reinstalled, it has to be
    added back through the picker.
