package com.feloguarin.lensguard;

/** Small isolated highlights only. This heuristic never classifies a camera. */
public final class GlintDetector {
    private GlintDetector() {}
    public static int count(byte[] luminance, int width, int height) {
        if (width < 3 || height < 3 || luminance == null || luminance.length < width * height) return 0;
        boolean[] seen = new boolean[width * height];
        int[] queue = new int[width * height];
        int count = 0;
        int maxArea = Math.max(4, Math.min(36, width * height / 120));
        for (int i = 0; i < width * height; i++) {
            if (seen[i] || (luminance[i] & 255) < 238) continue;
            int read = 0, write = 0;
            queue[write++] = i;
            seen[i] = true;
            int minX = width, maxX = 0, minY = height, maxY = 0;
            boolean border = false;
            while (read < write) {
                int p = queue[read++], x = p % width, y = p / width;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                border |= x == 0 || y == 0 || x == width - 1 || y == height - 1;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int n = ny * width + nx;
                    if (!seen[n] && (luminance[n] & 255) >= 238) {
                        seen[n] = true; queue[write++] = n;
                    }
                }
            }
            if (border || write > maxArea || maxX - minX > 10 || maxY - minY > 10) continue;
            int ringSum = 0, ringCount = 0;
            for (int y = Math.max(0, minY - 2); y <= Math.min(height - 1, maxY + 2); y++)
                for (int x = Math.max(0, minX - 2); x <= Math.min(width - 1, maxX + 2); x++) {
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY) continue;
                    ringSum += luminance[y * width + x] & 255; ringCount++;
                }
            if (ringCount > 0 && ringSum / ringCount < 170) count++;
        }
        return count;
    }
}
