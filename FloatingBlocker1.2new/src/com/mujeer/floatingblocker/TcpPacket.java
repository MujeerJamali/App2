package com.mujeer.floatingblocker;

/**
 * Minimal, defensive IPv4+TCP packet parser/builder for TcpSession's relay.
 * Deliberately does not parse or emit TCP options beyond an optional MSS on
 * our own SYN-ACK (see build()) - everything else uses a plain 20-byte TCP
 * header with no options at all, which also means we never advertise or
 * honor window scaling (RFC1323): the window field is used and interpreted
 * as a literal, un-scaled 0-65535 value throughout. That caps our
 * effective per-connection throughput well below what modern high-bandwidth
 * links could do, but it's simple, correct, and plenty for typical
 * on-device browsing - a real limitation worth knowing about, not a hidden
 * one.
 */
public class TcpPacket {

    public static final int FLAG_FIN = 0x01;
    public static final int FLAG_SYN = 0x02;
    public static final int FLAG_RST = 0x04;
    public static final int FLAG_PSH = 0x08;
    public static final int FLAG_ACK = 0x10;
    public static final int FLAG_URG = 0x20;

    public byte[] sourceIp;
    public byte[] destIp;
    public int sourcePort;
    public int destPort;
    public long seq;   // stored as unsigned 32-bit value in a (signed) long
    public long ack;
    public int flags;
    public int window;
    public byte[] payload;
    public int payloadLength;

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    /** Parses a raw packet already confirmed (via IpV4UdpPacket.peekProtocol) to be IPv4/TCP. Returns null if malformed. */
    public static TcpPacket parse(byte[] data, int length) {
        if (length < 20) {
            return null;
        }
        int versionAndIhl = data[0] & 0xFF;
        if ((versionAndIhl >> 4) != 4) {
            return null;
        }
        int ipHeaderLength = (versionAndIhl & 0x0F) * 4;
        if (ipHeaderLength < 20 || length < ipHeaderLength + 20) {
            return null; // not enough room for even a minimal (option-less) TCP header
        }
        if ((data[9] & 0xFF) != ChecksumUtil.PROTOCOL_TCP) {
            return null;
        }

        TcpPacket p = new TcpPacket();
        p.sourceIp = new byte[4];
        p.destIp = new byte[4];
        System.arraycopy(data, 12, p.sourceIp, 0, 4);
        System.arraycopy(data, 16, p.destIp, 0, 4);

        int t = ipHeaderLength; // start of TCP header
        p.sourcePort = ((data[t] & 0xFF) << 8) | (data[t + 1] & 0xFF);
        p.destPort = ((data[t + 2] & 0xFF) << 8) | (data[t + 3] & 0xFF);
        p.seq = ((long) (data[t + 4] & 0xFF) << 24) | ((data[t + 5] & 0xFF) << 16) | ((data[t + 6] & 0xFF) << 8) | (data[t + 7] & 0xFF);
        p.ack = ((long) (data[t + 8] & 0xFF) << 24) | ((data[t + 9] & 0xFF) << 16) | ((data[t + 10] & 0xFF) << 8) | (data[t + 11] & 0xFF);
        int dataOffset = ((data[t + 12] & 0xFF) >> 4) * 4;
        p.flags = data[t + 13] & 0x3F;
        p.window = ((data[t + 14] & 0xFF) << 8) | (data[t + 15] & 0xFF);

        if (dataOffset < 20) {
            return null;
        }
        int payloadStart = t + dataOffset; // skips any options - we never need to read them
        int payloadLen = length - payloadStart;
        if (payloadLen < 0) {
            return null; // declared data offset doesn't fit what we actually have
        }
        p.payload = new byte[payloadLen];
        if (payloadLen > 0) {
            System.arraycopy(data, payloadStart, p.payload, 0, payloadLen);
        }
        p.payloadLength = payloadLen;
        return p;
    }

    /** Builds a raw IPv4+TCP segment with no options. seq/ack are unsigned 32-bit values (pass as long, low 32 bits used). */
    public static byte[] build(byte[] srcIp, int srcPort, byte[] destIp, int destPort, long seq, long ack, int flags, int window, byte[] payload) {
        return build(srcIp, srcPort, destIp, destPort, seq, ack, flags, window, payload, null);
    }

