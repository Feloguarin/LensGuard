package com.feloguarin.lensguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Draws rings over the camera preview where small highlights are. Rings mark places to inspect. */
final class HighlightOverlay extends View {
    static final int NEW = 0, STEADY = 1, REFLECTION = 2, SOURCE = 3;

    private static final class Marker {
        final float x, y;
        final int style;

        Marker(float x, float y, int style) {
            this.x = x;
            this.y = y;
            this.style = style;
        }
    }

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private List<Marker> live = new ArrayList<>();
    private List<Marker> findings = new ArrayList<>();
    private float liveAspect;
    private float findingsAspect;

    HighlightOverlay(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        dot.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static int color(int style) {
        switch (style) {
            case STEADY:
            case REFLECTION: return Ui.MINT;
            case SOURCE: return Ui.AMBER;
            default: return Color.argb(190, 255, 255, 255);
        }
    }

    void showTracks(List<HighlightTracker.Track> tracks, float aspect) {
        List<Marker> markers = new ArrayList<>();
        for (HighlightTracker.Track track : tracks) markers.add(new Marker(track.x, track.y, track.steady ? STEADY : NEW));
        live = markers;
        liveAspect = aspect;
        invalidate();
    }

    void showFindings(List<LightComparison.Finding> results, float aspect) {
        List<Marker> markers = new ArrayList<>();
        for (LightComparison.Finding finding : results) {
            markers.add(new Marker(finding.x, finding.y,
                    finding.kind == LightComparison.Kind.REFLECTS_PHONE_LIGHT ? REFLECTION : SOURCE));
        }
        findings = markers;
        findingsAspect = aspect;
        invalidate();
    }

    boolean hasFindings() { return !findings.isEmpty(); }

    void clearFindings() {
        findings = new ArrayList<>();
        invalidate();
    }

    void clear() {
        live = new ArrayList<>();
        findings = new ArrayList<>();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        float viewAspect = (float) getWidth() / getHeight();
        for (Marker marker : live) draw(canvas, marker, liveAspect, viewAspect);
        for (Marker marker : findings) draw(canvas, marker, findingsAspect, viewAspect);
    }

    private void draw(Canvas canvas, Marker marker, float frameAspect, float viewAspect) {
        float[] point = FrameGeometry.fillCenter(marker.x, marker.y, frameAspect, viewAspect);
        if (point == null) return;
        float x = point[0] * getWidth(), y = point[1] * getHeight();
        int color = color(marker.style);
        ring.setColor(color);
        dot.setColor(color);
        switch (marker.style) {
            case STEADY:
                ring.setStrokeWidth(3 * density);
                canvas.drawCircle(x, y, 16 * density, ring);
                break;
            case REFLECTION:
                ring.setStrokeWidth(3 * density);
                canvas.drawCircle(x, y, 20 * density, ring);
                canvas.drawCircle(x, y, 4 * density, dot);
                break;
            case SOURCE:
                ring.setStrokeWidth(3 * density);
                float half = 17 * density;
                canvas.drawRect(x - half, y - half, x + half, y + half, ring);
                break;
            default:
                ring.setStrokeWidth(1.5f * density);
                canvas.drawCircle(x, y, 12 * density, ring);
        }
    }
}
