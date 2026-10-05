package com.feloguarin.lensguard;

import android.os.Handler;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One light on/off comparison, run as a timed sequence on the main thread: light on, let exposure
 * settle, collect frames, light off, settle, collect, then restore the user's light setting.
 */
final class ComparisonRun {
    interface Torch { void set(boolean on); }

    interface Listener {
        void onProgress(int step, int steps);
        void onDone(LightComparison.Result result);
        void onTimeout();
    }

    /** Torch ramp-up and auto-exposure need time before frames reflect the new lighting. */
    static final long SETTLE_MS = 900;
    static final long TIMEOUT_MS = 10_000;
    private static final int SETTLE_ON = 0, COLLECT_ON = 1, SETTLE_OFF = 2, COLLECT_OFF = 3, ENDED = 4;

    private final Handler handler;
    private final Torch torch;
    private final BooleanSupplier moving;
    private final Listener listener;
    private final boolean restoreTorch;
    private final LightComparison data;
    private int phase = SETTLE_ON;
    private boolean moved;

    private final Runnable advance = this::settled;
    private final Runnable timeout = this::timedOut;

    ComparisonRun(Handler handler, Torch torch, BooleanSupplier moving, Listener listener, boolean restoreTorch,
                  float aspect) {
        this.handler = handler;
        this.torch = torch;
        this.moving = moving;
        this.listener = listener;
        this.restoreTorch = restoreTorch;
        this.data = new LightComparison(aspect);
    }

    static int steps() {
        return LightComparison.FRAMES_PER_PHASE * 2;
    }

    void start() {
        torch.set(true);
        handler.postDelayed(advance, SETTLE_MS);
        handler.postDelayed(timeout, TIMEOUT_MS);
        listener.onProgress(0, steps());
    }

    boolean active() {
        return phase != ENDED;
    }

    /** Feeds one analysed frame's highlight positions. Frames during settling are ignored. */
    void offer(List<float[]> points) {
        if (phase != COLLECT_ON && phase != COLLECT_OFF) return;
        if (moving.getAsBoolean()) moved = true;
        if (phase == COLLECT_ON) {
            data.addLightOn(points);
            listener.onProgress(data.lightOnFrames(), steps());
            if (data.lightOnFrames() >= LightComparison.FRAMES_PER_PHASE) {
                phase = SETTLE_OFF;
                torch.set(false);
                handler.postDelayed(advance, SETTLE_MS);
            }
        } else {
            data.addLightOff(points);
            listener.onProgress(LightComparison.FRAMES_PER_PHASE + data.lightOffFrames(), steps());
            if (data.lightOffFrames() >= LightComparison.FRAMES_PER_PHASE) {
                LightComparison.Result result = data.result(moved);
                end();
                listener.onDone(result);
            }
        }
    }

    private void settled() {
        if (phase == SETTLE_ON) phase = COLLECT_ON;
        else if (phase == SETTLE_OFF) phase = COLLECT_OFF;
    }

    private void timedOut() {
        if (phase == ENDED) return;
        end();
        listener.onTimeout();
    }

    /** Stops without a result and restores the user's light setting. */
    void cancel() {
        if (phase != ENDED) end();
    }

    private void end() {
        phase = ENDED;
        handler.removeCallbacks(advance);
        handler.removeCallbacks(timeout);
        torch.set(restoreTorch);
    }
}
