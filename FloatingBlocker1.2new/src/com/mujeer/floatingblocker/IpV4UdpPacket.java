package com.mujeer.floatingblocker;

import java.net.InetAddress;

/**
 * Minimal, defensive IPv4+UDP packet parser/builder - just enough to
 * extract a DNS query's payload and construct a reply packet addressed
 * back to whoever sent the query. Every parse step is bounds-checked;
 * anything unexpected returns null rather than guessing, so a malformed
 * or unusual packet gets safely dropped instead of crashing the service.
 */
public class IpV4UdpPacket {

    public byte[] sourceIp;      // the original sender's IP (4 bytes) - our reply goes back TO this
    public byte[] destIp;        // the original destination IP (4 bytes) - our reply comes FROM this
    public int sourcePort;
    public int destPort;
    public byte[] payload;       // the UDP payload (the raw DNS message)
    public int payloadLength;

    /** Quick peek at just the IPv4 protocol byte, without fully parsing - used by DnsVpnService's dispatch to decide UDP vs TCP vs other before committing to a full parse. Returns -1 if this isn't a parseable IPv4 packet. */
    public static int peekProtocol(byte[] data, int length) {
        if (length < 20) {
            return -1;
        }
        int version = (data[0] & 0xFF) >> 4;
        if (version != 4) {
            return -1;
        }
        return data[9] & 0xFF;
    }

    /** Quick peek at just the UDP destination port, without fully parsing - lets DnsVpnService route a packet to the right dispatch pool (DNS vs generic relay) before paying for a full parse. Returns -1 if this isn't a parseable IPv4/UDP packet. */
    public static int peekUdpDestPort(byte[] data, int length) {
        if (length < 20) {
            return -1;
        }
        int versionAndIhl = data[0] & 0xFF;
        if ((versionAndIhl >> 4) != 4) {
            return -1;
        }
        int ipHeaderLength = (versionAndIhl & 0x0F) * 4;
        if (ipHeaderLength < 20 || length < ipHeaderLength + 4) {
            return -1;
        }
        int udpStart = ipHeaderLength;
        return ((data[udpStart + 2] & 0xFF) << 8) | (data[udpStart + 3] & 0xFF);
    }

    /** Parses a raw packet read from the VPN's TUN interface. Returns null if it isn't IPv4/UDP or is malformed. */
    public static IpV4UdpPacket parse(byte[] data, int length) {
        if (length < 20) {
            return null; // too short for even a minimal IPv4 header
        }
        int versionAndIhl = data[0] & 0xFF;
        int version = versionAndIhl >> 4;
        if (version != 4) {
            return null; // only IPv4 handled
        }
        int ihl = versionAndIhl & 0x0F;
        int ipHeaderLength = ihl * 4;
        if (ipHeaderLength < 20 || length < ipHeaderLength + 8) {
            return null; // header too short, or not enough room for a UDP header after it
        }
        int protocol = data[9] & 0xFF;
        if (protocol != 17) {
            return null; // not UDP
        }

        IpV4UdpPacket p = new IpV4UdpPacket();
        p.sourceIp = new byte[4];
        p.destIp = new byte[4];
        System.arraycopy(data, 12, p.sourceIp, 0, 4);
        System.arraycopy(data, 16, p.destIp, 0, 4);

        int udpStart = ipHeaderLength;
        p.sourcePort = ((data[udpStart] & 0xFF) << 8) | (data[udpStart + 1] & 0xFF);
        p.destPort = ((data[udpStart + 2] & 0xFF) << 8) | (data[udpStart + 3] & 0xFF);
        int udpLength = ((data[udpStart + 4] & 0xFF) << 8) | (data[udpStart + 5] & 0xFF);
        int udpPayloadStart = udpStart + 8;
        int udpPayloadLength = udpLength - 8;

        if (udpPayloadLength <= 0 || udpPayloadStart + udpPayloadLength > length) {
            return null; // declared length doesn't fit what we actually have
        }

        p.payload = new byte[udpPayloadLength];
        System.arraycopy(data, udpPayloadStart, p.payload, 0, udpPayloadLength);
        p.payloadLength = udpPayloadLength;
        return p;
    }

    /** Builds a reply packet: swaps source/dest so it goes back to the original querying app, with the given DNS response as payload. */
    public static byte[] buildResponsePacket(IpV4UdpPacket original, byte[] responsePayload) {
        // Reply comes FROM the original destination (our virtual DNS IP), TO the original source.
        return buildPacket(original.destIp, original.destPort, original.sourceIp, original.sourcePort, responsePayload);
    }

    /**
     * General-purpose IPv4/UDP packet builder: wraps an arbitrary UDP payload with a
     * real, checksummed IPv4+UDP header. Used both for real replies going back out to
     * tun (via buildResponsePacket above) and by DnsVpnService's in-process self-test,
     * which needs a synthetic *query* packet (as if it had come off the tun interface)
     * without ever touching the tun interface or Android's VPN routing at all.
     */
    public static byte[] buildPacket(byte[] srcIp, int srcPort, byte[] destIp, int destPort, byte[] payload) {
        int totalLength = 20 + 8 + payload.length;
        byte[] out = new byte[totalLength];

        // IPv4 header
        out[0] = 0x45; // version 4, IHL 5 (20-byte header, no options)
        out[1] = 0;
        out[2] = (byte) ((totalLength >> 8) & 0xFF);
        out[3] = (byte) (totalLength & 0xFF);
        out[4] = 0; out[5] = 0; // identification - unused, fine as 0
        out[6] = 0x40; out[7] = 0; // don't-fragment flag set, fragment offset 0
        out[8] = 64; // TTL
        out[9] = 17; // protocol UDP
        out[10] = 0; out[11] = 0; // checksum - filled in below

        System.arraycopy(srcIp, 0, out, 12, 4);
        System.arraycopy(destIp, 0, out, 16, 4);

        int ipChecksum = ChecksumUtil.computeIpChecksum(out, 20);
        out[10] = (byte) ((ipChecksum >> 8) & 0xFF);
        out[11] = (byte) (ipChecksum & 0xFF);

        // UDP header + payload - payload is written first so the checksum
        // below can be computed over the complete section, not just the
        // header. A checksum of 0 is technically legal for IPv4 UDP
        // ("not computed"), but some network stacks silently drop packets
        // that use it anyway - computing a real one is the safe choice.
        int udpLength = 8 + payload.length;
        out[20] = (byte) ((srcPort >> 8) & 0xFF);
        out[21] = (byte) (srcPort & 0xFF);
        out[22] = (byte) ((destPort >> 8) & 0xFF);
        out[23] = (byte) (destPort & 0xFF);
        out[24] = (byte) ((udpLength >> 8) & 0xFF);
        out[25] = (byte) (udpLength & 0xFF);
        out[26] = 0; out[27] = 0; // zero while computing
        System.arraycopy(payload, 0, out, 28, payload.length);

        int udpChecksum = ChecksumUtil.computeTransportChecksum(srcIp, destIp, ChecksumUtil.PROTOCOL_UDP, out, 20, udpLength);
        out[26] = (byte) ((udpChecksum >> 8) & 0xFF);
        out[27] = (byte) (udpChecksum & 0xFF);

        return out;
    }

    public InetAddress sourceIpAsInetAddress() throws Exception {
        return InetAddress.getByAddress(sourceIp);
    }
}
