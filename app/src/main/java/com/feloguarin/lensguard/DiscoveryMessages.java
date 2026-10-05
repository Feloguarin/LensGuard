package com.feloguarin.lensguard;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds and parses standard local-network discovery messages: ONVIF WS-Discovery probes and UPnP
 * SSDP searches. Replies are untrusted text from the network and are bounded before display.
 */
public final class DiscoveryMessages {
    public static final String MULTICAST_GROUP = "239.255.255.250";
    public static final int WS_DISCOVERY_PORT = 3702;
    public static final int SSDP_PORT = 1900;
    static final int MAX_REPLY_BYTES = 16 * 1024;

    private DiscoveryMessages() {}

    /** One device that answered a discovery request. */
    public static final class Reply {
        public final String protocol; // "onvif" or "upnp"
        public final String key;
        public final String name;
        public final String detail;
        public final boolean cameraRole;

        Reply(String protocol, String key, String name, String detail, boolean cameraRole) {
            this.protocol = protocol;
            this.key = key;
            this.name = name;
            this.detail = detail;
            this.cameraRole = cameraRole;
        }
    }

    /** An ONVIF probe for {@code types}, for example "dn:NetworkVideoTransmitter" or "tds:Device". */
    public static byte[] wsDiscoveryProbe(String messageId, String types) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\""
                + " xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\""
                + " xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\""
                + " xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\""
                + " xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">"
                + "<e:Header><w:MessageID>uuid:" + messageId + "</w:MessageID>"
                + "<w:To e:mustUnderstand=\"true\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>"
                + "<w:Action e:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>"
                + "</e:Header><e:Body><d:Probe><d:Types>" + types + "</d:Types></d:Probe></e:Body></e:Envelope>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] ssdpSearch() {
        String request = "M-SEARCH * HTTP/1.1\r\n"
                + "HOST: " + MULTICAST_GROUP + ":" + SSDP_PORT + "\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                + "MX: 2\r\n"
                + "ST: ssdp:all\r\n"
                + "\r\n";
        return request.getBytes(StandardCharsets.US_ASCII);
    }

    /** Parses a reply received from {@code sender}. Returns null for anything that is not a reply. */
    public static Reply parse(String payload, String sender) {
        if (payload == null || sender == null) return null;
        if (payload.length() > MAX_REPLY_BYTES) payload = payload.substring(0, MAX_REPLY_BYTES);
        if (payload.regionMatches(true, 0, "HTTP/1.1 200", 0, 12)) return parseSsdp(payload, sender);
        if (payload.contains("ProbeMatch")) return parseProbeMatch(payload, sender);
        return null;
    }

    static Reply parseSsdp(String payload, String sender) {
        String server = null, searchTarget = null;
        for (String line : payload.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String header = line.substring(0, colon).trim().toUpperCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (header.equals("SERVER")) server = value;
            else if (header.equals("ST")) searchTarget = value;
        }
        String role = shortSearchTarget(searchTarget);
        boolean camera = DeviceHints.cameraLikeDescription(server) || DeviceHints.cameraLikeDescription(searchTarget);
        return new Reply("upnp", "upnp/" + sender, Text.clean(server, null), role, camera);
    }

    static Reply parseProbeMatch(String payload, String sender) {
        String types = element(payload, "Types");
        String scopes = element(payload, "Scopes");
        String name = null, hardware = null;
        Set<String> roles = new LinkedHashSet<>();
        boolean video = types != null && types.contains("NetworkVideoTransmitter");
        if (scopes != null) {
            for (String scope : scopes.trim().split("\\s+")) {
                String lower = scope.toLowerCase(Locale.ROOT);
                if (lower.startsWith("onvif://www.onvif.org/name/")) name = decode(scope.substring(27));
                else if (lower.startsWith("onvif://www.onvif.org/hardware/")) hardware = decode(scope.substring(31));
                else if (lower.startsWith("onvif://www.onvif.org/type/")) {
                    String role = decode(scope.substring(27));
                    roles.add(role);
                    String compact = role.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                    if (compact.contains("videoencoder") || compact.contains("networkvideotransmitter")
                            || compact.equals("nvt") || compact.contains("ptz")) video = true;
                }
            }
        }
        StringBuilder detail = new StringBuilder();
        if (hardware != null) detail.append(hardware);
        for (String role : roles) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(role);
        }
        // One entry per address: a device often answers both probe types.
        return new Reply("onvif", "onvif/" + sender, Text.clean(name, null),
                Text.clean(detail.toString(), null), video);
    }

    /** "urn:schemas-upnp-org:device:MediaRenderer:1" → "MediaRenderer"; services and IDs → null. */
    static String shortSearchTarget(String target) {
        if (target == null) return null;
        String[] parts = target.split(":");
        if (target.startsWith("urn:") && parts.length >= 5 && parts[parts.length - 3].equalsIgnoreCase("device")) {
            return Text.clean(parts[parts.length - 2], null);
        }
        return null;
    }

    /** Text content of the first element with this local name, whatever its namespace prefix. */
    static String element(String xml, String localName) {
        Matcher matcher = Pattern.compile("<(?:[\\w.-]+:)?" + localName + "(?:\\s[^>]*)?>([^<]*)</(?:[\\w.-]+:)?" + localName + "\\s*>")
                .matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8");
        } catch (RuntimeException | java.io.UnsupportedEncodingException malformed) {
            return value;
        }
    }
}
