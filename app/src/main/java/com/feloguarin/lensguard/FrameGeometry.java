package com.feloguarin.lensguard;

/**
 * Maps analysis-buffer coordinates to what the preview shows. All points are normalised to 0–1 so
 * the mapping can be checked without a camera.
 */
public final class FrameGeometry {
    private FrameGeometry() {}

    /**
     * Rotates a normalised buffer point clockwise by CameraX's rotation degrees, then mirrors it
     * horizontally when the preview mirrors the front camera.
     */
    public static float[] upright(float u, float v, int rotationDegrees, boolean mirrored) {
        float x, y;
        switch (((rotationDegrees % 360) + 360) % 360) {
            case 90: x = 1 - v; y = u; break;
            case 180: x = 1 - u; y = 1 - v; break;
            case 270: x = v; y = 1 - u; break;
            default: x = u; y = v;
        }
        return new float[] {mirrored ? 1 - x : x, y};
    }

    /** Width/height of a buffer region after it is rotated upright. */
    public static float uprightAspect(int width, int height, int rotationDegrees) {
        if (width <= 0 || height <= 0) return 1;
        boolean quarterTurn = (((rotationDegrees % 360) + 360) % 360) % 180 == 90;
        return quarterTurn ? (float) height / width : (float) width / height;
    }

    /**
     * Places an upright frame point in a view that centre-crops the frame to fill itself.
     * Returns null when the point is cropped out of the visible area.
     */
    public static float[] fillCenter(float x, float y, float frameAspect, float viewAspect) {
        if (!(frameAspect > 0) || !(viewAspect > 0)) return null;
        float viewX = x, viewY = y;
        if (frameAspect > viewAspect) {
            float visible = viewAspect / frameAspect;
            viewX = (x - (1 - visible) / 2) / visible;
        } else if (frameAspect < viewAspect) {
            float visible = frameAspect / viewAspect;
            viewY = (y - (1 - visible) / 2) / visible;
        }
        if (viewX < 0 || viewX > 1 || viewY < 0 || viewY > 1) return null;
        return new float[] {viewX, viewY};
    }
}
