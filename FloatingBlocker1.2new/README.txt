Claude connection test - this line confirms Claude Code can read and edit this repo.
===================================================================================

Floating Blocker - version 4.37 (real root cause found: barcode decoding was scanning a sideways image)
===================================================================================

Floating Blocker - version 4.36 (NEW: flashlight toggle on the barcode scan screen)
===================================================================================

Floating Blocker - version 4.35 (Holiday Breaks starting on a later day can now be created while locked)
===================================================================================

Floating Blocker - version 4.34 (Alarm punishment now also skips Blocks currently on a Holiday Break)
===================================================================================

Floating Blocker - version 4.33 (NEW: barcode-dismiss Alarms that punish missed wake-ups by widening Blocks)
===================================================================================

Floating Blocker - version 4.32 (VPN removed from this app entirely - back to app-suspension-only blocking)
===================================================================================

Floating Blocker - version 4.31 (DISALLOW_CONFIG_VPN re-enforced - VPN-based blocking abandoned)
===================================================================================

Floating Blocker - version 4.30 (TEMPORARY: DISALLOW_CONFIG_VPN released during WireGuard migration testing)
===================================================================================

Floating Blocker - version 4.29 (real root cause found: the VPN tunnel was restarting roughly every minute)
===================================================================================

Floating Blocker - version 4.28 (new leading theory: silently-dropped IPv6 connection attempts, now logged)
===================================================================================

Floating Blocker - version 4.27 (diagnostic can now run live, updating continuously until you stop it)
===================================================================================

Floating Blocker - version 4.26 (diagnostic now reports whether Android has validated the VPN network)
===================================================================================

