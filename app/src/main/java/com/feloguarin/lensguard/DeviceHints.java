package com.feloguarin.lensguard;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Marks names and services that camera products commonly use. A hint is a reason to look closer:
 * plenty of cameras show none, and an ordinary device can share a name.
 */
public final class DeviceHints {
    private DeviceHints() {}

    /** Whole words only, so "Campus" or "Camden" do not match "cam". */
    private static final Set<String> CAMERA_WORDS = new HashSet<>(Arrays.asList(
            "cam", "cams", "camera", "cameras", "webcam", "ipcam", "ipcamera", "ipc", "cctv",
            "dvr", "nvr", "spy", "spycam", "minicam", "nannycam", "hiddencam", "wificam", "hdcam",
            "v380", "lookcam", "xmeye", "icsee", "camhi", "yoosee", "ezviz", "hikvision", "dahua",
            "reolink", "wyze", "wyzecam", "eufycam", "arlo", "foscam", "amcrest", "annke", "imou",
            "jooan", "vstarcam", "sricam", "escam", "zmodo", "furbo", "petcube"));

    /** UPnP device descriptions that state a camera role. */
    private static final String[] CAMERA_DESCRIPTIONS = {
            "digitalsecuritycamera", "ipcamera", "networkcamera", "webcam"
    };

    public static boolean cameraLikeName(String name) {
        if (name == null) return false;
        for (String word : words(name)) if (CAMERA_WORDS.contains(word)) return true;
        return false;
    }

    /** mDNS service types that carry video streams or camera control. */
    public static boolean videoServiceType(String serviceType) {
        if (serviceType == null) return false;
        String type = serviceType.toLowerCase(Locale.ROOT);
        return type.startsWith("_rtsp.") || type.startsWith("_onvif.");
    }

    /** A UPnP SERVER header or search target that names a camera product or role. */
    public static boolean cameraLikeDescription(String description) {
        if (description == null) return false;
        String compact = description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String role : CAMERA_DESCRIPTIONS) if (compact.contains(role)) return true;
        return cameraLikeName(description);
    }

    /** Lower-case words, including camel-case and digit/letter parts: "SpyCam3" → spycam3, spy, cam3, cam, 3. */
    static Set<String> words(String value) {
        Set<String> result = new LinkedHashSet<>();
        // A possessive ("Cam's phone") names a person, not a product, so the whole word is dropped.
        String text = value.replaceAll("[\\p{L}\\p{N}]+['’]s\\b", " ");
        for (String raw : text.split("[^\\p{L}\\p{N}]+")) {
            if (raw.isEmpty()) continue;
            result.add(raw.toLowerCase(Locale.ROOT));
            for (String part : raw.split("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})")) {
                result.add(part.toLowerCase(Locale.ROOT));
                for (String piece : part.split("(?<=\\p{N})(?=\\p{L})")) result.add(piece.toLowerCase(Locale.ROOT));
                for (String piece : part.split("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")) {
                    result.add(piece.toLowerCase(Locale.ROOT));
                }
            }
        }
        result.remove("");
        return result;
    }
}
