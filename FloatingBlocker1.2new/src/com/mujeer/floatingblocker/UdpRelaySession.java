package com.mujeer.floatingblocker;

import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * One NAT'd UDP "flow" (there's no real connection state in UDP - this is
 * just "traffic between this app's source port and this one remote
 * destination"). Needed for anything that isn't DNS - most importantly
 * QUIC/HTTP3, which modern Chrome uses heavily for ordinary web traffic
 * and which is plain UDP on port 443, not TCP. Unlike TcpSession, there's
 * no ordering or handshake to get right here - each datagram from the app
 * is just forwarded as-is, and anything that comes back is relayed as-is.
 */
public class UdpRelaySession {

    private static final int RECEIVE_BUFFER_SIZE = 65535;

    private final DnsVpnService service;
    private final String key;
    private final byte[] clientIp;
    private final int clientPort;
    private final byte[] remoteIp;
    private final int remotePort;
    private final FileOutputStream tunOut;

    private DatagramSocket socket;
    private Thread readerThread;
    private volatile boolean closed = false;
    private volatile long lastActivityMillis = System.currentTimeMillis();

    public UdpRelaySession(DnsVpnService service, String key, byte[] clientIp, int clientPort, byte[] remoteIp, int remotePort, FileOutputStream tunOut) {
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

    /** Opens the real socket and starts the reader thread. Returns false (and the session should be discarded) if the socket couldn't be created/protected. */
    public boolean start() {
        try {
            socket = new DatagramSocket();
            boolean protectedOk = service.protect(socket);
            if (!protectedOk) {
                DnsVpnService.log("[UDP " + key + "] protect(socket) returned FALSE");
            }
        } catch (Exception e) {
            DnsVpnService.log("[UDP " + key + "] failed to open relay socket: " + e.getMessage());
            return false;
        }
        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                pumpFromRemote();
            }
        }, "udp-" + key);
        readerThread.start();
        return true;
    }

    /** Forwards one datagram from the app out to the real destination. Safe to call from any thread. */
    public void sendToRemote(byte[] payload, int length) {
        if (closed) {
            return;
        }
        try {
            DatagramPacket packet = new DatagramPacket(payload, length, InetAddress.getByAddress(remoteIp), remotePort);
            socket.send(packet);
            lastActivityMillis = System.currentTimeMillis();
        } catch (Exception e) {
            DnsVpnService.log("[UDP " + key + "] send FAILED: " + e.getMessage());
        }
    }

    private void pumpFromRemote() {
        byte[] buf = new byte[RECEIVE_BUFFER_SIZE];
        while (!closed) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (Exception e) {
                break; // socket closed, or a real error - either way this session is done
            }
            byte[] payload = new byte[packet.getLength()];
            System.arraycopy(buf, 0, payload, 0, packet.getLength());
            lastActivityMillis = System.currentTimeMillis();
            try {
                // Reply appears to come FROM the real remote address the app sent to, exactly
                // like DnsMessage/IpV4UdpPacket's own reply-building for DNS.
                byte[] responsePacket = IpV4UdpPacket.buildPacket(remoteIp, remotePort, clientIp, clientPort, payload);
                synchronized (tunOut) {
                    tunOut.write(responsePacket);
                }
            } catch (Exception e) {
                DnsVpnService.log("[UDP " + key + "] write to tun FAILED: " + e.getMessage());
                break;
            }
        }
        close();
    }

    /** Closes the real socket and removes this session from DnsVpnService's map. Safe to call more than once. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) { }
        service.removeUdpSession(key);
    }
}