Floating Blocker - version 4.25 (found: Chrome's DNS works but its connections may be silently Block-dropped)
===================================================================================

Floating Blocker - version 4.24 (diagnostic log now shows which app each packet belongs to)
===================================================================================

Floating Blocker - version 4.23 (real root cause found: CleanBrowsing outage/rate-limit, no fallback DNS)
===================================================================================

Floating Blocker - version 4.22 (fixed: 4.21's shared UDP thread pool starved Chrome/video behind slow DNS)
===================================================================================

Floating Blocker - version 4.21 (fixed: UDP-packet thread storm causing intermittent Chrome DNS failures)
===================================================================================

Floating Blocker - version 4.17 (full-traffic VPN: real TCP/UDP relay, not DNS-only)
===================================================================================

Floating Blocker - version 4.18 (TCP relay was connecting unprotected - fixed)
===================================================================================

Floating Blocker - version 4.19 (per-app internet cutoff, Play Store block removed)
===================================================================================

Floating Blocker - version 4.20 (fixed: app updates could mass-add everything to every Block)
===================================================================================

WHAT CHANGED IN 4.47
-----------------------
- NEW: any settings change made during a 20km-away Location Override
  window is now temporary - it reverts the instant you're back in range,
  as if it never happened. The moment the override turns on, everything
  Lock Schedule normally protects gets snapshotted (Blocks, the schedule
  itself, Holiday Breaks, Blocked Websites, Registered Barcodes, Alarms,
  and the Blocks-paused toggle); the moment it turns back off, all of
  that gets restored, discarding whatever changed in between - additions
  included, not just removals/weakenings. The whole point of the window
  is to not be stuck locked out while away, not to let changes made in it
  stick without ever passing through a real unlocked period at home.
- Not snapshotted/restored: Emergency Safety (already its own separate,
  freely-togglable thing, untouched by Lock Schedule before this feature
  existed too) and the home location/override toggle themselves (that'd
  be circular).
- Detected via a new persisted "was the override active last check" flag
  (HomeLocationStorage), checked every enforcement cycle - the snapshot
  and restore both run as the very first thing in that cycle, before
  anything else reads Blocks/Holiday Breaks/Alarms, so a restore takes
  effect immediately rather than a cycle late.

WHAT CHANGED IN 4.46
-----------------------
- Build fix: HomeLocationChecker.java failed to compile in AIDE with
  "Method onProviderEnabled does not override method from its
  superclass" (and same for onProviderDisabled) - AIDE's compiler (ECJ)
  doesn't reliably recognize @Override on those two specific
  LocationListener methods against newer android.jar versions, even
  though the signature is correct. Removed @Override from just those
  two (onLocationChanged and onStatusChanged, which compiled fine, keep
  theirs) - still a correct interface implementation either way.

WHAT CHANGED IN 4.45
-----------------------
- NEW: an optional "Location Override" (new screen, off by default). When
  turned on, Lock Schedule's editing-protection, all Blocks, and Alarms
  stop applying whenever you're more than 20km from a home location you
  set (stand there and tap "Set This As Home" - no maps library needed).
  If your location can't be determined for any reason - permission not
  granted, Location turned off, no fix available - everything stays
  exactly as it would without this feature; restrictions still apply.
  That fail-safe direction is deliberate and matches how every other
  "can't tell" case in this app already behaves.
- Deliberately NOT affected by this, regardless of location: the
  permanent Device Owner protections (uninstall block, VPN block, safe
  boot/factory reset/add-user block, the Chrome content policy, the
  other-browsers lock) and the Termux/Shizuku/debugging-features
  anti-tamper locks. Those exist to protect the app itself, not to
  enforce a schedule, so they stay on no matter where the phone is.
- Changing an ALREADY-SET home location is gated by the true Lock
  Schedule state, not by this same location override - on purpose. If it
  used the location override too, being far from home would unlock the
  ability to redefine what "home" means, and a single trip could
  permanently disable the whole feature (reset home to wherever you
  currently are while the override has already kicked in, and your real
  home becomes "far away" forever after that). First-time setup, with no
  location saved yet, is always allowed, same as every other "add" in
  this app.
- Location permission (fine + background) is granted silently via Device
  Owner - no runtime prompt - only while the feature is turned on, and
  released back to the normal default otherwise, so nothing is held onto
  for no reason. Uses plain framework LocationManager, not Play Services
  (no Gradle dependency to build against here); a cached fix older than
  15 minutes is treated as "not found" rather than trusted.
- Known limitation: no location history is kept, so resolving a missed
  Alarm from while the phone was off uses today's current location as a
  best-effort stand-in for "was I far from home when it actually rang",
  not the true location at that past moment.

WHAT CHANGED IN 4.44
-----------------------
- NEW: an Alarm no longer rings at all while any Holiday Break is active
  - not just this Alarm's own affected Blocks' breaks, any active break
  - at the exact moment it was due. No sound, no vibration, no lock
    screen takeover, and it's not treated as missed either, so nothing
    gets punished once the break ends. This is a different, broader rule
    than the existing "punishment doesn't apply to a Block on an active
    break" one from 4.34 - that one only ever affected whether a specific
    Block got widened; this one stops the Alarm from ringing in the first
    place.
  - Applies to the live trigger (AlarmRingReceiver) and to the catch-up
    scan for occurrences missed while the phone was off - the same
    Holiday-Break check runs against each occurrence's own actual time,
    not just "right now", so a phone that was off through an occurrence
    that fell inside a break window is handled the same way it would
    have been handled live.

WHAT CHANGED IN 4.43
-----------------------
- Browser lockdown from 4.42 relied only on a fixed list of known browser
  package names - a browser installed from Play Store that wasn't on that
  list would have stayed completely unblocked. Switched to live
  detection: every cycle, any app that resolves a plain, host-agnostic
  http:// link gets suspended along with the named ones - that's the
  exact same mechanism Android itself uses to decide what shows up in the
  "Open with..." chooser, so it catches a newly installed or previously
  unrecognized browser automatically instead of needing its package name
  added by hand.
- A small exclude-list (Google app/Assistant, Gmail, Google Drive, Google
  Messages, Google Maps, Play Store) is never auto-suspended by this
  detection, even if one of them happens to register a generic link
  handler for its own link-preview purposes - avoids silently breaking
  an app that isn't actually a standalone browser.
- The fixed package-name list from 4.42 is kept too, as a backup for the
  rare case a browser's own intent filter is built unusually enough to
  not get picked up by the live check.

WHAT CHANGED IN 4.42
-----------------------
- NEW: adult-content blocking, with no DNS, VPN, Accessibility Service or
  Usage Stats involved. Two permanent protections, always on, not tied to
  Blocks or Lock Schedule:
  1. Chrome managed policy, pushed straight into Chrome via
     setApplicationRestrictions (the same mechanism real enterprise MDM
     apps use): blocks mature/explicit sites in general
     (SafeSitesFilterBehavior), forces Google SafeSearch, forces YouTube's
     Strict restricted mode, and disables Incognito. This is enforced
     inside Chrome itself, so it applies to every tab Chrome opens - not
     something this app has to watch for.
  2. Every other common browser (Firefox, Opera, Samsung Internet, Brave,
     Edge, DuckDuckGo, UC Browser, Vivaldi, Kiwi, Mi Browser, Yandex, Tor
     Browser, Chrome's own Beta/Dev/Canary channels, and more) is kept
     permanently suspended, so Chrome can't just be swapped out for one
     that doesn't have the policy. Self-healing - a newly installed
     browser gets suspended again on the next periodic check (within
     about a minute).
- The existing "Blocked Websites" list is now actually enforced for the
  first time - it used to just be recorded with nothing acting on it
  (leftover from when the VPN-based filter was removed). Domains there
  are now pushed into Chrome's URLBlocklist policy: a domain blocks that
  host and every subdomain of it. Adding/removing a domain now applies
  immediately instead of waiting for the next unrelated enforcement pass.
- The background periodic check (roughly once a minute while the screen
  is on, throttled back automatically by Android's own Doze system
  otherwise) now always runs, even for a user with zero Blocks configured
  - needed so these new permanent protections (and the existing Device
  Owner ones) keep self-healing regardless of whether any Blocks exist.
- Known gap, inherent to this approach rather than a bug: in-app browsers
  and WebViews inside other apps (e.g. a link opened inside Reddit,
  Instagram, X) aren't covered by a Chrome-only policy - only Chrome
  itself, and only Chrome, is being locked down and filtered here.

WHAT CHANGED IN 4.41
-----------------------
- FIXED: "alarm is ringing and vibrating but there's no place to scan
  the barcode and dismiss it." The sound/vibration run from the
  foreground service directly and don't depend on the notification, so
  they always work - but the full-screen ringing screen only launches
  automatically when the phone's screen was off or locked at the
  moment the alarm fired. If the screen was already on and unlocked
  (e.g. actively using the phone, or testing), Android suppresses the
  automatic full-screen takeover and shows only a small notification
  instead - easy to miss if you don't realize it's tappable.
- The alarm notification now has its own explicit "Scan Barcode to
  Dismiss" action button, so there's always a visible, obvious way in
  even when the full-screen screen doesn't appear on its own. Also
  marked the notification as an alarm-category, publicly-visible
  notification so it's treated with the same priority/lock-screen
  visibility as a normal alarm clock's notification.
- If tapping that notification action still doesn't get you to a scan
  screen, that would point to something else (like the notification
  not showing at all, e.g. due to a device-specific battery/autostart
  restriction) - let me know exactly what you do or don't see when the
  next alarm fires (any notification banner at all? was the phone
  locked or already unlocked/in use at the time?).

WHAT CHANGED IN 4.40
-----------------------
- The 4.39 fix stopped the crash on every decode attempt (confirmed by
  the diagnostics: "Last decode error" now correctly shows the normal
  NotFoundException instead of a crash), but scanning a real barcode
  still wasn't succeeding - photos of the attempt showed the barcode
  held at a visible angle/tilt in frame. ZXing's default decode mode
  is a fast/light pass tuned for a clean, well-aligned code; it's much
  less tolerant of skew and blur than that.
- Enabled ZXing's TRY_HARDER decode hint, which makes the readers do a
  more thorough scan (more tolerant of angle, blur and noise) at the
  cost of some extra CPU time per attempt - acceptable here since only
  one decode runs at a time, on a background thread.
- If scanning still doesn't succeed after this, try holding the phone
  so the barcode's bars are roughly horizontal/parallel with the
  screen's top and bottom edges (not tilted diagonally), filling a
  good portion of the frame, held steady long enough to focus.

WHAT CHANGED IN 4.39
-----------------------
- ACTUAL REMAINING ROOT CAUSE FOUND (via the 4.38 on-screen diagnostics):
  every single decode attempt was throwing
  ArrayIndexOutOfBoundsException, not just failing to find a code. The
  NV21 90-degree rotation helper added in 4.37 had a bug in its own
  chroma (VU) plane loop - it indexed that plane using the same
  full-resolution row range as the Y plane (height rows), but the
  chroma plane for NV21 only actually has height/2 rows. That mismatch
  read/wrote past the end of the buffer on every frame, before ZXing
  ever got a valid image to look at.
- FIXED: the chroma loop now correctly indexes only the height/2 rows
  the chroma plane actually has, matching the standard, well-known
  NV21 rotation algorithm. This is what was actually breaking barcode
  scanning - the 4.37 fix corrected the right idea (the buffer does
  need rotating), but the rotation code itself had this bug the whole
  time.
- The 4.38 on-screen diagnostics are left in place for this build so
  the fix can be confirmed directly (frames received/decode attempts
  climbing with no repeating exception, and a successful scan actually
  finishing the screen).

WHAT CHANGED IN 4.38
-----------------------
- TEMPORARY DIAGNOSTIC BUILD: the 4.37 orientation fix alone did not
  resolve "barcode scanning still not working" as reported after
  rebuilding, so there's at least one more bug still present. Rather
  than guess again, the barcode scan screen now shows live on-screen
  diagnostics (same philosophy as the rest of this app - no logcat
  reliance, since that's not practically accessible on a non-rooted
  device): preview size and format (compared against the NV21 format
  ZXing decoding assumes), a running count of camera preview frames
  received, a running count of decode attempts made, and the most
  recent decode error/result (including the previously-silent "no code
  in this frame" case).
- If the camera fails to open, the screen no longer immediately closes
  - it now stays open showing the exact failure so it can actually be
  read, instead of an instant close with nothing visible.
- A sanity check was added before decoding: if the raw frame buffer is
  smaller than the claimed preview width x height, that's now reported
  as a specific error instead of silently failing or crashing.
- This diagnostic readout temporarily replaces the normal
  scan-instructions/wrong-code text on this screen - accepted for this
  debug build so the real remaining cause can be pinned down from what
  gets reported back (camera never opening vs. zero frames arriving
  vs. a real exception vs. a preview-format mismatch vs. a buffer-size
  mismatch).

WHAT CHANGED IN 4.37
-----------------------
- ACTUAL ROOT CAUSE FOUND for "camera shows, but never detects any
  barcode": camera.setDisplayOrientation(90) only rotates what's rendered
  on screen for the user to see - it does NOT rotate the raw preview
  buffer delivered to onPreviewFrame(). That buffer stays in the camera
  sensor's native (landscape) orientation regardless, so every decode
  attempt was analyzing a sideways image the whole time. A QR code's
  detector is rotation-tolerant enough to sometimes survive this, but a
  real 1D barcode essentially never does - matching exactly what was
  reported (nothing detected, ever).
- FIXED: the raw NV21 buffer is now rotated 90 degrees clockwise (the
  standard, well-known NV21 rotation routine) to match
  setDisplayOrientation(90) before being handed to ZXing, so the decoder
  now sees the same upright orientation the user sees on screen.

WHAT CHANGED IN 4.36
-----------------------
- NEW: a "Flashlight" toggle button on the barcode scan screen (top-right
  corner), for scanning in the dark - relevant since a wake-up alarm's
  barcode is exactly the kind of thing you'd be scanning first thing in
  the morning with the lights off. Uses the same classic Camera API
  already used for scanning (Camera.Parameters.FLASH_MODE_TORCH) - no new
  permission needed (CAMERA already covers it) and no new dependency.
- Only shown on devices that actually report torch support
  (getSupportedFlashModes().contains(FLASH_MODE_TORCH)) - hidden entirely
  otherwise rather than showing a button that would just fail. Always
  starts off when the scan screen opens, and is force-turned-off whenever
  the camera itself stops (leaving the screen, camera error, etc.) so it
  can never get left on by accident.

WHAT CHANGED IN 4.35
-----------------------
- NEW EXCEPTION to Holiday Break creation: previously it required the Lock
  Schedule to currently be unlocked, full stop. Now it's also allowed
  during a locked period if the break's chosen START doesn't take effect
  until a later "app-day" - and for this specific purpose, a day is
  defined as starting at 2:00 AM instead of midnight, so e.g. 1:30 AM
  still counts as the day before, while 2:00 AM onward counts as the new
  one. The reasoning: a break that only starts on a genuinely later day
  can't weaken anything happening right now, so gating it behind "is the
  schedule unlocked THIS EXACT MOMENT" was blocking harmless future
  planning for no real reason.
- This is purely an ADDITIONAL way creation becomes allowed - the existing
  unlocked-Lock-Schedule path still works exactly as before, unchanged.
  Nothing about how an already-created break is enforced changed at all
  (still starts/ends at its exact stored millis, same as always) -
  ONLY the creation-time permission check gained this one exception.
- The on-screen note and locked-message string now explain the 2:00 AM
  rule directly, and update live as you change the picked start date/time
  (previously the whole form was just disabled outright while locked,
  which no longer makes sense once whether it's allowed depends on what
  start time you end up picking).

WHAT CHANGED IN 4.34
-----------------------
- FIXED (4.33 follow-up): AlarmPunisher.resolveMissed() now explicitly
  skips any Block that's currently on an active Holiday Break at the
  moment a miss is resolved, same per-block check computeActiveBlockedPackages()
  already used to let a Break override a Block's normal schedule. Before
  this, a punished Block on Break would still have gotten a stored widen
  window - harmless in practice (the Break already overrides it at
  enforcement time, every time computeActiveBlockedPackages() runs), but
  now it's an explicit rule instead of an incidental side effect, and the
  widen window is never even recorded for that Block in the first place.
- The already-existing "skipped entirely if the whole Lock Schedule is
  currently unlocked" rule from 4.33 is unchanged - this adds the Holiday
  Break exception alongside it, per-Block rather than all-or-nothing.

WHAT CHANGED IN 4.33
-----------------------
- NEW: "Alarms" - inspired by "I Can't Wake Up!"-style alarm apps, but with
  exactly one dismiss method and nothing else: scanning a registered
  barcode/QR code. No snooze, no back button, no swipe-to-dismiss. An Alarm
  can have several triggers (each its own time + set of days, e.g. 6:00 AM
  weekdays and 8:00 AM weekends), and any one of several registered
  barcodes you choose can dismiss it - register a barcode once (under the
  new "Registered Barcodes" screen) by scanning it, then place that object
  somewhere that actually requires getting up.
- NEW: missing an Alarm (no correct scan within 10 minutes of it ringing)
  punishes the Blocks you chose for that Alarm: each one's current/next
  occurrence gets widened by 1 hour earlier on the start and 1 hour later
  on the end (e.g. 6:00 AM-6:00 PM becomes 5:00 AM-7:00 PM), capped so a
  single occurrence never exceeds a full 24-hour day. This is deliberately
  ONE-TIME, not permanent - it applies to that one occurrence only, and
  the Block reverts to its normal configured schedule the next time
  around, achieved by never touching the Block's own stored ranges at all
  (see BlockPunishmentStorage) - just a temporary, self-expiring override
  consulted at evaluation time.
- The punishment is skipped entirely if the moment the 10-minute window
  expires falls during a currently UNLOCKED Lock Schedule period.
- Missing an Alarm because the phone was powered off through its entire
  ring window still punishes it - BlockEnforcer now catches up on any
  fully-elapsed, never-resolved Alarm occurrence the next time the app or
  device wakes up (see checkForMissedAlarms), walking forward through any
  backlog one occurrence at a time.
- Uses ZXing's core barcode-decoding library (a single, dependency-free
  jar - no native code, no AAR, nothing like the AmneziaWG library
  situation from 4.30) paired with the classic Camera API for the actual
  scanning, kept deliberately in the same "plain SDK, zero real
  dependencies" style as the rest of this app.
- New permissions: CAMERA (for scanning), VIBRATE/WAKE_LOCK/
  FOREGROUND_SERVICE/USE_FULL_SCREEN_INTENT (for the ringing screen and
  sound/vibration while an Alarm is active).
- HONEST LIMIT: like every app on Android, this cannot truly prevent
  force-stopping Self-Control itself from Settings, or powering the phone
  off - Device Owner has no API for either. The phone-off case is
  specifically handled by the missed-alarm catch-up above; force-stop
  during an active ring isn't preventable by any app, same limitation this
  README has always been honest about for Settings access generally.

WHAT CHANGED IN 4.32
-----------------------
- FOLLOW-UP TO 4.31: not just re-enforcing DISALLOW_CONFIG_VPN - this app's
  own VPN is now gone entirely, by explicit request. DnsVpnService (the
  hand-rolled DNS-filtering/TCP-UDP-relay engine this whole project's
  history - versions 4.7 through 4.29 - was built around) is deleted,
  along with everything that only existed to support it:
  - DnsVpnService.java, TcpSession.java, UdpRelaySession.java,
    IpV4UdpPacket.java, TcpPacket.java, DnsMessage.java, ChecksumUtil.java
    (the packet-relay engine itself)
  - VpnSafetyStorage.java and the "VPN Safety" / "Delete VPN Safety
    Forever" buttons on the main screen (a kill switch for a VPN that no
    longer exists has nothing left to switch)
  - DiagnosticActivity.java and the "Network Diagnostics" screen (it
    existed specifically to debug DnsVpnService's pipeline - nothing left
    to diagnose)
  - The <service> declaration for DnsVpnService in the manifest
  - The INTERNET and ACCESS_NETWORK_STATE permissions (nothing in this
    app talks to the network anymore at all)
- App-suspension-based blocking (Blocks, Lock Schedule, Holiday Breaks,
  Master Safety) is completely unaffected - none of it ever depended on
  the VPN. This is the app's actual, real blocking mechanism and it's
  untouched.
- IMPORTANT - Blocked Websites is now unenforced. The list (Blocks >
  Blocked Websites) still exists and can still be edited, but nothing
  currently blocks the domains on it - that enforcement was entirely
  DnsVpnService's job, and no replacement exists. The screen's own
  description now says this directly. If domain-level blocking is wanted
  again later, it needs a new mechanism - not necessarily VPN-based.
- WHY: a full-tunnel VPN (this app's own DNS-filter engine, and separately
  the WireGuard/AmneziaWG self-hosted-server replacement explored in
  4.30/4.31) breaks banking apps, which do their own VPN-detection as a
  fraud-prevention measure. That's a hard blocker for daily-driver use,
  independent of how well any particular VPN engine is built or
  configured - this isn't a bug to fix, it's why VPN-based blocking is
  being abandoned as an approach entirely.

WHAT CHANGED IN 4.31
-----------------------
- VPN-BASED BLOCKING PLAN ABANDONED: after 4.30's temporary release of
  DISALLOW_CONFIG_VPN (to test WireGuard/AmneziaWG apps against a real
  self-hosted server), the whole idea of running this app's traffic
  through a full-tunnel VPN was dropped - a full VPN breaks banking apps
  (a real, common fraud-prevention check many of them do), which makes it
  unworkable as a daily-driver device restriction, regardless of which VPN
  engine or how well it's configured.
- FIXED (reverted 4.30): that restriction is back to being actively
  enforced (addUserRestriction, not cleared) every cycle, same as every
  version before 4.30. No third-party VPN app - including the ones used
  for 4.30's testing - can be authorized on this device anymore. This
  closes the temporary gap 4.30 deliberately left open.
- The self-hosted WireGuard/AmneziaWG server itself (Oracle Cloud) is
  untouched by this change - it's just no longer anything this app points
  at or depends on.

WHAT CHANGED IN 4.30
-----------------------
- CONTEXT: this app's whole custom VPN engine (DnsVpnService's hand-rolled
  TCP/UDP relay) is being replaced with the official WireGuard protocol -
  a real, standard remote VPN server, not custom packet-relay code. Step
  one is proving the new server works at all, using the official WireGuard
  Android app before writing the real integration into this app.
- PROBLEM HIT: applyPermanentDeviceOwnerProtections() has always
  unconditionally applied UserManager.DISALLOW_CONFIG_VPN as a permanent,
  every-cycle restriction (real tamper-resistance - stops anyone from
  bypassing Blocks by just installing a different VPN app). That's exactly
  right for the finished product, but it also means Android silently
  refuses to authorize ANY third-party VPN app - including the official
  WireGuard app being used to test the replacement server - with no
  in-app toggle to release it. ("VPN service not authorized by user" in
  WireGuard, with no obvious cause, is what this restriction looks like
  from the other app's side.)
- FIXED (temporarily): that one addUserRestriction() call now calls
  clearUserRestriction() instead, so any third-party VPN - specifically
  the WireGuard app - can be authorized again while the new engine is
  being built and tested against the real server.
- THIS IS NOT THE FINAL STATE. Once WireGuard is integrated directly into
  this app (replacing DnsVpnService, same package, same Device Owner),
  DISALLOW_CONFIG_VPN needs to go back to being actively enforced -
  otherwise this specific protection is simply off, permanently, on any
  device running this build. Revert this one line back to
  addUserRestriction() as part of that integration work, not before.

WHAT CHANGED IN 4.29
-----------------------
- ACTUAL ROOT CAUSE FOUND, after 4.21-4.28 chased (and ruled out one by one,
  each with real evidence) thread starvation, a single point of failure in
  DNS forwarding, Block-list drops, network validation, and silently-dropped
  IPv6 - none of which were it. A live diagnostic capture caught the real
  thing directly: a working "Via" browser session (three established
  connections loading Wikipedia, a fourth just-established connection to
  Google) got torn down ALL AT ONCE - "client sent RST" on every single one
  simultaneously - immediately followed by an unrelated Instagram flow
  failing to write to the tunnel with EIO (I/O error). That is not four
  separate app-level failures - it is the VPN tunnel itself being torn down
  and recreated out from under everything using it at that exact instant.
- Background apps (WhatsApp, Instagram) silently reconnect when this
  happens and look completely unaffected. A browser's one-shot page load
  caught mid-flight when it happens just fails outright, with no retry -
  which is exactly the "some things work, some randomly don't" pattern
  reported throughout this app's entire history, on every network, every
  browser, every Android build tested.
- WHY it was restarting: BlockEnforcer.applyVpnLockdownAndServiceState()
  runs on every reapply cycle - every time the app is opened, every
  settings save, AND a periodic safety-net alarm that fires roughly every
  60 seconds whenever any Block exists (see scheduleNextAlarm). Every
  single one of those calls unconditionally called
  DevicePolicyManager.setAlwaysOnVpnPackage() again, even when the value
  being set was byte-for-byte identical to what was already configured.
  Android does not appear to treat a redundant call as a no-op - it
  restarts the VPN network's association regardless, tearing down every
  live connection through it. With a Block configured, this was happening
  roughly once a minute, indefinitely, for as long as the app has existed.
- FIXED: applyVpnLockdownAndServiceState() now reads the CURRENT always-on
  assignment first (DevicePolicyManager.getAlwaysOnVpnPackage()) and only
  calls setAlwaysOnVpnPackage() when it actually needs to change - VPN
  Safety just got toggled, or the assignment is missing/wrong for some
  other reason. On every other reapply cycle (the vast majority of them),
  this is now a no-op, and the tunnel stays up uninterrupted.

WHAT CHANGED IN 4.28
-----------------------
- A live capture ruled out Google-specific behavior directly: loading
  wikipedia.org (unrelated to Google entirely) failed with the exact same
  DNS_PROBE_FINISHED_BAD_CONFIG error, and the log around that moment
  showed www.wikipedia.org queried from TWO different source ports almost
  simultaneously - the classic signature of a dual-stack resolver sending
  separate A (IPv4) and AAAA (IPv6) queries. Both got real answers and
  were forwarded correctly. After that, same pattern as every previous
  capture: no TCP or UDP connection for actually fetching the page ever
  appeared anywhere in the log, for any app attribution, at all.
- NEW LEADING THEORY: this VPN has a known, already-documented limitation
  - it never declares any IPv6 address or route, so IPv6-version packets
  landing on the tun interface were only ever silently counted
  ("ipv6NoiseCount"), never individually logged, on the unverified
  assumption they were just routine background noise (NDP/MLD etc). If a
  browser's dual-stack connection logic gets a real IPv6 answer (which
  this app DOES forward correctly, since DNS is plain UDP/53 regardless of
  A vs AAAA) and prefers IPv6 for the actual page-load connection
  (standard "Happy Eyeballs" behavior in modern browsers when a real
  AAAA record exists), that connection attempt would land in this exact
  silent-count-only path and vanish with zero trace - matching "DNS
  visibly works, the real connection leaves no trace anywhere" exactly,
  for every site/app combination observed so far (Google, Wikipedia,
  Facebook's own CDN, Anthropic's API - all real, modern, IPv6-enabled
  services; WhatsApp/Instagram/Facebook Lite, which don't race IPv6 the
  same way, keep working).
- NOT YET CONFIRMED - this is a theory pending direct evidence, deliberately
  not accompanied by a speculative "fix" this time. FIXED (diagnostic-only):
  what used to be an anonymous, undifferentiated counter now logs the real
  destination, protocol, owning app, and whether it's a TCP SYN (i.e. a
  genuine new connection attempt, not routine housekeeping) for every IPv6
  TCP/UDP packet silently dropped this way. The next capture during a real
  failure should show definitively whether this is actually what's
  happening.

