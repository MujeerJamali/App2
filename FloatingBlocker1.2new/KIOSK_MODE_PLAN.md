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

## Not yet discussed

- Exact mechanics of forcing the phone into the Kiosk lockdown screen
  immediately the moment Lock Schedule becomes locked (even mid-use of
  another app) - this was "yes, force it immediately" in the previous
  (abandoned) round; needs reconfirming.
- Lock-task feature set (whether Recents/Home are enabled in lock task
  mode) - previous round kept `LOCK_TASK_FEATURE_SYSTEM_INFO` only.
- Migration handling for existing Blocks (blocklist data can't be safely
  reinterpreted as whitelist data - needs a one-time migration, as
  designed in the previous abandoned round).
- Whether the "Always Allowed Apps" screen allows adding *any* installed
  app, or something more constrained.
