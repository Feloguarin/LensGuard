package com.feloguarin.lensguard;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
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

    @Test public void blockMaximumKeepsAOnePixelHighlightThatPointSamplingWouldSkip() {
        int width = 80, height = 60;
        byte[] plane = new byte[width * height];
        Arrays.fill(plane, (byte) 20);
        // Off the every-fourth-pixel grid that 1.x sampled.
        plane[30 * width + 41] = (byte) 255;
        GlintDetector.Grid grid = GlintDetector.reduce(plane, width, 1, 0, 0, width, height, 4);
        assertNotNull(grid);
        assertEquals(20, grid.width);
        assertEquals(15, grid.height);
        List<GlintDetector.Blob> blobs = GlintDetector.find(grid);
        assertEquals(1, blobs.size());
        assertEquals(10, blobs.get(0).x, 0.01);
        assertEquals(7, blobs.get(0).y, 0.01);
        assertEquals(255, blobs.get(0).peak);
        int sampled = 0;
        for (int y = 0; y < height; y += 4) for (int x = 0; x < width; x += 4) if ((plane[y * width + x] & 255) > 238) sampled++;
        assertEquals("Point sampling misses the same highlight", 0, sampled);
    }

    @Test public void reductionHonoursCropStridesAndAverageBrightness() {
        int rowStride = 64, pixelStride = 2, left = 4, top = 8, width = 20, height = 24;
        byte[] plane = new byte[rowStride * 40];
        Arrays.fill(plane, (byte) 100);
        plane[(top + 9) * rowStride + (left + 13) * pixelStride] = (byte) 250; // Inside the crop.
        plane[2 * rowStride + 2] = (byte) 255; // Above the crop.
        GlintDetector.Grid grid = GlintDetector.reduce(plane, rowStride, pixelStride, left, top, width, height, 2);
        assertNotNull(grid);
        assertEquals(10, grid.width);
        assertEquals(12, grid.height);
        assertEquals(100, grid.averageLuminance);
        List<GlintDetector.Blob> blobs = GlintDetector.find(grid);
        assertEquals(1, blobs.size());
        assertEquals(6, blobs.get(0).x, 0.01);
        assertEquals(4, blobs.get(0).y, 0.01);
    }

    @Test public void reductionRejectsRegionsOutsideTheBuffer() {
        assertNull(GlintDetector.reduce(new byte[100], 10, 1, 0, 0, 20, 20, 2));
        assertNull(GlintDetector.reduce(new byte[400], 20, 1, 0, 0, 4, 4, 2));
        assertNull(GlintDetector.reduce(null, 20, 1, 0, 0, 20, 20, 2));
        assertTrue(GlintDetector.find((GlintDetector.Grid) null).isEmpty());
    }

    @Test public void surroundUsesTheMeanGridSoGlareDoesNotHideAPoint() {
        byte[] peak = new byte[400], mean = new byte[400];
        Arrays.fill(peak, (byte) 200);
        Arrays.fill(mean, (byte) 60);
        peak[105] = (byte) 255;
        mean[105] = (byte) 120;
        assertEquals(0, GlintDetector.find(peak, peak, 20, 20).size());
        assertEquals(1, GlintDetector.find(peak, mean, 20, 20).size());
    }
}
