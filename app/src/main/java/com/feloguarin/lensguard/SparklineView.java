package com.feloguarin.lensguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A minimal line chart of recent readings, with an optional reference line such as a baseline. */
final class SparklineView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reference = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] values = new float[0];
    private float referenceValue = Float.NaN;

    SparklineView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * density);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(Ui.MINT);
        reference.setStyle(Paint.Style.STROKE);
        reference.setStrokeWidth(1.5f * density);
        reference.setColor(Ui.AMBER);
        reference.setPathEffect(new DashPathEffect(new float[] {6 * density, 5 * density}, 0));
        frame.setStyle(Paint.Style.FILL);
        frame.setColor(Ui.RAISED);
    }

    /** @param description spoken summary, because screen readers cannot read the line itself */
    void setValues(float[] data, float referenceLine, CharSequence description) {
        values = data == null ? new float[0] : data;
        referenceValue = referenceLine;
        setContentDescription(description);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        canvas.drawRoundRect(0, 0, width, height, height / 10, height / 10, frame);
        if (values.length < 2) return;
        float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            if (!Float.isFinite(value)) continue;
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        if (Float.isFinite(referenceValue)) {
            low = Math.min(low, referenceValue);
            high = Math.max(high, referenceValue);
        }
        if (!Float.isFinite(low) || !Float.isFinite(high)) return;
        float span = Math.max(high - low, 1f);
        low -= span * 0.12f;
        high += span * 0.12f;
        float pad = height * 0.08f;
        if (Float.isFinite(referenceValue)) {
            float y = y(referenceValue, low, high, height, pad);
            canvas.drawLine(pad, y, width - pad, y, reference);
        }
        path.reset();
        boolean started = false;
        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) continue;
            float x = pad + (width - 2 * pad) * i / (values.length - 1);
            float y = y(values[i], low, high, height, pad);
            if (started) path.lineTo(x, y);
            else { path.moveTo(x, y); started = true; }
        }
        canvas.drawPath(path, line);
    }

    private static float y(float value, float low, float high, float height, float pad) {
        return pad + (height - 2 * pad) * (1 - (value - low) / (high - low));
    }
}
