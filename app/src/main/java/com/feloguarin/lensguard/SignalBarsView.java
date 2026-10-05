package com.feloguarin.lensguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Four bars for received signal strength. Bars show strength, never distance or identity. */
final class SignalBarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int level;

    SignalBarsView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
    }

    /** 0–4 bars from dBm; thresholds follow common Android signal-bar conventions. */
    static int level(int rssi) {
        if (rssi == WirelessProbe.NO_RSSI) return 0;
        if (rssi >= -55) return 4;
        if (rssi >= -67) return 3;
        if (rssi >= -78) return 2;
        if (rssi >= -90) return 1;
        return 0;
    }

    void setRssi(int rssi) {
        level = level(rssi);
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(22 * density), Math.round(16 * density));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float barWidth = 3.5f * density, gap = 2 * density, height = getHeight();
        for (int i = 0; i < 4; i++) {
            float barHeight = height * (i + 1) / 4f;
            float left = i * (barWidth + gap);
            paint.setColor(i < level ? Ui.MINT : Ui.RAISED);
            canvas.drawRoundRect(left, height - barHeight, left + barWidth, height, density, density, paint);
        }
    }
}
