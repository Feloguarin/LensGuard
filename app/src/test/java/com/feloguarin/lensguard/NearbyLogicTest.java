package com.feloguarin.lensguard;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** Name hints, discovery parsing, report masking and signal trends used by Nearby. */
public class NearbyLogicTest {
    @Test public void cameraWordsMatchWholeWordsAndCommonProductSpellings() {
        for (String name : new String[] {"IPC-5F3A", "SpyCam", "WIFI_CAMERA_01", "V380_PRO_1234", "WyzeCam v3",
                "HIKVISION DS-2CD", "Living room cam", "nanny-cam", "CCTV 4", "IPCam", "MyCamera"}) {
            assertTrue(name, DeviceHints.cameraLikeName(name));
        }
    }

    @Test public void ordinaryNamesDoNotBecomeLeads() {
        for (String name : new String[] {"Campus-WiFi", "Camden Guest", "Cambridge", "Camila's iPhone",
                "Cam's Pixel", "DIRECT-xy-HP Printer", "Hotel Guest 5G", "Galaxy Buds", "Scamp", "", null}) {
            assertFalse(String.valueOf(name), DeviceHints.cameraLikeName(name));
        }
    }

    @Test public void wordsSplitCamelCaseDigitsAndSeparators() {
        assertTrue(DeviceHints.words("SpyCam3").containsAll(java.util.Arrays.asList("spycam3", "spy", "cam3", "cam", "3")));
        assertTrue(DeviceHints.words("V380Pro").contains("v380"));
        assertTrue(DeviceHints.words("IPCamera").contains("camera"));
    }

    @Test public void videoServiceTypesAndCameraDescriptions() {
        assertTrue(DeviceHints.videoServiceType("_rtsp._tcp."));
        assertTrue(DeviceHints.videoServiceType("_ONVIF._tcp"));
        assertFalse(DeviceHints.videoServiceType("_http._tcp."));
        assertFalse(DeviceHints.videoServiceType(null));
        assertTrue(DeviceHints.cameraLikeDescription("urn:schemas-upnp-org:device:DigitalSecurityCamera:1"));
        assertTrue(DeviceHints.cameraLikeDescription("Linux/3.10 UPnP/1.0 IPCamera/1.0"));
        assertFalse(DeviceHints.cameraLikeDescription("Linux/4.9 UPnP/1.0 MediaRenderer/2.0"));
    }

    @Test public void probeAndSearchMessagesFollowTheStandards() {
        String probe = new String(DiscoveryMessages.wsDiscoveryProbe("1234", "dn:NetworkVideoTransmitter"), StandardCharsets.UTF_8);
        assertTrue(probe.contains("<w:MessageID>uuid:1234</w:MessageID>"));
        assertTrue(probe.contains("http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe"));
        assertTrue(probe.contains("<d:Types>dn:NetworkVideoTransmitter</d:Types>"));
        String search = new String(DiscoveryMessages.ssdpSearch(), StandardCharsets.US_ASCII);
        assertTrue(search.startsWith("M-SEARCH * HTTP/1.1\r\n"));
        assertTrue(search.contains("MAN: \"ssdp:discover\"\r\n"));
        assertTrue(search.endsWith("\r\n\r\n"));
    }

    @Test public void onvifProbeMatchBecomesAVideoDeviceReply() {
        String reply = "<?xml version=\"1.0\"?><SOAP-ENV:Envelope><SOAP-ENV:Body><d:ProbeMatches><d:ProbeMatch>"
                + "<wsa:EndpointReference><wsa:Address>urn:uuid:abc</wsa:Address></wsa:EndpointReference>"
                + "<d:Types>dn:NetworkVideoTransmitter tds:Device</d:Types>"
                + "<d:Scopes>onvif://www.onvif.org/type/video_encoder onvif://www.onvif.org/name/Hall%20Cam "
                + "onvif://www.onvif.org/hardware/DS-2CD2032 onvif://www.onvif.org/location/</d:Scopes>"
                + "<d:XAddrs>http://192.168.1.20/onvif/device_service</d:XAddrs>"
                + "</d:ProbeMatch></d:ProbeMatches></SOAP-ENV:Body></SOAP-ENV:Envelope>";
        DiscoveryMessages.Reply parsed = DiscoveryMessages.parse(reply, "192.168.1.20");
        assertNotNull(parsed);
        assertEquals("onvif", parsed.protocol);
        assertEquals("onvif/192.168.1.20", parsed.key);
        assertEquals("Hall Cam", parsed.name);
        assertTrue(parsed.detail.contains("DS-2CD2032"));
        assertTrue(parsed.detail.contains("video_encoder"));
        assertTrue(parsed.cameraRole);
    }

