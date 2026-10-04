package com.feloguarin.lensguard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.Arrays;
import java.util.Locale;

/** Optional microphone clue meter. Samples stay in RAM and are discarded on stop. */
public final class AudioProbe {
    public interface Listener { void onUpdate(String message); }

    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_SAMPLES = 4096;
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private Session active;
    private long generation;

    private static final class Session {
        volatile boolean cancelled;
        volatile boolean failed;
        long generation;
        AudioRecord recorder;
        Thread thread;
    }

    public AudioProbe(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            postIdle("Microphone permission is needed to inspect sound locally.");
            return;
        }
        Session session;
        synchronized (lock) {
            if (active != null) return;
            session = new Session();
            active = session;
            generation++;
            session.generation = generation;
            session.thread = new Thread(() -> capture(session), "LensGuard-audio");
            post(session, "Inspecting 15–22 kHz sound locally. Audio is never saved. Microphone filtering may hide tones.");
            session.thread.start();
        }
    }

    public boolean isRunning() {
        synchronized (lock) { return active != null && !active.cancelled && !active.failed; }
    }

    public void stop() {
        Session session;
        synchronized (lock) {
            session = active;
            active = null;
            generation++;
            if (session != null) {
                session.cancelled = true;
                if (session.recorder != null) safeStop(session.recorder);
                if (session.thread != null) session.thread.interrupt();
            }
        }
        // The worker uses non-blocking reads and releases the recorder in its finally block.
        postIdle("Microphone stopped. No audio was saved.");
    }

    private void capture(Session session) {
        AudioRecord recorder = null;
        short[] frame = new short[FRAME_SAMPLES];
        short[] incoming = new short[1024];
        try {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                fail(session, "Microphone permission is needed to inspect sound locally.");
                return;
            }
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            int minimumBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimumBytes <= 0) {
                fail(session, "48 kHz microphone capture is unavailable on this device.");
                return;
            }
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            boolean unprocessed = audioManager != null && "true".equalsIgnoreCase(
                    audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
            int source = unprocessed ? MediaRecorder.AudioSource.UNPROCESSED
                    : MediaRecorder.AudioSource.VOICE_RECOGNITION;
            recorder = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minimumBytes, FRAME_SAMPLES * 8));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                fail(session, "Microphone could not initialize. Check Android microphone access.");
                return;
            }
            synchronized (lock) {
                if (session.cancelled) return;
                session.recorder = recorder;
                recorder.startRecording();
            }
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                fail(session, "Microphone did not start. It may be in use by another app.");
                return;
            }
            int filled = 0, frameNumber = 0, sustained = 0;
            double previousFrequency = Double.NaN;
            long lastSamplesAt = android.os.SystemClock.elapsedRealtime();
            while (!session.cancelled) {
                int read = recorder.read(incoming, 0, incoming.length, AudioRecord.READ_NON_BLOCKING);
                if (read < 0) {
                    if (!session.cancelled) fail(session, "Microphone stream ended (Android error " + read + "). Restart to retry.");
                    break;
                }
                if (read == 0) {
                    if (android.os.SystemClock.elapsedRealtime() - lastSamplesAt > 3000) {
                        fail(session, "No microphone samples arrived. Check Android microphone access and restart.");
                        break;
                    }
                    Thread.sleep(8);
                    continue;
                }
                lastSamplesAt = android.os.SystemClock.elapsedRealtime();
                int offset = 0;
                while (offset < read) {
                    int copy = Math.min(read - offset, frame.length - filled);
                    System.arraycopy(incoming, offset, frame, filled, copy);
                    offset += copy;
                    filled += copy;
                    if (filled == frame.length) {
                        DetectionMath.ToneResult result = DetectionMath.highFrequencyTone(frame, SAMPLE_RATE);
                        if (result.prominent) {
                            sustained = Double.isFinite(previousFrequency)
                                    && Math.abs(result.frequencyHz - previousFrequency) < 200
                                    ? sustained + 1 : 1;
                            previousFrequency = result.frequencyHz;
                        } else {
                            sustained = 0;
                            previousFrequency = Double.NaN;
                        }
                        if (++frameNumber % 4 == 0) post(session, describe(result, sustained, unprocessed));
                        filled = 0;
                    }
                }
            }
        } catch (SecurityException exception) {
            fail(session, "Android denied microphone access. Grant microphone permission, then restart.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!session.cancelled) fail(session, "Microphone capture was interrupted. Restart to retry.");
        } catch (RuntimeException exception) {
            fail(session, "Microphone capture is unavailable. Check Android microphone access and restart.");
        } finally {
            Arrays.fill(frame, (short) 0);
            Arrays.fill(incoming, (short) 0);
            synchronized (lock) {
                session.recorder = null;
                if (recorder != null) {
                    safeStop(recorder);
                    recorder.release();
                }
                if (active == session) active = null;
            }
        }
    }

    private static String describe(DetectionMath.ToneResult result, int sustained, boolean unprocessed) {
        String status;
        if (result.clipped) status = "Sound is clipping; move away from loud sources.";
        else if (result.rmsDbfs < -85) status = "Very little sound received. The room may be quiet or Android may be silencing the microphone.";
        else if (sustained >= 3) status = String.format(Locale.US,
                "Persistent high-frequency tone near %.1f kHz. Investigate the sound source.", result.frequencyHz / 1000);
        else status = "No persistent prominent tone in the sampled high-frequency band.";
        return status + String.format(Locale.US,
                "\nLevel %.0f dBFS • band peak %.1f kHz • %.0f dB above band floor",
                result.rmsDbfs, result.frequencyHz / 1000, result.peakToFloorDb)
                + "\nChargers, displays and other electronics can make tones. Sound cannot identify a camera."
                + (unprocessed ? "" : " Microphone processing can suppress high frequencies.");
    }

    private static void safeStop(AudioRecord recorder) {
        try {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
        } catch (IllegalStateException ignored) { /* Already stopped or initialization failed. */ }
    }

    private void post(Session session, String message) {
        main.post(() -> {
            synchronized (lock) {
                if (session.cancelled || generation != session.generation) return;
            }
            listener.onUpdate(message);
        });
    }

    private void fail(Session session, String message) {
        session.failed = true;
        post(session, message);
    }

    private void postIdle(String message) {
        long expectedGeneration;
        synchronized (lock) { expectedGeneration = generation; }
        main.post(() -> {
            synchronized (lock) {
                if (active != null || generation != expectedGeneration) return;
            }
            listener.onUpdate(message);
        });
    }
}