WHAT CHANGED IN 4.27
-----------------------
- NEW: "Start Live Monitoring" button on the Diagnostics screen. Previously
  every diagnostic was a single snapshot - you had to reproduce a failure,
  THEN open Diagnostics, by which point the exact failing moment was often
  already gone or buried under whatever happened after. Live monitoring
  keeps the traffic counters and live log refreshing on-screen every ~1
  second (auto-scrolling to the newest entries) so you can start it BEFORE
  reproducing the problem and watch it happen in real time, and it keeps
  running until you tap "Stop Live Monitoring" - not a fixed duration.
- Only the traffic counters + log actually repeat every second - the real
  network probes (direct DNS queries to CleanBrowsing/Google/Cloudflare,
  the self-test, raw TCP tests) still run ONCE per Start/Run press, not
  every tick. Repeating those every second would hammer external DNS
  servers for no reason, which is a real concern given a live diagnostic
  earlier in this app's history directly caught CleanBrowsing itself
  rate-limiting this network.
- Leaving the Diagnostics screen automatically stops live monitoring
  (it would otherwise leak the screen's views and keep waking the app up
  in the background for nothing) - come back and tap Start again to
  resume watching.

WHAT CHANGED IN 4.26
-----------------------
- 4.25's Block-drop logging came back completely clean (no DROPPED/reaper
  lines at all) on a live diagnostic where Chrome's DNS still succeeded but
  NO TCP or UDP connection for com.android.chrome appeared anywhere else in
  the log - ruling out the Block-list theory directly. Chrome's actual
  page-load connection isn't reaching this app's code at all - not
  mishandled, not dropped, just never arriving - meaning whatever's wrong
  is between Chrome and this app's tun interface, not inside DnsVpnService.
