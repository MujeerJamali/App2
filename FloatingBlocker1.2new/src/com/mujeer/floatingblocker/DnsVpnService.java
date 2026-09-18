package com.mujeer.floatingblocker;

import android.net.VpnService;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * FULL-TRAFFIC VPN with DNS filtering built in. This was originally a
 * DNS-only VPN (routing just a single fake DNS address, letting everything
 * else bypass the tunnel) - that design turned out to be fundamentally
 * incompatible with how Android treats VPNs: a network that only ever
 * claims one /32 address gets no real internet access, isn't trusted by
 * the OS, and other apps' traffic - DNS included - never actually gets
 * routed through it at all. See README.txt's 4.17 entry for the full
 * story; the short version is every other kind of traffic now gets a real
 * default route and is actually relayed, not just DNS.
 *
 * What's captured and how each is handled:
 * - UDP port 53 (to ANY destination, not just our own virtual DNS address -
 *   this closes a real gap where an app hardcoding a different DNS server
 *   could have bypassed filtering entirely): parsed, checked against the
 *   blocked-websites list, answered with a local NXDOMAIN if blocked, or
 *   forwarded to CleanBrowsing (UPSTREAM_DNS), falling back to Cloudflare
 *   Family (UPSTREAM_DNS_FALLBACK) if that times out/fails, and the real
 *   answer relayed back - see processPacket() and forwardToRealDns() below.
 * - Everything else UDP (QUIC/HTTP3 on 443, and anything else): generic
 *   NAT-style relay - see UdpRelaySession.
 * - TCP: relayed via a locally-terminated TCP connection per flow - see
 *   TcpSession for the full explanation and its documented
 *   simplifications (no retransmission of data we send, no window
 *   scaling, pragmatic rather than RFC-exhaustive connection teardown).
 * - IPv6: still not handled at all (no IPv6 address/route ever declared)
 *   - Android's documented default for an unclaimed address family is to
 *     block it rather than let it bypass, so IPv6 traffic from every app
 *     is silently dropped. This is the same as being on a plain IPv4-only
 *     network (many real cellular networks work exactly this way) - apps
 *     needing IPv6 fall back to IPv4 via normal Happy Eyeballs behavior,
 *     this is not the "network considered untrustworthy" failure mode the
 *     old DNS-only design hit.
 *
 * Locked in via Device Owner as an always-on VPN (NOT lockdown - see
 * BlockEnforcer.applyVpnLockdownAndServiceState for why). Practical
 * effect: if this service ever isn't running, other apps just get normal,
 * unfiltered internet rather than Android blocking everything outright.
 * Tamper resistance instead comes from Device Owner restrictions
 * (uninstall blocked, DISALLOW_CONFIG_VPN) and this service relaunching
 * itself (START_STICKY / BootReceiver).
 *
 * VPN Safety is a real kill switch (see BlockEnforcer). Engaging it fully
 * stops this service AND releases the always-on assignment at the OS
 * level, so normal internet works exactly as if this app had no VPN at
 * all.
 *
 * Separate, additional layer: any app currently in an active Block (see
 * Block.java/BlockEnforcer.computeActiveBlockedPackages) has its internet
 * access cut here too, not just its ability to be opened - every TCP
 * connection attempt and every UDP flow is checked against the owning
 * app's package name (via ConnectivityManager.getConnectionOwnerUid) and
 * silently dropped if that app is currently blocked. This reuses the
 * exact same "what's blocked right now" logic BlockEnforcer already uses
 * to decide what to suspend - no separate app list, no separate screen.
 */
public class DnsVpnService extends VpnService implements Runnable {

    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String UPSTREAM_DNS = "185.228.168.168"; // CleanBrowsing Family Filter
    // Used ONLY if the primary times out/fails. Cloudflare's Family filter
    // (malware + adult content) - deliberately not a plain unfiltered
    // resolver like 8.8.8.8/1.1.1.1, so a fallback never silently drops the
    // content filtering this app exists for. A single upstream with no
    // fallback at all is a real single point of failure: if CleanBrowsing
    // is temporarily unreachable/rate-limited on a given network (seen for
    // real - a live diagnostic showed direct probes to CleanBrowsing timing
    // out while Google/Cloudflare/the network's own DNS all succeeded),
    // every query that happens to land in that window just fails outright,
    // with no way to recover it. A page loading many distinct domains
    // (a browser) is far more likely to hit that window than a
    // low-request-volume app, which is exactly the "some apps work, others
    // don't" pattern this was causing.
    private static final String UPSTREAM_DNS_FALLBACK = "1.1.1.2";

