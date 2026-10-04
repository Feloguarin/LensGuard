package com.feloguarin.lensguard;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class GlintDetectorTest {
    @Test public void darkRoomHasNoHighlights() { assertEquals(0, GlintDetector.count(new byte[400], 20, 20)); }
    @Test public void isolatedHighlightsCount() {
        byte[] y = new byte[400]; y[105] = (byte)255; y[294] = (byte)250;
        assertEquals(2, GlintDetector.count(y, 20, 20));
    }
    @Test public void adjacentBrightPixelsAreOneClue() {
        byte[] y = new byte[400]; y[105] = (byte)255; y[106] = (byte)255;
        assertEquals(1, GlintDetector.count(y, 20, 20));
    }
    @Test public void broadBrightWindowDoesNotCount() {
        byte[] y = new byte[400]; Arrays.fill(y, (byte)255);
        assertEquals(0, GlintDetector.count(y, 20, 20));
    }
    @Test public void borderAndBrightSurroundingsDoNotCount() {
        byte[] y = new byte[400]; Arrays.fill(y, (byte)200); y[105] = (byte)255; y[0] = (byte)255;
        assertEquals(0, GlintDetector.count(y, 20, 20));
    }
    @Test public void malformedFrameIsSafe() { assertEquals(0, GlintDetector.count(new byte[2], 20, 20)); }
}