- ADDED: the diagnostic now reports whether Android has actually marked
  the active (VPN) network as VALIDATED (NetworkCapabilities.
  NET_CAPABILITY_VALIDATED), alongside the INTERNET and CAPTIVE_PORTAL
  capabilities. This is a real, separate Android mechanism: the OS runs
  its own connectivity probe on every network including a VPN's, and
  Chrome specifically is known to refuse to load pages over a network
  that hasn't been validated yet, even while DNS lookups and other apps
  may proceed regardless. If VALIDATED=false shows up, that's very likely
  the actual explanation for "DNS/some apps work, Chrome shows nothing" -
  and it would mean the fix is in how/whether this VPN's Builder responds
  to Android's own validation probe, not in packet-by-packet relay logic
  that's already been gone through in detail without finding a bug there.

WHAT CHANGED IN 4.25
-----------------------
- LIKELY REAL FINDING, from reading a live diagnostic with 4.24's new
  app-attribution logging: Chrome's DNS query for google.com appeared in
  the log and succeeded normally (app=com.android.chrome, 540ms, response
  written back to tun) - but NO TCP or UDP connection for Chrome appeared
  anywhere else in the same window. Chrome resolved a real IP and then
  never even attempted to open the actual page-load connection through the
  tunnel, as far as this app's own log could show.
- ROOT CAUSE (found by re-reading dispatchUdp/dispatchTcp with this in
  mind): DNS queries (port 53) are NEVER checked against
  isOwningAppBlocked() at all - only regular TCP connections and non-DNS
  UDP flows are. And when isOwningAppBlocked() DOES return true for one of
  those, the code silently returns with NO log line whatsoever. So if an
  app ends up in an active Block's package list - deliberately, or via the
  auto-add-new-installs feature, which has had at least one real bug
  before (see 4.20) - the exact symptom is: its DNS keeps resolving fine
  (never checked), while every actual connection just vanishes with zero
  trace. That matches every report in this whole troubleshooting session:
  DNS always looked healthy, specific apps' real traffic just disappeared.
- FIXED (diagnostic-only, not yet confirmed as THE root cause): the
  silent Block-drop in dispatchTcp, dispatchUdp, and the idle-session
  reaper now all log which app got dropped and why. The next diagnostic
  taken while something is broken will show definitively whether this is
  what's actually happening.
- IF THIS IS CONFIRMED: the real fix is checking (and if needed, editing)
  the Blocks list in the app itself - Blocks > each Block > its app list -
  to make sure Chrome/whatever isn't in there by accident. This isn't
  something a code patch should silently override, since deliberately
  blocking an app is the entire point of this app's Blocks feature.

WHAT CHANGED IN 4.24
-----------------------
- DIAGNOSTIC IMPROVEMENT, not a fix - because 4.23 didn't actually fix the
  underlying problem. A diagnostic taken right after 4.23 showed the DNS
  pipeline completely healthy (CleanBrowsing resolving in 162ms, every
  query in the log succeeding, no fallback ever needed, self-test passing)
  while Chrome was STILL broken - proving the DNS-forwarding/CleanBrowsing
  theory from 4.23, while real, was never the (or not the only) actual
  cause of "some apps work, others don't". The live log had no way to show
  WHICH app a given packet or query belonged to, so there was no way to
  tell "Chrome's traffic never reached the tunnel at all" apart from "it
  reached the tunnel and something else went wrong" - both looked
  identical in the log.
- ADDED: every DNS query, new TCP connection, and new UDP flow logged by
  DnsVpnService now includes "app=<package name>" (via
  ConnectivityManager.getConnectionOwnerUid - the same per-connection
  owner lookup already used to decide what's blocked, just also used for
  log attribution now). The next diagnostic taken while something is
  actually broken will show directly whether the failing app's own
  traffic is showing up in this log at all - that's the key fact still
  missing to find the real cause.

