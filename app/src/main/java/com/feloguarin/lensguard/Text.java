package com.feloguarin.lensguard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text from radios and networks is untrusted: strip control characters and bound its length. */
public final class Text {
    private static final Pattern MAC = Pattern.compile("(?i)^([0-9a-f]{2}[:-]){5}[0-9a-f]{2}$");
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}$");

    private Text() {}

    public static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String safe = value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").trim();
        if (safe.isEmpty()) return fallback;
        return safe.length() > 80 ? safe.substring(0, 80) + "…" : safe;
    }

    /**
     * Hides the device-specific part of a hardware or network address for reports. The first half
     * of a MAC address (often a manufacturer prefix) and the network part of an IPv4 address remain
     * so entries can still be told apart.
     */
    public static String maskAddress(String address) {
        if (address == null) return null;
        String value = address.trim();
        if (MAC.matcher(value).matches()) {
            char separator = value.charAt(2);
            return value.substring(0, 8) + separator + "••" + separator + "••" + separator + "••";
        }
        Matcher ip = IPV4.matcher(value);
        if (ip.matches()) return ip.group(1) + "." + ip.group(2) + ".•.•";
        return "••••";
    }
}