    private static final int MAX_TCP_SESSIONS = 300;
    private static final int MAX_UDP_SESSIONS = 300;
    private static final long TCP_IDLE_TIMEOUT_MS = 10 * 60 * 1000; // safety net only - normal connections close via FIN/RST well before this
    private static final long UDP_IDLE_TIMEOUT_MS = 2 * 60 * 1000;
    private static final long REAP_INTERVAL_MS = 30 * 1000;

    // UDP packet dispatch runs on TWO separate bounded pools, not one -
    // this split matters and used to be a single shared pool, which was
    // itself a real bug (see below).
    //
    // - DNS_FORWARD_THREADS: only for port-53 queries. Each one blocks for
    //   up to 5s waiting on the upstream resolver (forwardToRealDns) - that
    //   blocking is inherent and fine, DNS query volume from all apps
    //   combined is naturally much lower than total UDP packet volume.
    // - UDP_DISPATCH_THREADS: everything else - overwhelmingly QUIC/HTTP3,
    //   which is most of what Chrome, video streaming, and most modern
    //   apps actually use for real data transfer. Each task here is meant
    //   to be near-instant (a session lookup + non-blocking send).
    //
    // These must NOT share one pool. They did, briefly (a single 64-thread
    // pool for all UDP packets), fixing an earlier bug where every UDP
    // packet got its own brand-new raw Thread (thousands of them per
    // session, causing real scheduling contention). But mixing DNS's
    // inherently-blocking work into the SAME pool as the high-volume,
    // latency-sensitive relay dispatch work created a worse bug: a burst of
    // slow DNS lookups could occupy every worker for up to 5s each, and
    // every OTHER queued UDP packet - including live QUIC data for a
    // connection Chrome or a video player already had open - had to wait
    // behind them. TCP-only/lighter apps (e.g. Facebook Lite, which
    // bypasses this pool entirely - TCP is dispatched inline, see run())
    // kept working fine while QUIC-heavy apps stalled completely. Two
    // separate pools means a burst of slow DNS lookups can never block the
    // fast relay path Chrome/video actually depend on for data, and vice
    // versa.
    private static final int DNS_FORWARD_THREADS = 32;
    private static final int UDP_DISPATCH_THREADS = 64;

    // In-memory diagnostic trail of the most recent queries handled by this
    // service, so DiagnosticActivity can show exactly what happened inside
    // the real pipeline for a real query - there's no practical logcat
    // access on this device, so this is the only way to see past "it
    // failed" into "it failed HERE, with THIS exception".
    private static final int MAX_LOG_ENTRIES = 400;
    private static final java.util.LinkedList<String> recentLog = new java.util.LinkedList<String>();
    private static final java.util.concurrent.atomic.AtomicInteger ipv6NoiseCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger totalPacketsRead = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger tcpPacketsCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger udpPacketsCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger otherProtocolCount = new java.util.concurrent.atomic.AtomicInteger(0);

    static void log(String message) {
        synchronized (recentLog) {
            recentLog.addLast(new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(new java.util.Date()) + "  " + message);
            while (recentLog.size() > MAX_LOG_ENTRIES) {
                recentLog.removeFirst();
            }
        }
    }

    /** Snapshot of the most recent packet-handling activity, oldest first. Safe to call from any thread/process context within this app. */
    public static java.util.List<String> getRecentLog() {
        synchronized (recentLog) {
            return new java.util.ArrayList<String>(recentLog);
        }
    }

    /** Total packets read from the tun fd since this service instance started (survives log-buffer eviction). */
    public static int getTotalPacketsRead() {
        return totalPacketsRead.get();
    }

    /** How many of those were routine IPv6 background chatter (NDP/MLD etc.), filtered out of the detailed log to avoid drowning out real DNS activity. */
    public static int getIpv6NoiseCount() {
        return ipv6NoiseCount.get();
    }

    /** How many real TCP packets (any flow, any app) have been captured and relayed since this service instance started. */
    public static int getTcpPacketsCount() {
        return tcpPacketsCount.get();
    }

    /** How many real UDP packets (DNS and everything else) have been captured and relayed since this service instance started. */
    public static int getUdpPacketsCount() {
        return udpPacketsCount.get();
    }