WHAT CHANGED IN 4.23
-----------------------
- ACTUAL ROOT CAUSE FOUND for "some apps work, others (especially Chrome)
  don't" - and it was never the 4.21/4.22 threading changes at all. A live
  diagnostic caught it directly: the diagnostic's OWN direct probe to
  CleanBrowsing (185.228.168.168, our ONLY upstream DNS resolver at the
  time) timed out after 4s - completely bypassing the VPN and every line of
  code touched in 4.21/4.22 - while the exact same diagnostic's direct
  probes to Google, Cloudflare, and the network's own router DNS all
  succeeded normally. The live pipeline log showed the identical
  SocketTimeoutException happening inside forwardToRealDns at the same
  time. CleanBrowsing was simply unreachable/rate-limited on this network
  at that moment - nothing wrong with this app's code, but this app had
  ZERO fallback: every query that landed in that window just failed
  outright, with no way to recover it. A browser loading a page touches
  many distinct domains per load, so it was far more likely to hit that
  failure window than a low-request-volume app like Facebook Lite - that
  alone fully explains "some apps work, others don't" without needing any
  threading explanation.
- FIXED: forwardToRealDns now tries CleanBrowsing first as before, but on
  timeout/failure automatically retries once against a fallback resolver -
  Cloudflare's Family filter (1.1.1.2, blocks malware + adult content).
  Deliberately NOT a plain unfiltered resolver like 8.8.8.8/1.1.1.1 - a
  fallback should never silently remove the content filtering this app
  exists for. Per-attempt timeout reduced from 5s to 3s so a
  primary-then-fallback worst case stays around 6s instead of a full 10s.
- DiagnosticActivity's "How to read this" section now specifically calls
  out this exact failure pattern (CleanBrowsing direct test fails while
  Google/Cloudflare direct succeed) so it's identifiable at a glance in
  future, instead of needing a full manual read-through of the raw log
  like this time.

WHAT CHANGED IN 4.22
-----------------------
- REAL FIX for a regression 4.21 itself introduced: right after that update,
  reports came in of "Facebook Lite works, but Facebook videos won't play,
  Chrome doesn't work, and other apps sometimes don't work" - worse than
  before, not better. Root cause: 4.21 fixed the old per-UDP-packet raw-
  Thread storm by moving ALL UDP dispatch (DNS queries AND everything else)
  onto one shared 64-thread pool. That merged two very different workloads
  onto the same pool: DNS forwarding, which legitimately blocks for up to
  5s waiting on the upstream resolver, and generic UDP relay dispatch
  (overwhelmingly QUIC/HTTP3 - what Chrome, video streaming, and most
  modern apps actually use for real data transfer), where each task is
  meant to be near-instant. A burst of slow DNS lookups could occupy every
  worker in the shared pool for seconds at a time, and every OTHER queued
  UDP packet - including live QUIC data for a connection Chrome or a video
  player already had open - queued up behind them. Facebook Lite (plain
  HTTP over TCP, which bypasses this pool entirely - TCP is handled
  inline) kept working fine throughout, which is exactly the split that
  was reported.
- FIXED: DNS forwarding and generic UDP relay dispatch now run on two
  SEPARATE bounded pools (DNS_FORWARD_THREADS=32, UDP_DISPATCH_THREADS=64
  in DnsVpnService), chosen via a cheap peek at the UDP destination port
  before the full packet parse. A burst of slow DNS lookups can no longer
  block the fast relay path Chrome/video actually depend on for data, and
  vice versa.
- FIXED (found while investigating the above, not yet reported as its own
  symptom): the UDP dispatch pool being shut down mid-flight (normal VPN
  Safety toggle or service restart) could throw an uncaught
  RejectedExecutionException on the tun-read thread. Android's default
  behavior is to kill the ENTIRE app process on ANY uncaught exception on
  ANY thread, not just that feature - meaning a plain VPN restart could, in
  principle, crash the whole app and briefly take real internet down with
  it while it restarted. Both dispatch pools' submissions are now
  defensively guarded against this.

WHAT CHANGED IN 4.21
-----------------------
- LIKELY REAL FIX for intermittent "Chrome says DNS_PROBE_FINISHED_BAD_CONFIG,
  other apps sometimes don't load" while every diagnostic test (direct DNS,
  the in-process self-test, raw TCP) reports success: DnsVpnService was
  spawning a brand-new raw Thread for EVERY single UDP packet it read off
  the tun interface - not one thread per flow, one thread per packet. That
  includes every DNS query AND every other UDP packet (QUIC/HTTP3, which is
  most of what modern Chrome traffic actually is). A real diagnostic
  session showed 5,000+ UDP packets, meaning 5,000+ raw OS threads created
  in one sitting - and the live pipeline log showed DNS forwards that
  should take ~200-800ms (confirmed by the diagnostic's own direct-probe
  timings to the same upstream) instead taking 3.8-4.1 seconds under load.
  That's thread-scheduling contention, not network latency - enough of it
  that Chrome (and other apps) legitimately give up and report DNS as
  broken outright rather than just slow.
- FIXED: UDP packet dispatch now runs on a bounded fixed thread pool
  (UDP_DISPATCH_THREADS = 64) instead of an unbounded Thread-per-packet.
  One slow upstream DNS response still can't block the next query (the
  original point of dispatching UDP off the tun-read thread at all) -
  concurrency is preserved, just capped instead of unbounded.
- INCREASED the in-app diagnostic log buffer (DiagnosticActivity's live
  pipeline log) from the last 60 events to the last 400. At real traffic
  volumes (thousands of packets per session) 60 entries got overwritten
  within seconds, which meant the exact moment something actually failed
  was almost always already evicted by the time you opened Diagnostics to
  look. 400 gives a much better chance the failure itself is still in the
  visible window.
- Diagnostic report text size increased (12sp -> 15sp) - easier to actually
  read the report on-device without zooming, per direct request.

WHAT CHANGED IN 4.20
-----------------------
- FIXED a real bug: installing an updated version of this app itself,
  then opening it, could add EVERY installed app to EVERY Block. Cause:
  checkForNewlyInstalledApps() compares the currently-installed app list
  against a stored snapshot to detect real new installs (so new apps
  aren't silently exempt from existing Blocks). If that stored snapshot
  was ever read back spuriously empty or corrupted around the moment of
  an app update, literally every app on the phone looked "newly
  installed" at once, and all of it got added to every Block's list.
- The fix doesn't depend on pinning down the exact moment the snapshot
  got corrupted: a real new-install event realistically means one app,
  maybe two or three at once - never dozens. checkForNewlyInstalledApps
  now treats a diff of more than 5 apps appearing "new" simultaneously as
  an unreliable snapshot rather than genuine installs - it re-baselines
  silently and blocks nothing, the same as a real first run.
- IMPORTANT - this only prevents it from happening AGAIN. If it already
  happened on your device from an earlier version, your existing Blocks
  may still have apps in them that were added by this bug rather than by
  you. There's no reliable way to tell those apart after the fact (no
  marker was ever stored for which additions were automatic), so it's
  worth reviewing each Block's app list once and removing anything that
  shouldn't be there.

WHAT CHANGED IN 4.19
-----------------------
- NEW: apps currently in an active Block now have their internet access
  cut directly, not just their ability to be opened. Every new TCP
  connection and UDP flow is checked against the owning app's package
  name (via ConnectivityManager.getConnectionOwnerUid - the same
  mechanism every real per-app-firewall VPN uses, since a raw tun packet
  carries no UID) and silently refused if that app is currently blocked.
  Already-open connections get the same check on every session-reaper
  sweep (every 30s), so a Block starting while an app is mid-connection
  cuts it off shortly after, not just on its next reconnect. This reuses
  BlockEnforcer.computeActiveBlockedPackages() exactly - same Blocks,
  same Holiday Break/Master Safety/Blocks Pause handling as app
  suspension already uses, not a second list that could drift out of
  sync. No new screen - it's just what "this app is in an active Block"
  means now. The check fails OPEN (never blocks) if the UID lookup fails
  for any reason, so a lookup hiccup can't silently break some other
  app's internet.
- REMOVED the separate Play Store domain-blocking layer (play-fe/play.
  googleapis.com via DNS). It's now fully subsumed by the feature above -
  add Play Store to a Block's app list like anything else and its
  internet gets cut the same way every other blocked app's does, which
  is more thorough than the old domain-list approach ever was.

WHAT CHANGED IN 4.18
-----------------------
- REAL FIX for every TCP connection hanging and timing out under 4.17: the
  first diagnostic after 4.17 was actually great news in disguise - 11,724
  real TCP packets and 86 real UDP packets reached the tun interface,
  something that had NEVER happened once in every single diagnostic before
  it (the DNS-only design genuinely never routed other apps' traffic at
  all). But almost every TCP connection logged "protect(socket) returned
  FALSE" and then timed out after 8s.
- Cause: protect(Socket) needs a real underlying file descriptor to mark
  as bypassing the VPN. TcpSession was calling it immediately after `new
  Socket()`, which doesn't allocate a file descriptor until the socket is
  actually bound or connected - so protect() found nothing to protect,
  silently no-opped, and the socket went on to connect UNPROTECTED. Its
  own outbound SYN then got captured by our own 0.0.0.0/0 route and
  looped straight back into our own tun interface instead of reaching the
  real network - hence every connection hanging until timeout, and the
  resulting retry storms that also exhausted the 300-session cap
  (TcpSession now explicitly binds to an ephemeral local port before
  calling protect(), forcing the file descriptor to exist first).