    /** Same as build(), but with an explicit raw options block (already padded to a multiple of 4 bytes) inserted after the fixed 20-byte header - used only for our SYN-ACK's MSS option. */
    public static byte[] build(byte[] srcIp, int srcPort, byte[] destIp, int destPort, long seq, long ack, int flags, int window, byte[] payload, byte[] options) {
        int payloadLength = payload == null ? 0 : payload.length;
        int optionsLength = options == null ? 0 : options.length; // caller guarantees this is already a multiple of 4
        int tcpHeaderLength = 20 + optionsLength;
        int totalLength = 20 + tcpHeaderLength + payloadLength;
        byte[] out = new byte[totalLength];

        // IPv4 header
        out[0] = 0x45;
        out[1] = 0;
        out[2] = (byte) ((totalLength >> 8) & 0xFF);
        out[3] = (byte) (totalLength & 0xFF);
        out[4] = 0; out[5] = 0;
        out[6] = 0x40; out[7] = 0; // don't-fragment, offset 0
        out[8] = 64; // TTL
        out[9] = (byte) ChecksumUtil.PROTOCOL_TCP;
        out[10] = 0; out[11] = 0; // IP checksum - filled below
        System.arraycopy(srcIp, 0, out, 12, 4);
        System.arraycopy(destIp, 0, out, 16, 4);
        int ipChecksum = ChecksumUtil.computeIpChecksum(out, 20);
        out[10] = (byte) ((ipChecksum >> 8) & 0xFF);
        out[11] = (byte) (ipChecksum & 0xFF);

        // TCP header
        int t = 20;
        out[t] = (byte) ((srcPort >> 8) & 0xFF);
        out[t + 1] = (byte) (srcPort & 0xFF);
        out[t + 2] = (byte) ((destPort >> 8) & 0xFF);
        out[t + 3] = (byte) (destPort & 0xFF);
        out[t + 4] = (byte) ((seq >> 24) & 0xFF);
        out[t + 5] = (byte) ((seq >> 16) & 0xFF);
        out[t + 6] = (byte) ((seq >> 8) & 0xFF);
        out[t + 7] = (byte) (seq & 0xFF);
        out[t + 8] = (byte) ((ack >> 24) & 0xFF);
        out[t + 9] = (byte) ((ack >> 16) & 0xFF);
        out[t + 10] = (byte) ((ack >> 8) & 0xFF);
        out[t + 11] = (byte) (ack & 0xFF);
        int dataOffsetWord = (tcpHeaderLength / 4) << 4; // data offset in the top 4 bits, reserved bits 0
        out[t + 12] = (byte) dataOffsetWord;
        out[t + 13] = (byte) (flags & 0x3F);
        out[t + 14] = (byte) ((window >> 8) & 0xFF);
        out[t + 15] = (byte) (window & 0xFF);
        out[t + 16] = 0; out[t + 17] = 0; // checksum - filled below
        out[t + 18] = 0; out[t + 19] = 0; // urgent pointer - unused

        if (optionsLength > 0) {
            System.arraycopy(options, 0, out, t + 20, optionsLength);
        }
        if (payloadLength > 0) {
            System.arraycopy(payload, 0, out, t + tcpHeaderLength, payloadLength);
        }

        int tcpSegmentLength = tcpHeaderLength + payloadLength;
        int tcpChecksum = ChecksumUtil.computeTransportChecksum(srcIp, destIp, ChecksumUtil.PROTOCOL_TCP, out, t, tcpSegmentLength);
        out[t + 16] = (byte) ((tcpChecksum >> 8) & 0xFF);
        out[t + 17] = (byte) (tcpChecksum & 0xFF);

        return out;
    }

    /** A 4-byte MSS option (kind=2, length=4, value=mss) - the only TCP option this relay ever sends, and only on our SYN-ACK. Without it the client defaults to a 536-byte MSS, which works but is needlessly slow. */
    public static byte[] mssOption(int mss) {
        return new byte[] { 0x02, 0x04, (byte) ((mss >> 8) & 0xFF), (byte) (mss & 0xFF) };
    }
}
