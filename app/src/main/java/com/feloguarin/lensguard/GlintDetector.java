package com.feloguarin.lensguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small isolated highlights only. This heuristic never classifies a camera. */
public final class GlintDetector {
    static final int BRIGHT = 238;
    static final int DARK_SURROUND = 170;

    private GlintDetector() {}

    /** A small bright region. Coordinates are grid cells; the centre is brightness-weighted. */
    public static final class Blob {
        public final float x;
        public final float y;
        public final int area;
        public final int peak;

        Blob(float x, float y, int area, int peak) {
            this.x = x;
            this.y = y;
            this.area = area;
            this.peak = peak;
        }
    }

    /**
     * Block-reduced luminance. The block maximum keeps a one-pixel highlight that point sampling
     * could skip; the block mean describes the surroundings without that highlight's glare.
     */
    public static final class Grid {
        public final byte[] peak;
        public final byte[] mean;
        public final int width;
        public final int height;
        public final int averageLuminance;

        Grid(byte[] peak, byte[] mean, int width, int height, int averageLuminance) {
            this.peak = peak;
            this.mean = mean;
            this.width = width;
            this.height = height;
            this.averageLuminance = averageLuminance;
        }
    }

    /** Reduces a luminance plane region into step × step blocks. Returns null for unusable input. */
    public static Grid reduce(byte[] plane, int rowStride, int pixelStride, int left, int top,
                              int width, int height, int step) {
        if (plane == null || step < 1 || rowStride < 1 || pixelStride < 1 || left < 0 || top < 0) return null;
        int gridWidth = width / step, gridHeight = height / step;
        if (gridWidth < 3 || gridHeight < 3) return null;
        long last = (long) (top + gridHeight * step - 1) * rowStride
                + (long) (left + gridWidth * step - 1) * pixelStride;
        if (last >= plane.length) return null;
        byte[] peak = new byte[gridWidth * gridHeight];
        byte[] mean = new byte[gridWidth * gridHeight];
        int[] maximum = new int[gridWidth];
        int[] sum = new int[gridWidth];
        long total = 0;
        int cells = step * step;
        for (int gy = 0; gy < gridHeight; gy++) {
            Arrays.fill(maximum, 0);
            Arrays.fill(sum, 0);
            for (int dy = 0; dy < step; dy++) {
                int index = (top + gy * step + dy) * rowStride + left * pixelStride;
                for (int gx = 0; gx < gridWidth; gx++) {
                    int blockMaximum = maximum[gx], blockSum = sum[gx];
                    for (int dx = 0; dx < step; dx++, index += pixelStride) {
                        int value = plane[index] & 255;
                        blockSum += value;
                        if (value > blockMaximum) blockMaximum = value;
                    }
                    maximum[gx] = blockMaximum;
                    sum[gx] = blockSum;
                }
            }
            for (int gx = 0; gx < gridWidth; gx++) {
                peak[gy * gridWidth + gx] = (byte) maximum[gx];
                mean[gy * gridWidth + gx] = (byte) (sum[gx] / cells);
                total += sum[gx];
            }
        }
        return new Grid(peak, mean, gridWidth, gridHeight,
                (int) (total / ((long) gridWidth * gridHeight * cells)));
    }

    public static int count(byte[] luminance, int width, int height) {
        return find(luminance, luminance, width, height).size();
    }

    public static List<Blob> find(Grid grid) {
        return grid == null ? new ArrayList<>() : find(grid.peak, grid.mean, grid.width, grid.height);
    }

    /** Finds compact bright regions in {@code peak} whose ring in {@code surround} stays dark. */
    public static List<Blob> find(byte[] peak, byte[] surround, int width, int height) {
        List<Blob> blobs = new ArrayList<>();
        if (width < 3 || height < 3 || peak == null || surround == null
                || peak.length < width * height || surround.length < width * height) return blobs;
        boolean[] seen = new boolean[width * height];
        int[] queue = new int[width * height];
        int maxArea = Math.max(4, Math.min(36, width * height / 120));
        for (int i = 0; i < width * height; i++) {
            if (seen[i] || (peak[i] & 255) < BRIGHT) continue;
            int read = 0, write = 0;
            queue[write++] = i;
            seen[i] = true;
            int minX = width, maxX = 0, minY = height, maxY = 0, brightest = 0;
            double weight = 0, weightedX = 0, weightedY = 0;
            boolean border = false;
            while (read < write) {
                int p = queue[read++], x = p % width, y = p / width, value = peak[p] & 255;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                brightest = Math.max(brightest, value);
                double w = value - BRIGHT + 1;
                weight += w; weightedX += w * x; weightedY += w * y;
                border |= x == 0 || y == 0 || x == width - 1 || y == height - 1;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int n = ny * width + nx;
                    if (!seen[n] && (peak[n] & 255) >= BRIGHT) {
                        seen[n] = true; queue[write++] = n;
                    }
                }
            }
            if (border || write > maxArea || maxX - minX > 10 || maxY - minY > 10) continue;
            int ringSum = 0, ringCount = 0;
            for (int y = Math.max(0, minY - 2); y <= Math.min(height - 1, maxY + 2); y++)
                for (int x = Math.max(0, minX - 2); x <= Math.min(width - 1, maxX + 2); x++) {
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY) continue;
                    ringSum += surround[y * width + x] & 255; ringCount++;
                }
            if (ringCount > 0 && ringSum / ringCount < DARK_SURROUND) {
                blobs.add(new Blob((float) (weightedX / weight), (float) (weightedY / weight), write, brightest));
            }
        }
        return blobs;
    }
}