- UDP was never affected by this - `new DatagramSocket()` binds
  immediately at construction (unlike Socket), so its file descriptor
  already exists by the time protect() is called on it. This is exactly
  why DNS (which uses DatagramSocket throughout) kept working perfectly
  through every one of these diagnostics while TCP was completely dead.

WHAT CHANGED IN 4.17
-----------------------
- THE BIG ONE: this VPN is no longer DNS-only. It was proven, over several
  rounds of diagnostics, that a VPN routing only a single /32 DNS address
  gets no real internet access in Android's eyes, isn't trusted, and
  other apps' traffic - DNS included - never actually gets routed through
  it at all (confirmed directly: Play Store failed with "No internet
  connection" the same way Chrome did, with Private DNS completely out of
  the picture by that point). There was no smaller fix available -
  DNS-only and "other apps get real internet" turned out to be mutually
  exclusive on Android.
- The Builder now claims a real default route (0.0.0.0/0) and every kind
  of IPv4 traffic is actually relayed:
  - UDP port 53 (ANY destination, not just our own address - closes a gap
    where an app hardcoding a different DNS server could have bypassed
    filtering) - unchanged pipeline: block-check, NXDOMAIN or forward to
    CleanBrowsing.
  - Other UDP (QUIC/HTTP3 on 443 especially - this is a huge fraction of
    real Chrome traffic) - generic NAT-style relay, see UdpRelaySession.
  - TCP - a real, but deliberately simplified, per-connection relay: see
    TcpSession's class comment for exactly what's simplified (no
    retransmission of data we send - justified, not lazy, since the tun
    interface isn't a real lossy link; no window scaling; pragmatic
    rather than exhaustive connection teardown). Good enough for normal
    browsing, not a from-scratch lwIP replacement.
  - IPv6 still isn't handled (same as always) - this is now equivalent to
    being on a plain IPv4-only network, not the "network considered
    untrustworthy" failure the old design hit.
- New diagnostic counters (real TCP packet count, real UDP packet count,
  other-protocol count) alongside the existing IPv6-noise counter, so the
  diagnostic tool can now show real traffic actually flowing instead of
  only ever showing 100% IPv6 noise and 0 real packets - which is what
  every single diagnostic showed under the old design, no matter what
  else got changed, and was the actual tell the whole time in hindsight.

WHAT CHANGED IN 4.16
-----------------------
- ACTUAL ROOT CAUSE FOUND for "internet totally dead while the VPN is on,"
  and it was never inside DnsVpnService at all. This app had TWO separate,
  independent DNS-enforcement systems: DnsVpnService (the VPN, added
  later) and an older "DNS Lock" feature in the main screen that forced
  system-wide Private DNS into STRICT mode pointed at
  family-filter-dns.cleanbrowsing.org, locked via
  DISALLOW_CONFIG_PRIVATE_DNS. Strict-mode Private DNS does not fall back
  to plain DNS if validation fails - it blocks DNS resolution outright
  (this is exactly what Chrome's DNS_PROBE_FINISHED_BAD_CONFIG means).
  Validating that strict host requires a DNS lookup over whatever path is
  currently active - which, the instant the VPN is running, is the VPN's
  own DNS server - creating a dependency loop between the two systems
  that broke down specifically while the VPN was active. This is why
  every VPN-specific diagnostic always passed in isolation while real
  browsing stayed completely dead.
- REMOVED the DNS Lock feature entirely (button, strict-mode Private DNS
  call, the restriction). It was strictly redundant anyway:
  DnsVpnService already forwards every query that isn't on your own
  Blocked Websites list to that same CleanBrowsing family-filter
  resolver (see UPSTREAM_DNS), so the VPN alone does everything the old
  system did, plus your own custom blocklist on top, which the old
  system couldn't do at all (this is the actual gap that prompted
  building the VPN in the first place).
- Any device that had DNS Lock enabled from an earlier build gets it
  automatically released the next time the app is opened
  (BlockEnforcer.releaseStrictPrivateDnsIfLocked) - Private DNS mode is
  switched to Opportunistic ("Automatic"), which still tries
  DNS-over-TLS but safely falls back to plain DNS instead of blocking
  resolution outright, so this specific failure mode can't recur even
  if something re-locks it by mistake in the future.

WHAT CHANGED IN 4.15
-----------------------
- REAL FIX for "DNS diagnostic passes but the internet is still totally
  dead while the VPN is running": lockdown mode and this VPN's DNS-only
  design were fundamentally incompatible, and had been since lockdown was
  first turned on in 4.7. Lockdown blocks ANY traffic from another app
  that isn't covered by one of this VPN's declared routes - not just
  traffic while the service is fully down - and this VPN, by design, only
  ever routes the single virtual DNS address (10.0.0.2/32), so that
  everything else can bypass the tunnel and reach the real network
  untouched. Under lockdown, "everything else" had nowhere to go and was
  silently dropped for every app except this one - DNS resolution itself
  kept working perfectly (which is why earlier diagnostics looked fine),
  but no actual page could ever load. Lockdown is now OFF (always-on
  stays ON): other apps' non-DNS traffic now correctly falls through to
  the real network exactly as the DNS-only design always assumed. The
  real trade-off, so it's written down: if this service is ever killed,
  DNS now quietly reverts to the network's own unfiltered resolver
  instead of Android blocking all internet outright - fails open, not
  closed. Tamper-resistance still comes from Device Owner (uninstall
  blocked, DISALLOW_CONFIG_VPN, Safe Mode/factory reset blocked) and this
  service relaunching itself (START_STICKY / BootReceiver), same as
  before.
- REBUILT the diagnostic's "Through Self-Control's own VPN" check: it
  used to open a real socket from this app to its own VPN address, which
  can never succeed regardless of whether the VPN is healthy - Android
  excludes the VPN-owning app's own traffic from its own tunnel by
  default, so that probe always timed out even with a perfectly working
  pipeline. It now runs a synthetic query through DnsVpnService's real
  parse/block-check/forward/build-response code in-process instead, which
  actually reflects whether the pipeline itself works.

WHAT CHANGED IN 4.10
-----------------------
- LIKELY REAL FIX for "internet totally dead while VPN runs, restored
  when VPN Safety kills it": the VPN was sending back DNS response
  packets with the UDP checksum deliberately set to 0. This is legal per
  the networking spec (0 officially means "no checksum was computed"),
  but some network stacks silently drop UDP packets that use it anyway,
  even though it's technically allowed - which would explain every
  single DNS query failing identically, not just some. A real,
  correctly-computed checksum is now generated for every response
  packet instead.
- Hardening against reinstalling an older version of the app to bypass
  current protections: android:debuggable is now explicitly set to
  false. Locally-built debug APKs are normally allowed to downgrade
  install over a newer version without restriction - this closes that
  specific door, so a plain reinstall of an older build should now be
  refused the normal way Android refuses version downgrades. Version
  number also bumped so this and future updates are recognized as
  genuinely newer.
- HONEST LIMIT, not fixed and likely not fixable from inside the app: a
  determined attempt to intentionally raise an old build's version
  number and reinstall it, or to directly hand-edit the source code
  to remove a protection and rebuild, cannot be prevented by anything
  in this app - both require having full local source/build control,
  which this app's own owner (the user) genuinely has. This is a
  structural fact about the whole project, not a bug to patch.

WHAT HAPPENED AND WHY THIS VERSION EXISTS
----------------------------------------------
The VPN in v4.7/4.8 broke the user's internet completely after
installing an app from Play Store, with no way back in except
downgrading to an older version of the app (which happened to work
around it, but was a real close call - not a designed recovery path).
This version exists specifically to fix the root cause and give a real,
reliable way back in if anything like this happens again.

WHAT CHANGED IN 4.9
----------------------
- LIKELY ROOT CAUSE FOUND AND FIXED: the VPN was processing DNS queries
  one at a time, in a single line - each one could block for up to 5
  seconds waiting on a slow response before the next query even started
  being read. Installing an app triggers a burst of many DNS lookups at
  once (Play Store's own servers, download infrastructure, several
  Google API domains, all within seconds) - which could back up badly
  enough behind each other to feel exactly like the internet had
  stopped, even though nothing was actually broken, just severely
  queued. Each query is now handled independently instead of in a
  single queue, so a burst like this can't stall everything else.
- REBUILT: VPN Safety is now a REAL kill switch, not a soft pause.
  Engaging it fully STOPS the VPN service AND removes the always-on/
  lockdown assignment at the OS level entirely - meaning normal internet
  access works exactly as if this app had no VPN at all. (The previous
  version kept the VPN running in a "pass-through" mode when Safety was
  engaged - the problem is that doesn't help if the VPN process itself
  is the thing failing, which is what actually happened.) Reachable any
  time, same as Master Safety - not gated by Lock Schedule, since it
  needs to work as a genuine emergency escape hatch.
