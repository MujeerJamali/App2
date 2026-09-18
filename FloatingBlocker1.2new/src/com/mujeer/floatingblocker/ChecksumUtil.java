package com.mujeer.floatingblocker;

/**
 * IPv4 header checksum (RFC791) and transport-layer pseudo-header checksum
 * (RFC793/RFC768 - identical algorithm for TCP and UDP, just a different
 * protocol number) in one place, so IpV4UdpPacket and TcpPacket don't each
 * carry their own copy. A checksum bug here breaks every packet built with
 * it silently (the OS just discards anything that doesn't check out) -
 * worth having exactly one implementation instead of two that could drift.
 */
public class ChecksumUtil {

    public static final int PROTOCOL_TCP = 6;
    public static final int PROTOCOL_UDP = 17;

    /** Standard IPv4 header checksum over the given header bytes (normally the first 20 bytes, checksum field zeroed). */
    public static int computeIpChecksum(byte[] header, int headerLength) {
        long sum = 0;
        for (int i = 0; i < headerLength; i += 2) {
            int word = ((header[i] & 0xFF) << 8);
            if (i + 1 < headerLength) {
                word |= (header[i + 1] & 0xFF);
            }
            sum += word;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }

    /**
     * TCP/UDP checksum: pseudo-header (src IP, dst IP, protocol, segment
     * length) followed by the real transport header+payload, with the
     * transport checksum field itself zeroed while computing. protocol is
     * PROTOCOL_TCP or PROTOCOL_UDP - the pseudo-header is identical between
     * the two apart from that one byte.
     */
    public static int computeTransportChecksum(byte[] srcIp, byte[] dstIp, int protocol, byte[] packet, int segmentStart, int segmentLength) {
        long sum = 0;
        sum += ((srcIp[0] & 0xFF) << 8) | (srcIp[1] & 0xFF);
        sum += ((srcIp[2] & 0xFF) << 8) | (srcIp[3] & 0xFF);
        sum += ((dstIp[0] & 0xFF) << 8) | (dstIp[1] & 0xFF);
        sum += ((dstIp[2] & 0xFF) << 8) | (dstIp[3] & 0xFF);
        sum += protocol; // zero byte + protocol byte, combined as one 16-bit word
        sum += segmentLength;

        int end = segmentStart + segmentLength;
        for (int i = segmentStart; i < end; i += 2) {
            int word = (packet[i] & 0xFF) << 8;
            if (i + 1 < end) {
                word |= (packet[i + 1] & 0xFF);
            }
            sum += word;
        }

        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        int checksum = (int) (~sum & 0xFFFF);
        // RFC768: a computed value of 0 means "no checksum" for UDP specifically - substitute all-ones.
        // Harmless (and not required, but simplest to keep the same rule) for TCP too, since a real
        // all-zero TCP checksum is not a meaningful case we'd ever legitimately produce.
        return checksum == 0 ? 0xFFFF : checksum;
    }
}
