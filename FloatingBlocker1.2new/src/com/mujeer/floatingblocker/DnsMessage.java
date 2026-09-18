package com.mujeer.floatingblocker;

/**
 * Minimal, defensive DNS wire-format handling - just enough to read the
 * queried domain name out of a query, and build a synthetic NXDOMAIN
 * reply. Every step is bounds-checked; malformed input returns null
 * rather than guessing.
 */
public class DnsMessage {

    /** Reads the domain name being queried (the QNAME) out of a raw DNS query. Returns null if it can't be parsed. */
    public static String parseQuestionName(byte[] data, int length) {
        if (length < 12) {
            return null; // shorter than a DNS header
        }
        int qdCount = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        if (qdCount < 1) {
            return null; // no question section - not a normal query
        }

        StringBuilder name = new StringBuilder();
        int pos = 12;
        int labelsRead = 0;
        while (pos < length) {
            int labelLength = data[pos] & 0xFF;
            if (labelLength == 0) {
                pos++;
                break; // end of name
            }
            if ((labelLength & 0xC0) == 0xC0) {
                return null; // compression pointer in a question section is unexpected here - bail out safely
            }
            pos++;
            if (pos + labelLength > length) {
                return null; // label runs past the end of the packet
            }
            if (name.length() > 0) {
                name.append('.');
            }
            for (int i = 0; i < labelLength; i++) {
                name.append((char) data[pos + i]);
            }
            pos += labelLength;
            labelsRead++;
            if (labelsRead > 128) {
                return null; // sanity limit - something is wrong, bail out rather than loop
            }
        }
        if (name.length() == 0) {
            return null;
        }
        return name.toString();
    }

    /** Builds a minimal, valid raw DNS query (A-record lookup, recursion desired) for the given domain. Used both by DiagnosticActivity's direct-socket probes and by DnsVpnService's in-process self-test. */
    public static byte[] buildQuery(String domain) {
        String[] labels = domain.split("\\.");
        int qnameLength = 1; // final zero byte
        for (String label : labels) {
            qnameLength += 1 + label.length();
        }
        byte[] packet = new byte[12 + qnameLength + 4];

        int id = (int) (System.nanoTime() & 0xFFFF);
        packet[0] = (byte) ((id >> 8) & 0xFF);
        packet[1] = (byte) (id & 0xFF);
        packet[2] = 0x01; // flags: standard query, recursion desired
        packet[3] = 0x00;
        packet[4] = 0x00; packet[5] = 0x01; // QDCOUNT = 1
        packet[6] = 0x00; packet[7] = 0x00; // ANCOUNT = 0
        packet[8] = 0x00; packet[9] = 0x00; // NSCOUNT = 0
        packet[10] = 0x00; packet[11] = 0x00; // ARCOUNT = 0

        int pos = 12;
        for (String label : labels) {
            packet[pos++] = (byte) label.length();
            for (int i = 0; i < label.length(); i++) {
                packet[pos++] = (byte) label.charAt(i);
            }
        }
        packet[pos++] = 0x00; // end of QNAME

        packet[pos++] = 0x00; packet[pos++] = 0x01; // QTYPE = A
        packet[pos++] = 0x00; packet[pos] = 0x01; // QCLASS = IN

        return packet;
    }

    /** Builds an NXDOMAIN reply for the given query - same ID and question section, marked as a response with RCODE=NXDOMAIN, no answer records. */
    public static byte[] buildNxDomainResponse(byte[] query, int length) {
        if (length < 12) {
            return null;
        }
        byte[] response = new byte[length];
        System.arraycopy(query, 0, response, 0, length);

        // Flags byte 1 (offset 2): set QR (response) bit, keep RD (recursion desired) as the client sent it.
        response[2] = (byte) (0x80 | (query[2] & 0x01));
        // Flags byte 2 (offset 3): set RA (recursion available) and RCODE=3 (NXDOMAIN).
        response[3] = (byte) 0x83;

        // ANCOUNT, NSCOUNT, ARCOUNT all zero - no records beyond the echoed question.
        response[6] = 0; response[7] = 0;
        response[8] = 0; response[9] = 0;
        response[10] = 0; response[11] = 0;

        return response;
    }
}