    /** Anything that's neither IPv4 UDP, IPv4 TCP, nor IPv6 (ICMP echo/ping is the common case) - not handled at all, just counted so it's visible rather than silently mysterious. */
    public static int getOtherProtocolCount() {
        return otherProtocolCount.get();
    }

    private static String hexDump(byte[] data, int length) {
        int n = Math.min(length, 16);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(String.format(java.util.Locale.US, "%02X ", data[i]));
        }
        return sb.toString().trim();
    }

    /**
     * Logs what's actually inside an IPv6 packet that just got silently
     * dropped (see the call site's comment for why this exists). Only logs
     * TCP/UDP ones - routine ICMPv6 NDP/MLD housekeeping (next-header
     * anything else) is genuinely just noise and would drown out the
     * signal. Best-effort/defensive like every other parser in this app -
     * never throws, worst case just doesn't log anything for a malformed
     * packet.
     */
    private void logIpv6Packet(byte[] data, int length) {
        try {
            if (length < 40) {
                return; // shorter than a fixed IPv6 header - not worth a log line
            }
            int nextHeader = data[6] & 0xFF;
            if (nextHeader != ChecksumUtil.PROTOCOL_TCP && nextHeader != ChecksumUtil.PROTOCOL_UDP) {
                return; // ICMPv6 etc. - routine housekeeping, not an app's data traffic
            }
            if (length < 44) {
                return; // not enough room for even a source/dest port pair
            }
            byte[] src = new byte[16];
            byte[] dest = new byte[16];
            System.arraycopy(data, 8, src, 0, 16);
            System.arraycopy(data, 24, dest, 0, 16);
            int srcPort = ((data[40] & 0xFF) << 8) | (data[41] & 0xFF);
            int destPort = ((data[42] & 0xFF) << 8) | (data[43] & 0xFF);

            String protoName = (nextHeader == ChecksumUtil.PROTOCOL_TCP) ? "TCP" : "UDP";
            String extra = "";
            if (nextHeader == ChecksumUtil.PROTOCOL_TCP && length >= 40 + 14) {
                int flags = data[40 + 13] & 0xFF;
                if ((flags & 0x02) != 0 && (flags & 0x10) == 0) {
                    extra = " SYN (new connection attempt)";
                }
            }
            String owningApp = describeOwningApp(nextHeader, src, srcPort, dest, destPort);
            log("[IPv6] " + protoName + " app=" + owningApp + " -> [" + ipv6ToString(dest) + "]:" + destPort
                    + extra + " - NOT relayed, this VPN has no IPv6 route");
        } catch (Exception e) {
            // Best effort - a bug in this diagnostic logging must never break the tun read loop itself.
        }
    }

    private static String ipv6ToString(byte[] addr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            int word = ((addr[i] & 0xFF) << 8) | (addr[i + 1] & 0xFF);
            sb.append(Integer.toHexString(word));
        }
        return sb.toString();
    }

    private ParcelFileDescriptor vpnInterface;
    private Thread workerThread;
    private Thread reaperThread;
    private volatile boolean running = false;
    private FileOutputStream tunOut;
    private java.util.concurrent.ExecutorService udpDispatchExecutor;
    private java.util.concurrent.ExecutorService dnsForwardExecutor;

    private final Object tcpSessionsLock = new Object();
    private final Map<String, TcpSession> tcpSessions = new HashMap<String, TcpSession>();
    private final Object udpSessionsLock = new Object();
    private final Map<String, UdpRelaySession> udpSessions = new HashMap<String, UdpRelaySession>();

    // Live reference to whatever instance of this service is currently
    // running, so DiagnosticActivity can drive a self-test through the
    // real, running pipeline. Deliberately NOT used for anything other
    // than the self-test below - the real tun read loop never goes
    // through this reference.
    private static volatile DnsVpnService runningInstance;

    /** The currently running instance, or null if the service isn't started. Used only by the in-process self-test. */
    public static DnsVpnService getRunningInstance() {
        return runningInstance;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startVpn();
        return START_STICKY;
    }

    private synchronized void startVpn() {
        if (vpnInterface != null) {
            return; // already running
        }
        try {
            Builder builder = new Builder();
            builder.setSession(getString(R.string.app_name) + " DNS Filter");
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addDnsServer(VPN_ADDRESS);
            builder.addRoute("0.0.0.0", 0); // full traffic - see class comment for why a DNS-only route can't work on Android
            builder.setMtu(1500);
            // Deliberately no addAllowedApplication()/addDisallowedApplication() call here.
            // Calling addAllowedApplication even once switches the Builder into allow-list
            // mode, where ONLY the named app(s) are routed through the VPN and every other
            // app is excluded from it entirely - it would silently exclude every real app
            // from filtering, which defeats the entire point. The one consequence of leaving
            // this alone is that this app's OWN traffic is (by Android's normal default
            // policy) not routed through its own tunnel - that's expected, harmless, and
            // exactly why the self-test below runs in-process instead of over a real socket.
            vpnInterface = builder.establish();
        } catch (Exception e) {
            vpnInterface = null;
        }
        if (vpnInterface == null) {
            return; // could not establish - nothing more to do here
        }
        running = true;
        runningInstance = this;
        udpDispatchExecutor = java.util.concurrent.Executors.newFixedThreadPool(UDP_DISPATCH_THREADS);
        dnsForwardExecutor = java.util.concurrent.Executors.newFixedThreadPool(DNS_FORWARD_THREADS);
        workerThread = new Thread(this);
        workerThread.start();
        reaperThread = new Thread(new Runnable() {
            @Override
            public void run() {
                reapIdleSessions();
            }
        }, "session-reaper");
        reaperThread.start();
    }

    @Override
    public void run() {
        final FileInputStream in;
        try {
            in = new FileInputStream(vpnInterface.getFileDescriptor());
            tunOut = new FileOutputStream(vpnInterface.getFileDescriptor());
        } catch (Exception e) {
            return;
        }

        byte[] buffer = new byte[32767];
        while (running) {
            int length;
            try {
                length = in.read(buffer);
            } catch (Exception e) {
                break; // interface closed or similar - stop cleanly
            }
            if (length <= 0) {
                continue;
            }
            totalPacketsRead.incrementAndGet();

            if (((buffer[0] & 0xFF) >> 4) == 6) {
                // This VPN never adds an IPv6 address/route, so these are never
                // relayed - see class comment. Previously assumed to be routine
                // background noise (NDP/MLD etc.) and only ever counted, never
                // individually logged. That assumption was never actually
                // verified against a real browser failure: if an app's dual-stack
                // resolver gets a real AAAA answer (which we DO forward correctly,
                // since DNS is plain UDP/53 regardless of record type) and prefers
                // IPv6 for the actual connection (standard Happy-Eyeballs
                // behavior), that connection attempt would land here and vanish
                // silently - DNS visibly working, the real connection leaving
                // zero trace anywhere, which is exactly the symptom this was
                // added to actually test rather than assume away.
                ipv6NoiseCount.incrementAndGet();
                logIpv6Packet(buffer, length);
                continue;
            }

            int protocol = IpV4UdpPacket.peekProtocol(buffer, length);
            final byte[] packetCopy = new byte[length];
            System.arraycopy(buffer, 0, packetCopy, 0, length);

            if (protocol == ChecksumUtil.PROTOCOL_TCP) {
                tcpPacketsCount.incrementAndGet();
                // Deliberately handled INLINE on this same tun-reading thread, not
                // dispatched to a new ad-hoc thread per packet like UDP below. tun
                // reads are already strictly in order; a new unmanaged thread per
                // packet would let thread scheduling reorder two packets belonging
                // to the SAME TCP connection, which TCP cannot tolerate. This call
                // only does a fast map lookup/insert and queues the packet onto
                // that flow's own dedicated ordered thread (see TcpSession) - it
                // never blocks on real socket I/O itself, so the tun read loop
                // stays responsive.
                dispatchTcp(packetCopy, length);
            } else if (protocol == ChecksumUtil.PROTOCOL_UDP) {
                udpPacketsCount.incrementAndGet();
                // Routed to one of two separate pools based on a cheap peek
                // at the destination port - see DNS_FORWARD_THREADS/
                // UDP_DISPATCH_THREADS' comment for why DNS forwarding and
                // generic relay dispatch must never share one pool.
                boolean isDns = IpV4UdpPacket.peekUdpDestPort(buffer, length) == 53;
                java.util.concurrent.ExecutorService executor = isDns ? dnsForwardExecutor : udpDispatchExecutor;
                try {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                dispatchUdp(packetCopy, packetCopy.length);
                            } catch (Exception e) {
                                log("UNCAUGHT in dispatchUdp: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                            }
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // Pool already shut down (service stopping) - drop this one packet rather
                    // than let an uncaught exception here kill this thread (and, per Android's
                    // default uncaught-exception handling, the entire app process with it).
                }
            } else {
                otherProtocolCount.incrementAndGet(); // ICMP echo/ping etc. - not handled, just counted so it's visible rather than mysterious
            }
        }
    }

    /** Fast, non-blocking: finds or creates the TcpSession for this flow and queues the packet onto it. Runs on the main tun-read thread - see the comment where this is called from run(). */
    private void dispatchTcp(byte[] rawPacket, int length) {
        TcpPacket probe = TcpPacket.parse(rawPacket, length);
        if (probe == null) {
            return; // malformed - drop
        }
        String key = probe.sourcePort + ">" + NetUtil.ipToString(probe.destIp) + ":" + probe.destPort;
        TcpSession session;
        synchronized (tcpSessionsLock) {
            session = tcpSessions.get(key);
            if (session == null) {
                if (!probe.hasFlag(TcpPacket.FLAG_SYN) || probe.hasFlag(TcpPacket.FLAG_ACK)) {
                    return; // not a new-connection SYN and we have no session for it - stray packet for an already-closed connection, safe to drop
                }
                if (tcpSessions.size() >= MAX_TCP_SESSIONS) {
                    log("TCP session limit reached (" + MAX_TCP_SESSIONS + ") - dropping new connection to " + NetUtil.ipToString(probe.destIp) + ":" + probe.destPort);
                    return;
                }
                if (isOwningAppBlocked(ChecksumUtil.PROTOCOL_TCP, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort)) {
                    log("[TCP " + key + "] DROPPED - app=" + describeOwningApp(ChecksumUtil.PROTOCOL_TCP, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort) + " is currently in an active Block");
                    return; // owning app is currently in an active Block - no internet for it
                }
                log("[TCP " + key + "] new connection, app=" + describeOwningApp(ChecksumUtil.PROTOCOL_TCP, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort));
                session = new TcpSession(this, key, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort, tunOut);
                tcpSessions.put(key, session);
                session.start();
            }
        }
        session.enqueue(rawPacket, length);
    }

    /** Handles one UDP datagram: DNS (any destination, port 53) goes through the existing filter pipeline; everything else goes through the generic NAT relay. Runs on its own short-lived thread (see run()). */
    private void dispatchUdp(byte[] rawPacket, int length) throws Exception {
        IpV4UdpPacket probe = IpV4UdpPacket.parse(rawPacket, length);
        if (probe == null) {
            log("DROP: packet did not parse as IPv4/UDP (len=" + length + ") first bytes=" + hexDump(rawPacket, length));
            return;
        }

        if (probe.destPort == 53) {
            byte[] responsePacket = processPacket(rawPacket, length);
            if (responsePacket != null) {
                writeToTun(responsePacket);
            }
            return;
        }

        String key = probe.sourcePort + ">" + NetUtil.ipToString(probe.destIp) + ":" + probe.destPort;
        UdpRelaySession session;
        synchronized (udpSessionsLock) {
            session = udpSessions.get(key);
            if (session == null) {
                if (udpSessions.size() >= MAX_UDP_SESSIONS) {
                    log("UDP session limit reached (" + MAX_UDP_SESSIONS + ") - dropping new flow to " + NetUtil.ipToString(probe.destIp) + ":" + probe.destPort);
                    return;
                }
                if (isOwningAppBlocked(ChecksumUtil.PROTOCOL_UDP, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort)) {
                    log("[UDP " + key + "] DROPPED - app=" + describeOwningApp(ChecksumUtil.PROTOCOL_UDP, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort) + " is currently in an active Block");
                    return; // owning app is currently in an active Block - no internet for it
                }
                UdpRelaySession newSession = new UdpRelaySession(this, key, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort, tunOut);
                if (!newSession.start()) {
                    return; // couldn't open/protect the relay socket - drop this datagram
                }
                log("[UDP " + key + "] new flow, app=" + describeOwningApp(ChecksumUtil.PROTOCOL_UDP, probe.sourceIp, probe.sourcePort, probe.destIp, probe.destPort));
                udpSessions.put(key, newSession);
                session = newSession;
            }
        }
        session.sendToRemote(probe.payload, probe.payloadLength);
    }

    /** Called by TcpSession when it closes, to remove itself from the session map. */
    public void removeTcpSession(String key) {
        synchronized (tcpSessionsLock) {
            tcpSessions.remove(key);
        }
    }

    /** Called by UdpRelaySession when it closes, to remove itself from the session map. */
    public void removeUdpSession(String key) {
        synchronized (udpSessionsLock) {
            udpSessions.remove(key);
        }
    }

    /**
     * Is the app that owns this specific connection currently in an active
     * Block? Uses ConnectivityManager.getConnectionOwnerUid, which Android
     * grants specifically to the VPN app a connection's traffic belongs to
     * (see the class comment - this is the standard mechanism every real
     * per-app-firewall VPN uses, since a raw tun packet has no UID
     * attached to it directly). Reuses BlockEnforcer's exact
     * "what's blocked right now" logic - same Blocks, same Holiday Break/
     * Master Safety/Blocks Pause handling, no separate list to fall out of
     * sync with the app-suspension side of blocking.
     *
     * Fails OPEN: if the UID lookup fails, returns no owner, or the owning
     * package can't be resolved for any reason, this returns false (not
     * blocked) rather than risk cutting off traffic we can't actually
     * attribute. Getting this wrong in the other direction would mean a
     * lookup hiccup silently breaks some other app's internet, which is a
     * worse failure than occasionally missing an enforcement window.
     */
    /**
     * Best-effort human-readable name of the app that owns this connection
     * (e.g. "com.android.chrome"), for LOG ATTRIBUTION ONLY - never used for
     * any blocking decision (see isOwningAppBlocked for that). Added
     * specifically because the diagnostic log previously showed packets and
     * queries with no indication of which app they belonged to, making it
     * impossible to tell "this app's traffic never reached the tunnel at
     * all" apart from "it reached the tunnel and something else went
     * wrong" - the two look identical without this. Returns "unknown" if
     * the owning uid/package can't be determined for any reason.
     */
    private String describeOwningApp(int protocol, byte[] localIp, int localPort, byte[] remoteIp, int remotePort) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return "unknown";
            }
            java.net.InetSocketAddress local = new java.net.InetSocketAddress(InetAddress.getByAddress(localIp), localPort);
            java.net.InetSocketAddress remote = new java.net.InetSocketAddress(InetAddress.getByAddress(remoteIp), remotePort);
            int uid = cm.getConnectionOwnerUid(protocol, local, remote);
            if (uid < 0) {
                return "unknown";
            }
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages == null || packages.length == 0) {
                return "uid:" + uid;
            }
            return packages[0];
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isOwningAppBlocked(int protocol, byte[] localIp, int localPort, byte[] remoteIp, int remotePort) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            java.net.InetSocketAddress local = new java.net.InetSocketAddress(InetAddress.getByAddress(localIp), localPort);
            java.net.InetSocketAddress remote = new java.net.InetSocketAddress(InetAddress.getByAddress(remoteIp), remotePort);
            int uid = cm.getConnectionOwnerUid(protocol, local, remote);
            if (uid < 0) {
                return false; // no owner found - fail open
            }
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages == null) {
                return false;
            }
            Set<String> blocked = BlockEnforcer.computeActiveBlockedPackages(this);
            for (String pkg : packages) {
                if (blocked.contains(pkg)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Best effort - fail open (see javadoc above).
        }
        return false;
    }

    private void writeToTun(byte[] packet) {
        try {
            synchronized (tunOut) {
                tunOut.write(packet);
            }
            log("  -> WROTE " + packet.length + " bytes back to tun - OK");
        } catch (Exception e) {
            log("  -> WRITE TO TUN FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Periodically closes TCP/UDP sessions that have gone quiet for too long - a safety net against leaks, not the normal way connections end (TCP normally ends via FIN/RST; see TcpSession). */
    private void reapIdleSessions() {
        while (running) {
            try {
                Thread.sleep(REAP_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
            long now = System.currentTimeMillis();
            synchronized (tcpSessionsLock) {
                Iterator<TcpSession> it = tcpSessions.values().iterator();
                while (it.hasNext()) {
                    TcpSession s = it.next();
                    boolean idle = now - s.getLastActivityMillis() > TCP_IDLE_TIMEOUT_MS;
                    boolean nowBlocked = !idle && isOwningAppBlocked(ChecksumUtil.PROTOCOL_TCP, s.getClientIp(), s.getClientPort(), s.getRemoteIp(), s.getRemotePort());
                    if (nowBlocked) {
                        log("[TCP] reaper closing - app=" + describeOwningApp(ChecksumUtil.PROTOCOL_TCP, s.getClientIp(), s.getClientPort(), s.getRemoteIp(), s.getRemotePort()) + " is now in an active Block");
                    }
                    if (idle || nowBlocked) {
                        it.remove();
                        s.close();
                    }
                }
            }
            synchronized (udpSessionsLock) {
                Iterator<UdpRelaySession> it = udpSessions.values().iterator();
                while (it.hasNext()) {
                    UdpRelaySession s = it.next();
                    boolean idle = now - s.getLastActivityMillis() > UDP_IDLE_TIMEOUT_MS;
                    boolean nowBlocked = !idle && isOwningAppBlocked(ChecksumUtil.PROTOCOL_UDP, s.getClientIp(), s.getClientPort(), s.getRemoteIp(), s.getRemotePort());
                    if (nowBlocked) {
                        log("[UDP] reaper closing - app=" + describeOwningApp(ChecksumUtil.PROTOCOL_UDP, s.getClientIp(), s.getClientPort(), s.getRemoteIp(), s.getRemotePort()) + " is now in an active Block");
                    }
                    if (idle || nowBlocked) {
                        it.remove();
                        s.close();
                    }
                }
            }
        }
    }

    /**
     * The actual query-handling pipeline, with no dependency on the tun
     * interface at all: parse -> check against the blocked-websites list
     * -> either build a local NXDOMAIN or forward to CleanBrowsing -> build
     * the reply packet. Returns the finished reply packet bytes, or null if
     * the packet should simply be dropped (already logged either way).
     * Split out so runSelfTest() below can exercise this
     * exact real logic without needing a packet to have come off (or a
     * response to go back onto) the tun fd.
     */
    private byte[] processPacket(byte[] packet, int length) throws Exception {
        IpV4UdpPacket parsed = IpV4UdpPacket.parse(packet, length);
        if (parsed == null) {
            log("DROP: packet did not parse as IPv4/UDP (len=" + length + ") first bytes=" + hexDump(packet, length));
            return null; // not something we know how to handle - drop safely
        }

        String domain = DnsMessage.parseQuestionName(parsed.payload, parsed.payloadLength);
        String owningApp = describeOwningApp(ChecksumUtil.PROTOCOL_UDP, parsed.sourceIp, parsed.sourcePort, parsed.destIp, parsed.destPort);
        log("QUERY domain=" + domain + " srcPort=" + parsed.sourcePort + " app=" + owningApp);

        byte[] responsePayload;
        if (domain != null && isDomainBlocked(domain)) {
            log("  -> blocked, building NXDOMAIN locally");
            responsePayload = DnsMessage.buildNxDomainResponse(parsed.payload, parsed.payloadLength);
        } else {
            long start = System.currentTimeMillis();
            responsePayload = forwardToRealDns(parsed.payload, parsed.payloadLength);
            long elapsed = System.currentTimeMillis() - start;
            log("  -> forwardToRealDns returned " + (responsePayload == null ? "NULL (failed)" : responsePayload.length + " bytes") + " after " + elapsed + "ms");
        }

        if (responsePayload == null) {
            log("  -> DROP: no response payload to send back");
            return null; // couldn't build/get a response - drop rather than send something wrong
        }

        try {
            return IpV4UdpPacket.buildResponsePacket(parsed, responsePayload);
        } catch (Exception e) {
            log("  -> DROP: buildResponsePacket threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Result of runSelfTest() below - whether the in-process pipeline produced a real response, how long it took, and a human-readable detail string. */
    public static class SelfTestResult {
        public final boolean success;
        public final long elapsedMs;
        public final String detail;

        public SelfTestResult(boolean success, long elapsedMs, String detail) {
            this.success = success;
            this.elapsedMs = elapsedMs;
            this.detail = detail;
        }
    }

    /**
     * Runs one query through the exact real parse -> block-check ->
     * NXDOMAIN-or-forward -> build-response pipeline that real tun traffic
     * goes through, entirely in-process - no packet is read from or written
     * to the tun interface, and Android's per-app VPN routing (which
     * excludes this app's own traffic from its own tunnel by default, and
     * which must NOT be worked around via addAllowedApplication - see the
     * comment in startVpn()) never enters the picture at all. This is what
     * DiagnosticActivity uses in place of a real socket probe to our own
     * VPN address, which can never succeed for reasons that have nothing to
     * do with whether the pipeline itself works.
     */
    public SelfTestResult runSelfTest(String domain) {
        long start = System.currentTimeMillis();
        try {
            byte[] queryPayload = DnsMessage.buildQuery(domain);
            byte[] fakeAppIp = { 10, 0, 0, 9 }; // stand-in for "some other app's" source address - never actually used for routing
            byte[] vpnIp = { 10, 0, 0, 2 };
            byte[] queryPacket = IpV4UdpPacket.buildPacket(fakeAppIp, 55123, vpnIp, 53, queryPayload);

            log("[SELF-TEST] simulated query domain=" + domain);
            byte[] responsePacket = processPacket(queryPacket, queryPacket.length);
            long elapsed = System.currentTimeMillis() - start;

            if (responsePacket == null) {
                log("[SELF-TEST] FAILED - pipeline produced no response after " + elapsed + "ms");
                return new SelfTestResult(false, elapsed, "no response produced - see live log above for where it stopped");
            }
            log("[SELF-TEST] OK - got " + responsePacket.length + " byte response after " + elapsed + "ms");
            return new SelfTestResult(true, elapsed, responsePacket.length + " byte response");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log("[SELF-TEST] EXCEPTION: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return new SelfTestResult(false, elapsed, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean isDomainBlocked(String domain) {
        if (new VpnSafetyStorage(this).isEngaged()) {
            return false; // safety engaged - pass everything through unfiltered
        }
        String normalized = BlockedWebsitesStorage.normalize(domain);
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }

        Set<String> blockedDomains = new BlockedWebsitesStorage(this).loadDomains();
        for (String blocked : blockedDomains) {
            if (normalized.equals(blocked) || normalized.endsWith("." + blocked)) {
                return true; // exact match, or a subdomain of a blocked domain
            }
        }

        return false;
    }

    /** Forwards the raw DNS query to the real upstream resolver and returns its response payload, or null on total failure. Tries UPSTREAM_DNS first; only on timeout/failure does it retry once against UPSTREAM_DNS_FALLBACK - see that constant's comment for why. */
    private byte[] forwardToRealDns(byte[] query, int length) {
        byte[] result = forwardToRealDns(query, length, UPSTREAM_DNS);
        if (result == null) {
            log("    primary upstream (" + UPSTREAM_DNS + ") failed - trying fallback (" + UPSTREAM_DNS_FALLBACK + ")");
            result = forwardToRealDns(query, length, UPSTREAM_DNS_FALLBACK);
        }
        return result;
    }

    /** One attempt against one specific upstream resolver. 3s timeout (not the old 5s) so a primary-then-fallback worst case stays around 6s instead of 10s. */
    private byte[] forwardToRealDns(byte[] query, int length, String upstreamIp) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            boolean protected_ = protect(socket);
            if (!protected_) {
                log("    protect(socket) returned FALSE");
            }
            socket.setSoTimeout(3000);

            DatagramPacket outPacket = new DatagramPacket(query, length, InetAddress.getByName(upstreamIp), 53);
            socket.send(outPacket);

            byte[] responseBuffer = new byte[512];
            DatagramPacket inPacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(inPacket);

            byte[] result = new byte[inPacket.getLength()];
            System.arraycopy(responseBuffer, 0, result, 0, inPacket.getLength());
            return result;
        } catch (Exception e) {
            log("    forwardToRealDns(" + upstreamIp + ") EXCEPTION: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null; // upstream timeout/failure - caller decides whether to fall back or give up
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (runningInstance == this) {
            runningInstance = null;
        }
        if (reaperThread != null) {
            reaperThread.interrupt();
        }
        if (udpDispatchExecutor != null) {
            udpDispatchExecutor.shutdownNow();
        }
        if (dnsForwardExecutor != null) {
            dnsForwardExecutor.shutdownNow();
        }
        synchronized (tcpSessionsLock) {
            for (TcpSession s : new ArrayList<TcpSession>(tcpSessions.values())) {
                s.close();
            }
            tcpSessions.clear();
        }
        synchronized (udpSessionsLock) {
            for (UdpRelaySession s : new ArrayList<UdpRelaySession>(udpSessions.values())) {
                s.close();
            }
            udpSessions.clear();
        }
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (Exception e) {
            // Already closed or similar - fine.
        }
        vpnInterface = null;
    }
}
