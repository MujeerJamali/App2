package com.mujeer.floatingblocker;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls just the domain out of a URL or shared text, never a specific page/path. */
public class DomainExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    /**
     * Extracts a normalized domain from either a plain domain, a full URL, or
     * shared text that contains a URL somewhere in it (some browsers share
     * "Title\nhttps://url" rather than just the URL alone). Returns null if
     * nothing usable is found.
     */
    public static String extract(String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim();
        if (text.isEmpty()) {
            return null;
        }

        String urlPart = text;
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            urlPart = m.group();
        } else if (!text.contains("://")) {
            // Not a URL at all - might just be a bare domain typed manually.
            urlPart = "https://" + text;
        }

        try {
            URI uri = new URI(urlPart);
            String host = uri.getHost();
            return BlockedWebsitesStorage.normalize(host);
        } catch (Exception e) {
            return null;
        }
    }
}
