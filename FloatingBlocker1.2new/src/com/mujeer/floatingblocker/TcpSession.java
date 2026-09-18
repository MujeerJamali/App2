package com.mujeer.floatingblocker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * One TCP connection as seen by some app on this device, relayed to a real
 * socket to the real destination. The app's OS-level TCP stack thinks it's
 * talking directly to the real destination; underneath, everything it
 * sends is written to a real Socket's OutputStream, and everything the
 * real Socket's InputStream produces is turned back into TCP segments and
 * written into the tun interface, addressed as if they came straight from
 * that destination.
 *
 * IMPORTANT SIMPLIFICATIONS (see DnsVpnService's class comment for the
 * full picture):
 * - No TCP options are parsed or honored except sending our own MSS on the
 *   SYN-ACK. No window scaling - the window field is used literally.
 * - No retransmission of data WE send to the app if its ACK is somehow
 *   lost. This is deliberate, not an oversight: the tun interface is an
 *   in-kernel pipe, not a real lossy link, so packet loss between this
 *   service and the app is not a realistic failure mode the way it would
 *   be for a real network hop. The real, genuinely lossy hop (this
 *   device's real socket <-> the actual remote server) is handled
 *   entirely by the OS's own real TCP stack on that socket - we never
 *   touch it.
 * - All incoming packets for one connection are processed strictly in the
 *   order they arrived, on this session's own dedicated thread (see
 *   enqueue()/run() below) - never on whatever ad-hoc thread happened to
 *   read them off tun - specifically so two packets belonging to the same
 *   connection can never be handled out of order due to thread scheduling.
 * - Closing is handled pragmatically (track "their FIN seen" and "real
 *   socket hit EOF" independently, send our own FIN once the real socket
 *   is done, and tear down once both directions are finished) rather than
 *   implementing every RFC 9293 sub-state (TIME_WAIT etc.) - correct for
 *   the normal request/response connections real browsing consists of,
 *   not exhaustively tested against adversarial or simultaneous-close
 *   cases.
 */
public class TcpSession implements Runnable {

    private static final int STATE_NEW = 0;
    private static final int STATE_SYN_RECEIVED = 1;
    private static final int STATE_ESTABLISHED = 2;
    private static final int STATE_CLOSED = 3;

    private static final int MSS = 1400; // payload bytes per segment - keeps total packet size comfortably under the 1500 MTU set on the Builder
    private static final int ADVERTISED_WINDOW = 65535; // literal, unscaled - see class comment
    private static final int CONNECT_TIMEOUT_MS = 8000;

    private final DnsVpnService service;
    private final String key;
    private final byte[] clientIp;
    private final int clientPort;
    private final byte[] remoteIp;
    private final int remotePort;
    private final FileOutputStream tunOut;
    private final LinkedBlockingQueue<byte[]> inbox = new LinkedBlockingQueue<byte[]>();

    private volatile int state = STATE_NEW;
    private volatile long serverSeq;             // next sequence number WE will use
    private volatile long clientNextExpectedSeq;  // next sequence number WE expect FROM the app (also what we ACK with)
    private volatile boolean clientFinReceived = false;
    private volatile boolean remoteEofReached = false;
    private volatile boolean ourFinSent = false;
    private volatile boolean closed = false;
    private volatile long lastActivityMillis = System.currentTimeMillis();

    private Socket socket;
    private Thread processorThread;
    private Thread pumpThread;
    private long synIsn; // our chosen initial sequence number - fixed for the life of the connection, used verbatim on every SYN-ACK retransmit

    public TcpSession(DnsVpnService service, String key, byte[] clientIp, int clientPort, byte[] remoteIp, int remotePort, FileOutputStream tunOut) {
        this.service = service;
        this.key = key;
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.tunOut = tunOut;
    }

    public long getLastActivityMillis() {
        return lastActivityMillis;
    }

    public byte[] getClientIp() { return clientIp; }
    public int getClientPort() { return clientPort; }
    public byte[] getRemoteIp() { return remoteIp; }
    public int getRemotePort() { return remotePort; }

    public boolean isClosed() {
        return closed;
    }

    /** Starts this session's dedicated ordered-processing thread. Call once, right after registering it in DnsVpnService's session map. */
    public void start() {
        processorThread = new Thread(this, "tcp-" + key);
        processorThread.start();
    }

    /** Queues a raw packet (already confirmed IPv4/TCP for this exact flow) for in-order processing. Non-blocking, safe from any thread. */
    public void enqueue(byte[] rawPacket, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(rawPacket, 0, copy, 0, length);
        inbox.add(copy);
    }

