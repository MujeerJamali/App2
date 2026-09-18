package com.mujeer.floatingblocker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained network/DNS diagnostic tool. Does NOT rely on logcat
 * (not practically accessible on a non-rooted device) - instead it runs
 * a battery of real, isolated network probes itself and prints a full,
 * copyable report of exactly what happened at each step.
 *
 * IMPORTANT: none of the *direct* probes below (CleanBrowsing/Google/
 * Cloudflare/network's-own-DNS/TCP tests) go through DnsVpnService.protect().
 * They don't need to - the VPN's Builder only routes the single virtual
 * address 10.0.0.2/32 into the tunnel (see DnsVpnService's class comment);
 * a raw socket to any other explicit IP (which is what every one of those
 * probes does - no hostname lookups) already bypasses the tunnel entirely
 * and travels over the real network, same as if the VPN were not running
 * at all. That means those results reflect the real, raw state of the
 * network Self-Control's own DNS forwarding depends on.
 *
 * The "Through Self-Control's own VPN" section is different on purpose: it
 * does NOT open a real socket to 10.0.0.2. A real socket from this app to
 * our own VPN address can never reliably prove anything either way, because
 * Android excludes the VPN-owning app's own traffic from its own tunnel by
 * default - that probe would time out even with a perfectly healthy
 * pipeline, and deliberately routing it through the tunnel anyway (via
 * addAllowedApplication) would silently cut every OTHER app off from the
 * network under this VPN's always-on assignment. Instead this section asks
 * DnsVpnService to run a synthetic query through its real, running pipeline
 * in-process (see DnsVpnService.runSelfTest()) - same parse/block-check/
 * forward/build-response code real tun traffic uses, just without needing
 * OS-level VPN routing to deliver the probe.
 *
 * What it checks:
 * 1. Which network is active (WiFi/cellular) and the DNS server(s) that
 *    network itself hands out via DHCP (the router's own resolver).
 * 2. Raw UDP DNS queries (the exact wire format DnsVpnService forwards)
 *    sent directly to: CleanBrowsing (our upstream), Google, Cloudflare,
 *    and the network's own DNS server - each with its own timing and
 *    exact exception, so it's possible to tell "everything is blocked"
 *    apart from "only CleanBrowsing specifically is blocked" apart from
 *    "the whole network is down".
 * 3. An in-process self-test of DnsVpnService's real query pipeline.
 * 4. Raw TCP connectivity to a couple of well-known ports, to tell apart
 *    "no internet at all" from "UDP port 53 specifically is filtered".
 */
public class DiagnosticActivity extends Activity {

    private static final String VPN_VIRTUAL_DNS = "10.0.0.2";

    // How often the live tail (counters + log) refreshes once live monitoring is
    // started. Cheap - pure in-memory reads, no network I/O - so this is safe to
    // run indefinitely, unlike the one-off probes in buildStaticSection() which
    // hit real DNS servers and shouldn't be repeated every second.
    private static final long LIVE_REFRESH_INTERVAL_MS = 1000;

    private TextView txtReport;
    private ScrollView scrollReport;
    private Button btnRun;
    private Button btnCopy;
    private Button btnLive;

    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private boolean liveActive = false;
    private String cachedStaticSection = "";

    private final Runnable liveTick = new Runnable() {
        @Override
        public void run() {
            if (!liveActive) {
                return;
            }
            txtReport.setText(cachedStaticSection + buildLiveTailSection());
            scrollReport.post(new Runnable() {
                @Override
                public void run() {
                    scrollReport.fullScroll(android.view.View.FOCUS_DOWN);
                }
            });
            liveHandler.postDelayed(this, LIVE_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostic);

        txtReport = (TextView) findViewById(R.id.txtDiagnosticReport);
        scrollReport = (ScrollView) findViewById(R.id.scrollDiagnosticReport);
        btnRun = (Button) findViewById(R.id.btnRunDiagnostic);
        btnCopy = (Button) findViewById(R.id.btnCopyDiagnostic);
        btnLive = (Button) findViewById(R.id.btnLiveDiagnostic);

        btnRun.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                stopLiveMonitoring();
                runDiagnostics();
            }
        });

        btnCopy.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Self-Control diagnostic report", txtReport.getText()));
                    Toast.makeText(DiagnosticActivity.this, R.string.msg_diagnostic_copied, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnLive.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (liveActive) {
                    stopLiveMonitoring();
                } else {
                    startLiveMonitoring();
                }
            }
        });

        runDiagnostics();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop the repeating refresh when leaving the screen - the Handler holds
        // a reference to this Activity's Views via liveTick, so leaving it
        // running would leak the Activity and keep waking the app up in the
        // background for no reason. Live monitoring is meant to run "while I'm
        // watching", not silently forever.
        stopLiveMonitoring();
    }

    private void runDiagnostics() {
        btnRun.setEnabled(false);
        btnCopy.setEnabled(false);
        btnLive.setEnabled(false);
        txtReport.setText(R.string.msg_diagnostic_running);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String staticSection = buildStaticSection();
                final String fullReport = staticSection + buildLiveTailSection();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cachedStaticSection = staticSection;
                        txtReport.setText(fullReport);
                        btnRun.setEnabled(true);
                        btnCopy.setEnabled(true);
                        btnLive.setEnabled(true);
                    }
                });
            }
        }).start();
    }

    /** Starts (or restarts) live monitoring: runs the one-off probes once, then refreshes just the traffic counters + log every LIVE_REFRESH_INTERVAL_MS until stopLiveMonitoring() is called (Stop button, leaving the screen, or pressing Run again). */
    private void startLiveMonitoring() {
        btnRun.setEnabled(false);
        btnCopy.setEnabled(false);
        btnLive.setEnabled(false);
        txtReport.setText(R.string.msg_diagnostic_running);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String staticSection = buildStaticSection();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cachedStaticSection = staticSection;
                        liveActive = true;
                        btnRun.setEnabled(true);
                        btnCopy.setEnabled(true);
                        btnLive.setEnabled(true);
                        btnLive.setText(R.string.diagnostic_live_stop_button);
                        liveHandler.post(liveTick); // first tick runs immediately, then reschedules itself
                    }
                });
            }
        }).start();
    }

    /** Stops live monitoring if running. Safe to call even when it isn't (Run button, onPause, etc. all call this unconditionally). */
    private void stopLiveMonitoring() {
        if (!liveActive) {
            return;
        }
        liveActive = false;
        liveHandler.removeCallbacks(liveTick);
        btnLive.setText(R.string.diagnostic_live_start_button);
    }

    /** Everything that involves a real one-off network probe (direct DNS/TCP tests, the in-process self-test) - computed ONCE per Run/Start Live press, never repeated automatically, so live monitoring doesn't hammer external DNS servers every second. */
    private String buildStaticSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("Self-Control network diagnostic\n");
        sb.append("Time: ").append(new java.util.Date().toString()).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n\n");

        sb.append("--- Active network ---\n");
        List<InetAddress> networkDnsServers = new ArrayList<InetAddress>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm.getActiveNetwork();
            if (active == null) {
                sb.append("No active network reported by the OS.\n");
            } else {
                NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                sb.append("Transport: ").append(describeTransports(caps)).append("\n");
                if (caps != null) {
                    boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    boolean captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
                    sb.append("Android's own validation of this network: INTERNET capability=").append(hasInternet)
                            .append(", VALIDATED=").append(validated)
                            .append(captivePortal ? ", CAPTIVE_PORTAL=true" : "").append("\n");
                    if (!validated) {
                        sb.append("  -> Android has NOT marked this network as validated (its own connectivity probe\n")
                          .append("     hasn't succeeded, or hasn't run yet). Chrome and some other apps deliberately\n")
                          .append("     refuse to load pages over an unvalidated network, even though DNS and other\n")
                          .append("     apps may still work fine over it - this can look exactly like 'DNS works but\n")
                          .append("     Chrome does nothing' without being a bug in this app's own packet handling.\n");
                    }
                } else {
                    sb.append("Android's own validation of this network: could not read NetworkCapabilities (null)\n");
                }
                LinkProperties props = cm.getLinkProperties(active);
                if (props != null) {
                    sb.append("Interface: ").append(props.getInterfaceName()).append("\n");
                    List<InetAddress> dnsServers = props.getDnsServers();
                    if (dnsServers != null && !dnsServers.isEmpty()) {
                        sb.append("DNS servers reported for this connection:\n");
                        for (InetAddress d : dnsServers) {
                            sb.append("  - ").append(d.getHostAddress()).append("\n");
                            networkDnsServers.add(d);
                        }
                    } else {
                        sb.append("DNS servers reported for this connection: (none)\n");
                    }
                }
                // Also walk every network to find the underlying WiFi one specifically,
                // in case the "active" network above is our own VPN network instead.
                Network[] all = cm.getAllNetworks();
                for (Network n : all) {
                    NetworkCapabilities c = cm.getNetworkCapabilities(n);
                    if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        LinkProperties wifiProps = cm.getLinkProperties(n);
                        if (wifiProps != null) {
                            List<InetAddress> wifiDns = wifiProps.getDnsServers();
                            if (wifiDns != null) {
                                for (InetAddress d : wifiDns) {
                                    if (!containsAddress(networkDnsServers, d)) {
                                        networkDnsServers.add(d);
                                        sb.append("Underlying WiFi DNS server: ").append(d.getHostAddress()).append("\n");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("Could not read network info: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
        }

        sb.append("\n--- UDP DNS query tests (port 53) ---\n");
        sb.append("Each sends a real 'google.com' A-record query and waits up to 4s for a reply.\n\n");
        sb.append(udpDnsTest("CleanBrowsing (our upstream, direct)", "185.228.168.168"));
        sb.append(udpDnsTest("Google (direct)", "8.8.8.8"));
        sb.append(udpDnsTest("Cloudflare (direct)", "1.1.1.1"));
        for (InetAddress d : networkDnsServers) {
            String addr = d.getHostAddress();
            if (addr.equals(VPN_VIRTUAL_DNS)) {
                continue; // handled separately below, with the live pipeline log
            }
            if (!addr.equals("185.228.168.168") && !addr.equals("8.8.8.8") && !addr.equals("1.1.1.1")) {
                sb.append(udpDnsTest("This network's own DNS (" + addr + ")", addr));
            }
        }

        sb.append("\n--- Through Self-Control's own VPN (in-process pipeline test) ---\n");
        sb.append("Runs a synthetic query through DnsVpnService's real parse/block-check/forward/reply code directly, without going over a socket or touching the tun interface - see this file's class comment for why.\n\n");
        sb.append(selfTest("google.com"));
        sb.append("(Live traffic counters and the log itself are at the bottom of this report, and refresh every ~1s while live monitoring is running.)\n");

        sb.append("\n--- Raw TCP connectivity tests ---\n");
        sb.append("Checks whether the internet works at all, separate from DNS/port 53.\n\n");
        sb.append(tcpConnectTest("Cloudflare HTTPS", "1.1.1.1", 443));
        sb.append(tcpConnectTest("CleanBrowsing DNS-over-TCP", "185.228.168.168", 53));
        sb.append(tcpConnectTest("Google DNS-over-TCP", "8.8.8.8", 53));

        sb.append("\n--- How to read this ---\n");
        sb.append("- If 'Android's own validation of this network' above shows VALIDATED=false: this is very likely THE cause of 'DNS/some apps work, Chrome (or other apps) show nothing' - Chrome specifically is known to refuse to load pages over a network Android hasn't validated, even while DNS lookups and simpler apps go ahead anyway. Not a bug in this app's own packet handling if so.\n");
        sb.append("- If ALL direct/TCP tests above failed (UDP and TCP, every destination): the network itself has no working internet right now - not something this app can fix.\n");
        sb.append("- If direct tests succeeded but the self-test failed with 'DnsVpnService is not currently running': the service itself isn't up - that's the actual problem, not any single query.\n");
        sb.append("- If direct tests succeeded but the self-test failed some other way: the bug is inside DnsVpnService's pipeline itself, not the network or Android's VPN routing - check the live log below for the exact line where it stopped (parse failure, forward exception, or buildResponsePacket failure).\n");
        sb.append("- If the self-test SUCCEEDS: the real query pipeline (parse -> block-check -> forward/NXDOMAIN -> build response) works. If real browsing still seems unfiltered or broken, the issue is specific to how traffic from other apps gets routed into the tunnel, not this pipeline.\n");
        sb.append("- Real TCP/UDP packet counts above at 0 after you've actually browsed something means other apps' traffic still isn't reaching the tun interface at all - a routing problem, not anything inside DnsVpnService's own code.\n");
        sb.append("- If the live log shows 'protect(socket) returned FALSE' or a 'forwardToRealDns(...) EXCEPTION': the forwarding socket itself is failing inside the service.\n");
        sb.append("- If the network's own DNS server succeeded but CleanBrowsing/Google/Cloudflare (direct) all failed: this network only allows its own resolver and blocks external DNS servers specifically.\n");
        sb.append("- If the direct CleanBrowsing test above failed but Google/Cloudflare (direct) succeeded: CleanBrowsing specifically (our primary upstream) is down or rate-limited on this network right now - not a bug in this app. The VPN automatically retries via a fallback resolver (Cloudflare Family, 1.1.1.2) when this happens, so browsing should recover on its own; a 'trying fallback' line in the live log confirms it kicked in.\n");

        return sb.toString();
    }

    /** Just the traffic counters + live log - pure in-memory reads, no network I/O, safe to call every second indefinitely. This is what live monitoring refreshes repeatedly; buildStaticSection() (the real probes) never re-runs on its own. */
    private String buildLiveTailSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- Live tun traffic + log (updated ")
                .append(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()))
                .append(liveActive ? " - refreshing every ~1s, tap Stop Live to freeze" : "")
                .append(") ---\n");
        sb.append("Tun traffic seen from OTHER apps since the service last started (proof the tunnel is actively capturing real traffic):\n");
        sb.append("  Total packets read from tun: ").append(DnsVpnService.getTotalPacketsRead()).append("\n");
        sb.append("  Of those, routine IPv6 noise (filtered, not logged individually): ").append(DnsVpnService.getIpv6NoiseCount()).append("\n");
        sb.append("  Real TCP packets (web browsing, apps): ").append(DnsVpnService.getTcpPacketsCount()).append("\n");
        sb.append("  Real UDP packets (DNS, QUIC/HTTP3, etc.): ").append(DnsVpnService.getUdpPacketsCount()).append("\n");
        sb.append("  Other protocol (ICMP/ping etc, not handled): ").append(DnsVpnService.getOtherProtocolCount()).append("\n");
        sb.append("\nLive log from inside DnsVpnService (most recent activity, IPv6 noise excluded):\n");
        List<String> serviceLog = DnsVpnService.getRecentLog();
        if (serviceLog.isEmpty()) {
            sb.append("  (empty - service may not have processed anything yet, or isn't running)\n");
        } else {
            for (String line : serviceLog) {
                sb.append("  ").append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private boolean containsAddress(List<InetAddress> list, InetAddress addr) {
        for (InetAddress a : list) {
            if (a.getHostAddress().equals(addr.getHostAddress())) {
                return true;
            }
        }
        return false;
    }

    private String describeTransports(NetworkCapabilities caps) {
        if (caps == null) {
            return "(unknown)";
        }
        List<String> transports = new ArrayList<String>();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("WiFi");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("Cellular");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("VPN");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("Ethernet");
        if (transports.isEmpty()) {
            return "(none matched)";
        }
        return joinStrings(transports, " + ");
    }

    private String joinStrings(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    /** Sends one real DNS query for "google.com" straight to the given IP over UDP and reports what happened. */
    private String udpDnsTest(String label, String ip) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" (").append(ip).append("): ");
        DatagramSocket socket = null;
        long start = System.currentTimeMillis();
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(4000);
            byte[] query = DnsMessage.buildQuery("google.com");
            DatagramPacket outPacket = new DatagramPacket(query, query.length, InetAddress.getByName(ip), 53);
            socket.send(outPacket);

            byte[] responseBuffer = new byte[512];
            DatagramPacket inPacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(inPacket);

            long elapsed = System.currentTimeMillis() - start;
            sb.append("SUCCESS - got ").append(inPacket.getLength()).append(" bytes back in ").append(elapsed).append("ms\n");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            sb.append("FAILED after ").append(elapsed).append("ms - ")
                    .append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
        } finally {
            if (socket != null) socket.close();
        }
        return sb.toString();
    }

    /** Runs DnsVpnService's in-process self-test (see class comment) and formats the result the same way as the direct udpDnsTest probes above. */
    private String selfTest(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("Self-test via running service: ");
        DnsVpnService service = DnsVpnService.getRunningInstance();
        if (service == null) {
            sb.append("FAILED - DnsVpnService is not currently running (nothing to test)\n");
            return sb.toString();
        }
        DnsVpnService.SelfTestResult result = service.runSelfTest(domain);
        if (result.success) {
            sb.append("SUCCESS - pipeline produced a ").append(result.detail).append(" in ").append(result.elapsedMs).append("ms\n");
        } else {
            sb.append("FAILED after ").append(result.elapsedMs).append("ms - ").append(result.detail).append("\n");
        }
        return sb.toString();
    }

    /** Opens a bare TCP connection to the given host:port (no data exchanged) and reports whether it connected. */
    private String tcpConnectTest(String label, String ip, int port) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" (").append(ip).append(":").append(port).append("): ");
        Socket socket = null;
        long start = System.currentTimeMillis();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 3000);
            long elapsed = System.currentTimeMillis() - start;
            sb.append("SUCCESS - connected in ").append(elapsed).append("ms\n");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            sb.append("FAILED after ").append(elapsed).append("ms - ")
                    .append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception e) { /* ignore */ }
            }
        }
        return sb.toString();
    }
}