- FIXED: the app was starting the VPN unconditionally every time the
  main screen opened, which would have silently overridden the kill
  switch the moment you opened the app after engaging it. The VPN's
  actual running state is now only ever decided in one place, and it
  correctly respects VPN Safety.
- VPN Safety changes now take effect immediately when tapped, not on the
  next scheduled check cycle (previously could take up to ~1 minute) -
  important for something meant to work as an emergency switch.

IF THIS EVER HAPPENS AGAIN
------------------------------
Open Self-Control (the app icon works without internet) and tap "VPN
Safety: OFF (tap to shut off the VPN...)" - this should restore normal
internet immediately. If it doesn't, the old downgrade-the-app method
remains a fallback, but should not be needed anymore.

WHAT'S NEW IN 4.8
--------------------
- NEW, ADDITIONAL layer specifically for Play Store: whenever Play Store
  (com.android.vending) is currently in an active Block, the DNS VPN now
  also blocks Play Store's own server domains
  (play-fe.googleapis.com, play.googleapis.com and their subdomains).
  This doesn't replace the existing app-suspension attempt for Play
  Store (which likely fails anyway, since Play Store very probably holds
  Android's protected "package verifier" role) - it's added on top, so
  Play Store being blocked in a Block now has real teeth even though it
  can't be suspended directly.
- This ONLY applies to Play Store specifically, and ONLY while it's
  actually in an active Block right now - it doesn't touch these domains
  at any other time, and doesn't touch any other app or domain.
- Internal refactor to support this: extracted the "which packages are
  currently supposed to be blocked" logic (used to decide suspension)
  into its own reusable method, so this new layer checks the exact same
  live state as Blocks itself, rather than a second, separately-computed
  copy that could drift out of sync.

BETA FEATURE - READ BEFORE INSTALLING
------------------------------------------
This version adds a real, working local VPN that blocks domains from
your Blocked Websites list. This is genuinely new, low-level networking
code that could not be tested live before reaching your phone. Please
treat this as a beta: test carefully, and if anything about your
internet seems wrong after installing, engage VPN Safety immediately
(see below) - it keeps the VPN itself running (required, see below) but
stops it from filtering anything.

WHAT'S NEW IN 4.7
--------------------
- REAL FIX (long overdue): the actual Device Owner protections that were
  the entire point of this project were never implemented until now.
  Fixed: uninstalling Self-Control is now genuinely blocked, Safe Mode
  is blocked, factory reset is blocked. These are permanent, not tied to
  any schedule - they're always in effect.
- NEW: Termux and Shizuku (the tools originally used to grant Device
  Owner) are now automatically suspended - if either is ever reinstalled
  - whenever the Lock Schedule is locked. Closes the specific loophole
  where they could otherwise be used to remove Device Owner again.
- NEW: a real DNS-only VPN (DnsVpnService) now enforces the Blocked
  Websites list. How it works: your phone's DNS queries are routed
  through this VPN (and ONLY DNS queries - port 53 traffic. Everything
  else on your phone bypasses this VPN completely and is unaffected).
  Each query's domain is checked against your Blocked Websites list; if
  it matches, the VPN replies "this doesn't exist" directly (NXDOMAIN) -
  no real lookup happens at all. If it doesn't match, the query is
  forwarded untouched to CleanBrowsing's family-safe DNS resolver, whose
  own adult-content category filtering also applies on top, for free -
  no content-classification logic was built ourselves.
- NEW: the VPN is locked in via Device Owner as "always-on" with
  lockdown enabled - the user cannot disable or reconfigure it via
  Settings, and if it isn't running for any reason, Android blocks ALL
  internet access rather than silently falling back to unprotected
  browsing.
- NEW: "VPN Safety" - a separate one-time-use safety valve from Master
  Safety (governs this VPN specifically). Engaging it does NOT stop the
  VPN itself (that would cut off all internet, given the lockdown
  setting above) - it makes the VPN keep running but pass every query
  through unfiltered. Has its own separate "Delete Forever" button, same
  pattern as Master Safety.
- Blocked Websites domains are matched as whole domains AND their
  subdomains (blocking example.com also blocks sub.example.com), never
  a specific page/path, per the original requirement.

HONEST LIMITS AND KNOWN TRADE-OFFS
---------------------------------------
- This is DNS-level blocking, not full connection/IP-level blocking (a
  deliberate scope reduction agreed on given the real over-blocking risk
  IP-level blocking carries, since many unrelated sites share server
  IPs). A browser using its own encrypted DNS instead of the system
  resolver could in principle bypass this - a known, accepted trade-off.
- Performance: only DNS traffic (small, infrequent packets) passes
  through this VPN - regular browsing/video/etc. traffic is completely
  unaffected and bypasses it entirely, so any speed impact should be
  minimal to unnoticeable.
- The low-level packet parsing (IpV4UdpPacket, DnsMessage) is written
  defensively - any unexpected or malformed packet is safely dropped
  rather than guessed at, and one bad packet cannot crash the whole
  service. Still, this is new networking code without live testing.
- This is a genuinely new, more capable engine sitting ALONGSIDE the
  original DNS Filter (CleanBrowsing Private DNS lock) feature, not a
  replacement - both are left independently intact.

WHAT CHANGED IN 4.6
----------------------
- NEW: "Blocked Websites" screen - add and manage a list of domains,
  intended to always be blocked while the Lock Schedule is locked. Same
  pattern as everywhere else: adding a domain is always allowed,
  removing one is only allowed during an unlocked time.
- NEW: share-to-block. Share any page from a browser, and "Block with
  Self-Control" appears in the share sheet. Tapping it extracts just the
  domain (never the specific page/path) and asks for confirmation before
  adding it to the list.

IMPORTANT - THIS DOES NOT ACTUALLY BLOCK ANYTHING YET
----------------------------------------------------------
This version only manages the LIST of domains. Nothing currently
enforces it - no website in this list is actually blocked on the phone
yet. The existing DNS Filter (CleanBrowsing) is a separate, fixed,
single-provider category filter that cannot have arbitrary custom
domains added to it.

Actually enforcing a custom domain list system-wide (every browser,
every app) requires building a local VPN-based DNS filter from scratch -
real low-level network packet handling. This is a substantial, genuinely
risky piece of work: done carelessly, it's the kind of bug that could
cut off the phone's internet entirely, not just misbehave, and it can't
be fully tested without trying it live on the actual device. This has
deliberately NOT been attempted yet, pending an explicit decision on how
to proceed.

WHAT CHANGED IN 4.5
----------------------
- RENAMED: the app now displays as "Self-Control" instead of "Floating
  Blocker". IMPORTANT: only the display name changed - the underlying
  package identifier (com.mujeer.floatingblocker) is untouched on
  purpose, since Device Owner status is permanently bound to that exact
  identifier. Changing it would have broken Device Owner entirely and
  required the full factory-reset process again.
- DNS Filter fix attempt: found that this app had never declared the
  INTERNET or ACCESS_NETWORK_STATE permissions, in any version. Since
  setting the DNS Filter requires a real live connectivity check to the
  DNS server, this is very likely why it kept failing here specifically
  while working fine through Settings' own Private DNS screen (which
  has full system network access regardless of our app's permissions).
  Both permissions are now declared - please test the DNS Filter button
  again.
- NEW: Wireless Debugging (and other developer debugging features) is
  now automatically locked out entirely whenever the Lock Schedule is
  currently locked, and freed the moment it unlocks - fully automatic,
  no button, tied directly to the Lock Schedule's live state the same
  way everything else it governs works. This specifically closes the
  loophole where Wireless Debugging (via Termux/Shizuku) could
  otherwise be used to remove Device Owner during a locked period.
- NEW: creating a brand new Block now automatically adds it to every
  existing Holiday Break's coverage too - old breaks don't need to be
  manually re-edited every time a new Block is created.
- Confirmed already working as intended, no change needed: newly
  installed apps still get added to every Block's list even during an
  active Holiday Break - they just won't be actively enforced until the
  break ends or that Block's own schedule kicks in, exactly as
  requested.

WHAT CHANGED IN 4.4
----------------------
- FIXED (real root cause found): the v4.3 new-install auto-blocklist
  feature never actually worked, because it relied on a manifest-
  registered receiver for the PACKAGE_ADDED broadcast. Checked directly
  against Android's own official documentation: this specific broadcast
  is explicitly NOT on the short list of broadcasts exempted from
  Android 8.0+'s background-execution restrictions, and this app's
  targetSdkVersion (29) is well past the threshold where that
  restriction applies. The receiver was correctly written and correctly
  registered - it just could never have been delivered, on any phone,
  not just this one. This wasn't a ColorOS-specific quirk.
- REPLACED with a reliable, broadcast-free approach: every time the app
  wakes up anyway (at every Block/Holiday-Break transition, and now at
  minimum every ~1 minute even if nothing else is scheduled - Android's
  own documented ceiling for this kind of alarm during normal use, and
  automatically throttled back to roughly every 15 minutes by Android's
  own Doze system during genuine idle periods, at no extra battery cost
  we need to manage ourselves), it compares the current list of
  installed apps against a saved snapshot from the last check. Anything
  new gets added to every existing Block's block-list immediately, same
  as before - just detected differently. This doesn't depend on any
  broadcast being delivered at all, so it isn't subject to the
  restriction that broke the old approach, or to any OEM-specific
  background restrictions either.
- Detection is no longer instant (up to ~1 minute's delay while the
  phone is actively in use, longer if the phone is genuinely idle at
  the moment) - an honest trade-off for something that actually works
  reliably, versus something instant that silently never fired at all.

WHAT CHANGED IN 4.3
----------------------
- DNS Filter failures now show a specific reason instead of one generic
  message: whether the live connection check to the DNS server itself
  failed at that moment, whether the setting failed to apply for some
  other reason, or the exact error if something threw an exception. This
  is meant to help pin down the "Could not set the DNS Filter" issue
  precisely instead of guessing at the cause.
- Holiday Breaks now clean themselves up automatically once their end
  time has passed - they disappear from the list on their own, checked
  during the same alarm cycle that already wakes the app up at every
  Block/Break transition.
- NEW: any newly installed app is now automatically added to EVERY
  existing Block's block-list, the moment it's installed - not just
  currently-active Blocks. This closes the "just install something new
  to get around it" loophole at the root. The only way to actually use
  a newly-installed app again is going into whichever Block(s) it landed
  in and removing it from there - the same removal flow as any other
  blocked app, gated by the Lock Schedule the same way as always. This
  does not react to updates of apps already on the phone, and never
  touches Floating Blocker's own package.
- NOT added: a blanket "block all new installs" restriction
  (DISALLOW_INSTALL_APPS). This would also have blocked AIDE's own
  ability to install new builds of Floating Blocker itself - a real
  conflict with your own workflow - and the auto-block-on-install
  feature above already covers the underlying goal without that
  trade-off.

WHAT CHANGED IN 4.2
----------------------
- NEW: "DNS Filter" lock on the main screen. Uses Device Owner's real,
  dedicated API for this (setGlobalPrivateDnsModeSpecifiedHost +
  DISALLOW_CONFIG_PRIVATE_DNS) to set Private DNS to CleanBrowsing's
  Family Filter (family-filter-dns.cleanbrowsing.org, blocks adult
  content) AND lock the Private DNS settings screen so it genuinely
  cannot be changed through Settings at all - not just harder, actually
  greyed out. Requires Android 10+ (this phone qualifies).
  - Turning this ON is always allowed, any time - it only adds
    restriction.
  - Turning it OFF (unlocking, so it CAN be changed again) is only
    allowed outside a locked Lock Schedule period - same rule as
    everything else in this app that removes restriction. This was a
    deliberate choice: a permanent, un-pausable content lock felt too
    risky if something ever needed fixing, so it's tied to the same
    schedule as Blocks instead. Note this is NOT affected by Master
    Safety or Blocks Pause - those only touch Blocks enforcement, not
    this.
- NEW: "Battery Optimization" exemption button on the main screen.
  Requests Android's standard exemption from Doze/battery restrictions
  for Floating Blocker itself, so ColorOS is less likely to restrict it
  in the background. Honest note: this still shows one standard system
  confirmation popup asking you to allow it - Device Owner does not make
  this fully silent, despite what some sources claim. It's a one-time
  tap, not automatic.
- Confirmed and left unchanged: the AlarmManager scheduling already in
  place (setExactAndAllowWhileIdle) already matches Android's own
  recommended approach for reliable scheduled wake-ups - no changes
  needed there.

WHAT CHANGED IN 4.1
----------------------
- REMOVED: the "Release Device Owner" button entirely, per request. There
  is now no in-app way to give up Device Owner status at all - the only
  way to remove it is the full external process (shell command via
  Termux/Shizuku, or a factory reset).
- NEW: "Holiday Breaks" - schedule a one-time (non-recurring) date+time
  window ahead of time, during which specific Blocks you choose are
  paused. Add as many as you want, each with its own start date+time,
  end date+time (can span multiple days), and its own choice of which
  Block(s) it affects.
  - Creating a Holiday Break is only allowed during an unlocked Lock
    Schedule time (same idea as the Pause button - it's scheduling
    restriction being lifted, so it's gated the same way).
  - Cancelling a Holiday Break early is always allowed, any time - since
    that restores restriction sooner, not later.
  - A Block on an active Holiday Break is treated as fully inactive
    for that window, regardless of its own normal schedule.

REQUIRES DEVICE OWNER TO ALREADY BE ACTIVE
---------------------------------------------
This build assumes you've already gone through the separate factory
reset + Device Owner setup guide, and confirmed it worked (dpm
list-owners shows Floating Blocker). If you haven't done that yet, this
app's Blocks feature will do nothing - the main screen will show
"Device Owner: NOT ACTIVE" until that's done.

WHAT CHANGED IN 4.0 - A FULL REBUILD
---------------------------------------
The whole detection-and-react approach (accessibility service, polling
every half second, back-pressing apps closed, the floating-window
overlay) is GONE. None of it is needed anymore. Instead:

- Blocked apps are suspended directly at the OS level using Device
  Owner's setPackagesSuspended(). A suspended app simply cannot be
  opened at all - Android shows its own "not available" message. There
  is nothing to detect and nothing to react to, because the app never
  launches in the first place. This also means floating/multitasking
  windows are a non-issue now - an app that can't launch can't float
  either.
- No more continuous background service or polling loop. AlarmManager
  schedules exactly one wake-up at a time, for the next moment any
  Block's schedule is due to start or end. The phone is otherwise
  completely idle in between - much lighter on battery.
- DROPPED ENTIRELY: Settings-blocking, the Lock Schedule's old job of
  gating Settings access, and the Protection switch. These only ever
  existed to stop you from disabling the app via Settings - Device
  Owner now protects the app itself directly and properly, so the old
  workaround is unnecessary.
- DROPPED PERMISSIONS: Accessibility Service, Usage Access, Display
  over other apps. None of these are used anymore. The only permission
  this app now needs is Device Owner itself.
- Lock Schedule's job changed: it now exists purely to stop Blocks
  from being WEAKENED. Deleting a Block, removing an app from one, or
  removing a time range, are only allowed outside a locked period.
  Adding a new Block, or adding an app or time range to an existing
  one, is ALWAYS allowed, any time - since those only ever add
  restriction, never remove it.
- NEW: real OS-level protection for Floating Blocker itself - blocking
  uninstall, disable, force-stop, Safe Mode, and factory reset. This
  was the actual point of the whole Device Owner project: every other
  protection in this app was pointless if the app itself could just be
  removed.
- NEW: "Release Device Owner" button on the main screen. Usable any
  time, no lock restriction, with a confirmation prompt. This
  permanently gives up ALL of the above protection - the only way back
  is redoing the full factory reset + setup process from scratch. It's
  a deliberate one-time escape hatch, not a toggle.
- Master Safety and Blocks Pause/Continue work the same as before,
  just now mean "un-suspend everything" / "re-suspend based on current
  schedule" instead of "stop/start watching."

IMPORTANT - AFTER GRANTING DEVICE OWNER
-------------------------------------------
If you haven't already: uninstall Termux and Shizuku from the phone
once Device Owner is confirmed active. They were only ever needed to
grant it in the first place - leaving them installed means Device
Owner could be removed the same way it was granted, without ever
touching Settings.

HONEST LIMITS
----------------
- Settings itself almost certainly still can't be suspended even by a
  Device Owner (it's a protected core system app, same category as the
  default phone dialer) - which is exactly why Settings-blocking was
  dropped rather than rebuilt on top of Device Owner. This isn't a gap
  we're planning to close; it's an accepted trade-off.
- Duration-based blocks (e.g. "block after 2 hours of use" rather than
  a fixed schedule) are not supported - that would need Usage Access
  back, which was deliberately dropped. Only fixed day/time schedules
  are supported for now.
- This is a fresh-install-relevant change to the app's own logic (not
  its stored data format - Blocks/Lock Schedule/Master Safety storage
  is unchanged from before), but it does require Device Owner to
  already be active for Blocks to do anything at all.

SETUP ORDER
--------------
1. Confirm Device Owner is active (should already be done via the
   separate setup guide)
2. Open the app - main screen should show "Device Owner: ACTIVE"
3. Go to Lock Schedule and add your first schedule (optional - Blocks
   work fine with an empty Lock Schedule too, it just means Block
   editing is never restricted)
4. Go to Blocks and create your Blocks - each with its own days/times
   and its own list of apps to block
5. Leave Emergency Safety untouched unless something's genuinely wrong
6. Leave "Release Device Owner" alone unless you deliberately want to
   give up all of this protection permanently
