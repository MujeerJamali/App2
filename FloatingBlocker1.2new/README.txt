Floating Blocker - version 4.17 (full-traffic VPN: real TCP/UDP relay, not DNS-only)
===================================================================================

Floating Blocker - version 4.18 (TCP relay was connecting unprotected - fixed)
===================================================================================

Floating Blocker - version 4.19 (per-app internet cutoff, Play Store block removed)
===================================================================================

Floating Blocker - version 4.20 (fixed: app updates could mass-add everything to every Block)
===================================================================================

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