    @Test public void onvifDeviceWithoutVideoRoleIsNotMarked() {
        String reply = "<e:Envelope><e:Body><d:ProbeMatches><d:ProbeMatch><d:Types>tds:Device</d:Types>"
                + "<d:Scopes>onvif://www.onvif.org/type/Network_Access_Controller</d:Scopes>"
                + "</d:ProbeMatch></d:ProbeMatches></e:Body></e:Envelope>";
        DiscoveryMessages.Reply parsed = DiscoveryMessages.parse(reply, "10.0.0.9");
        assertNotNull(parsed);
        assertFalse(parsed.cameraRole);
        assertNull(parsed.name);
    }

    @Test public void ssdpResponseKeepsServerAndDeviceRole() {
        String reply = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nST: urn:schemas-upnp-org:device:DigitalSecurityCamera:1\r\n"
                + "USN: uuid:1::urn:schemas-upnp-org:device:DigitalSecurityCamera:1\r\nServer: Linux UPnP/1.0 Webcam/2\r\n\r\n";
        DiscoveryMessages.Reply parsed = DiscoveryMessages.parse(reply, "192.168.0.5");
        assertNotNull(parsed);
        assertEquals("upnp", parsed.protocol);
        assertEquals("upnp/192.168.0.5", parsed.key);
        assertEquals("Linux UPnP/1.0 Webcam/2", parsed.name);
        assertEquals("DigitalSecurityCamera", parsed.detail);
        assertTrue(parsed.cameraRole);
        DiscoveryMessages.Reply service = DiscoveryMessages.parse(
                "HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\nSERVER: Router/1\r\n\r\n", "192.168.0.1");
        assertNull(service.detail);
        assertFalse(service.cameraRole);
    }

    @Test public void unrelatedOrHostileTrafficIsBoundedOrIgnored() {
        assertNull(DiscoveryMessages.parse("NOTIFY * HTTP/1.1\r\n\r\n", "1.2.3.4"));
        assertNull(DiscoveryMessages.parse("random", "1.2.3.4"));
        assertNull(DiscoveryMessages.parse(null, "1.2.3.4"));
        StringBuilder huge = new StringBuilder("HTTP/1.1 200 OK\r\nSERVER: ");
        for (int i = 0; i < 40_000; i++) huge.append('x');
        DiscoveryMessages.Reply parsed = DiscoveryMessages.parse(huge + "\u0007\r\n\r\n", "1.2.3.4");
        assertNotNull(parsed);
        assertTrue(parsed.name.length() <= 81);
        assertFalse(parsed.name.contains("\u0007"));
    }

    @Test public void textIsCleanedAndAddressesMasked() {
        assertEquals("Hall camera", Text.clean(" Hall\u0000camera ".replace('\u0000', ' '), "x"));
        assertEquals("fallback", Text.clean("   ", "fallback"));
        assertEquals("A B", Text.clean("A​B", null));
        assertEquals("AA:BB:CC:••:••:••", Text.maskAddress("AA:BB:CC:DD:EE:FF"));
        assertEquals("aa-bb-cc-••-••-••", Text.maskAddress("aa-bb-cc-dd-ee-ff"));
        assertEquals("192.168.•.•", Text.maskAddress("192.168.1.20"));
        assertEquals("••••", Text.maskAddress("fe80::1"));
        assertNull(Text.maskAddress(null));
    }

    @Test public void signalTrendReportsDirectionOnlyAfterEnoughHistory() {
        SignalTrend trend = new SignalTrend();
        assertEquals(SignalTrend.STEADY, trend.trend());
        assertEquals(Integer.MIN_VALUE, trend.latest());
        for (int i = 0; i <= 10; i++) trend.add(i * 500L, -80 + i * 2);
        assertEquals(SignalTrend.STRONGER, trend.trend());
        assertEquals(-60, trend.latest());
        SignalTrend falling = new SignalTrend();
        for (int i = 0; i <= 10; i++) falling.add(i * 500L, -50 - i * 2);
        assertEquals(SignalTrend.WEAKER, falling.trend());
        SignalTrend flat = new SignalTrend();
        for (int i = 0; i <= 10; i++) flat.add(i * 500L, i % 2 == 0 ? -70 : -72);
        assertEquals(SignalTrend.STEADY, flat.trend());
        assertEquals(11, flat.recent(5_000, 30_000).length);
        assertEquals(3, flat.recent(5_000, 1_000).length);
    }

    @Test public void signalTrendNeedsThreeSecondsBeforeJudging() {
        SignalTrend trend = new SignalTrend();
        trend.add(0, -90);
        trend.add(1_000, -40);
        assertEquals(SignalTrend.STEADY, trend.trend());
        trend.clear();
        assertTrue(Double.isNaN(trend.smoothed()));
    }
}