    @Override
    public void run() {
        while (!closed) {
            byte[] raw;
            try {
                raw = inbox.take();
            } catch (InterruptedException e) {
                break;
            }
            try {
                TcpPacket tcp = TcpPacket.parse(raw, raw.length);
                if (tcp != null) {
                    handleIncoming(tcp);
                }
            } catch (Exception e) {
                DnsVpnService.log("[TCP " + key + "] UNCAUGHT in handleIncoming: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private void handleIncoming(TcpPacket tcp) {
        lastActivityMillis = System.currentTimeMillis();

        if (tcp.hasFlag(TcpPacket.FLAG_RST)) {
            DnsVpnService.log("[TCP " + key + "] client sent RST - tearing down");
            close();
            return;
        }

        if (state == STATE_NEW) {
            handleInitialSyn(tcp);
            return;
        }

        if (state == STATE_SYN_RECEIVED) {
            if (tcp.hasFlag(TcpPacket.FLAG_SYN) && !tcp.hasFlag(TcpPacket.FLAG_ACK)) {
                sendSynAck(); // retransmitted SYN (our SYN-ACK likely got lost or was slow) - resend it
                return;
            }
            if (!tcp.hasFlag(TcpPacket.FLAG_ACK)) {
                return; // not what we're waiting for - ignore
            }
            state = STATE_ESTABLISHED;
            DnsVpnService.log("[TCP " + key + "] ESTABLISHED");
            startPumpThread();
            // fall through - this same packet may also carry the first data (very common: the final
            // handshake ACK often rides in on the same segment as the first request)
        }

        if (state == STATE_ESTABLISHED) {
            handleEstablished(tcp);
        }
    }

    private void handleInitialSyn(TcpPacket tcp) {
        if (!tcp.hasFlag(TcpPacket.FLAG_SYN) || tcp.hasFlag(TcpPacket.FLAG_ACK)) {
            return; // dispatch only ever creates a session for a bare SYN - anything else here is unexpected, ignore
        }
        clientNextExpectedSeq = (tcp.seq + 1) & 0xFFFFFFFFL; // SYN consumes one sequence number

        try {
            socket = new Socket();
            // protect(Socket) needs a real underlying file descriptor to mark as
            // bypassing the VPN - a bare `new Socket()` doesn't allocate one until
            // it's actually bound or connected. Calling protect() before that finds
            // nothing to protect and silently no-ops (returns false), so the socket
            // then connects UNPROTECTED - its own outbound traffic gets captured by
            // our own 0.0.0.0/0 route and looped straight back into our own tun
            // interface instead of reaching the real network, and just hangs until
            // the connect timeout. Explicitly binding first forces the file
            // descriptor to exist before we ever call protect().
            socket.bind(new InetSocketAddress(0));
            boolean protectedOk = service.protect(socket);
            if (!protectedOk) {
                DnsVpnService.log("[TCP " + key + "] protect(socket) returned FALSE");
            }
            socket.connect(new InetSocketAddress(java.net.InetAddress.getByAddress(remoteIp), remotePort), CONNECT_TIMEOUT_MS);
        } catch (Exception e) {
            DnsVpnService.log("[TCP " + key + "] connect FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            sendRst((tcp.seq + 1) & 0xFFFFFFFFL);
            close();
            return;
        }

        synIsn = new java.util.Random().nextInt() & 0xFFFFFFFFL; // our own ISN - any value is fine, nothing security-sensitive relies on it here
        serverSeq = synIsn;
        state = STATE_SYN_RECEIVED;
        sendSynAck();
        serverSeq = (serverSeq + 1) & 0xFFFFFFFFL; // SYN consumes one sequence number
    }

    private void sendSynAck() {
        byte[] pkt = TcpPacket.build(remoteIp, remotePort, clientIp, clientPort, synIsn, clientNextExpectedSeq,
                TcpPacket.FLAG_SYN | TcpPacket.FLAG_ACK, ADVERTISED_WINDOW, null, TcpPacket.mssOption(MSS));
        writeToTun(pkt);
    }

    private void handleEstablished(TcpPacket tcp) {
        if (tcp.payloadLength > 0) {
            if (tcp.seq != clientNextExpectedSeq) {
                // Out of order, or a retransmission of data we've already consumed - don't
                // write it again (would corrupt the real stream), just re-ACK what we
                // actually expect next so the app's own stack knows to resend the right thing.
                sendAck();
                return;
            }
            try {
                socket.getOutputStream().write(tcp.payload, 0, tcp.payloadLength);
            } catch (IOException e) {
                DnsVpnService.log("[TCP " + key + "] write to real socket FAILED: " + e.getMessage());
                close();
                return;
            }
            clientNextExpectedSeq = (clientNextExpectedSeq + tcp.payloadLength) & 0xFFFFFFFFL;
        }

        if (tcp.hasFlag(TcpPacket.FLAG_FIN)) {
            clientNextExpectedSeq = (clientNextExpectedSeq + 1) & 0xFFFFFFFFL; // FIN consumes a sequence number
            clientFinReceived = true;
            try {
                socket.shutdownOutput(); // tell the real destination the app has no more data to send
            } catch (Exception ignored) { /* socket may already be closing/closed - fine */ }
            sendAck();
            maybeFinishClosing();
            return;
        }

        if (tcp.payloadLength > 0) {
            sendAck();
        }
        // A bare ACK with no payload and no FIN needs no response from us - we don't track
        // our own unacked-data-awaiting-retransmission state (see class comment), so there's
        // nothing to do with it beyond the lastActivityMillis bump already recorded above.
    }

    /** Reads from the real socket and turns everything it produces back into TCP segments toward the app. Started once, the moment the handshake completes. */
    private void startPumpThread() {
        pumpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[MSS];
                try {
                    java.io.InputStream socketIn = socket.getInputStream();
                    int n;
                    while ((n = socketIn.read(buf)) > 0) {
                        byte[] chunk = new byte[n];
                        System.arraycopy(buf, 0, chunk, 0, n);
                        sendDataToClient(chunk);
                    }
                } catch (Exception e) {
                    DnsVpnService.log("[TCP " + key + "] real socket read ended: " + e.getClass().getSimpleName());
                }
                remoteEofReached = true;
                maybeFinishClosing();
            }
        }, "tcp-pump-" + key);
        pumpThread.start();
    }

    private synchronized void sendDataToClient(byte[] chunk) {
        if (closed) {
            return;
        }
        byte[] pkt = TcpPacket.build(remoteIp, remotePort, clientIp, clientPort, serverSeq, clientNextExpectedSeq,
                TcpPacket.FLAG_ACK | TcpPacket.FLAG_PSH, ADVERTISED_WINDOW, chunk);
        writeToTun(pkt);
        serverSeq = (serverSeq + chunk.length) & 0xFFFFFFFFL;
        lastActivityMillis = System.currentTimeMillis();
    }

    private void sendAck() {
        byte[] pkt = TcpPacket.build(remoteIp, remotePort, clientIp, clientPort, serverSeq, clientNextExpectedSeq,
                TcpPacket.FLAG_ACK, ADVERTISED_WINDOW, null);
        writeToTun(pkt);
    }

    private void sendRst(long ackValue) {
        byte[] pkt = TcpPacket.build(remoteIp, remotePort, clientIp, clientPort, 0, ackValue,
                TcpPacket.FLAG_RST | TcpPacket.FLAG_ACK, 0, null);
        writeToTun(pkt);
    }

    private synchronized void maybeFinishClosing() {
        if (closed) {
            return;
        }
        if (remoteEofReached && !ourFinSent) {
            byte[] pkt = TcpPacket.build(remoteIp, remotePort, clientIp, clientPort, serverSeq, clientNextExpectedSeq,
                    TcpPacket.FLAG_FIN | TcpPacket.FLAG_ACK, ADVERTISED_WINDOW, null);
            writeToTun(pkt);
            serverSeq = (serverSeq + 1) & 0xFFFFFFFFL;
            ourFinSent = true;
        }
        if (clientFinReceived && remoteEofReached) {
            // Both directions have finished. We don't wait out a full TIME_WAIT - if a
            // stray final ACK for this connection arrives after this point, it'll simply
            // find no session in the map and be dropped, which is harmless.
            close();
        }
    }

    /** Tears down this session: closes the real socket, stops both threads, and removes it from DnsVpnService's session map. Safe to call more than once. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        state = STATE_CLOSED;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) { }
        if (processorThread != null) {
            processorThread.interrupt();
        }
        service.removeTcpSession(key);
    }

    private void writeToTun(byte[] packet) {
        try {
            synchronized (tunOut) {
                tunOut.write(packet);
            }
        } catch (IOException e) {
            DnsVpnService.log("[TCP " + key + "] write to tun FAILED: " + e.getMessage());
            close();
        }
    }
}
