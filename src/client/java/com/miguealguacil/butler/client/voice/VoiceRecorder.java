package com.miguealguacil.butler.client.voice;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class VoiceRecorder {

    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);
    // 30s limit: 44100 samples/s * 2 bytes * 30s
    private static final int MAX_BYTES = 44100 * 2 * 30;

    private TargetDataLine line;
    private Thread captureThread;
    private ByteArrayOutputStream buffer;
    private Runnable onLimitReached;

    /** Starts recording. Throws LineUnavailableException if microphone is not available. */
    public void start(Runnable onLimitReached) throws LineUnavailableException {
        if (line != null && line.isOpen()) throw new IllegalStateException("Already recording");
        this.onLimitReached = onLimitReached;
        buffer = new ByteArrayOutputStream();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();
        captureThread = Thread.ofVirtual().start(this::captureLoop);
    }

    /** Stops recording and returns WAV bytes. Returns null if not recording. */
    public byte[] stop() {
        if (line == null) return null;
        line.stop();
        line.close();
        try { captureThread.join(2000); } catch (InterruptedException ignored) {}
        line = null;
        captureThread = null;
        byte[] pcm = buffer.toByteArray();
        buffer = null;
        return toWav(pcm);
    }

    public boolean isRecording() {
        return line != null && line.isOpen();
    }

    /** Returns the number of PCM bytes captured so far (used for min/max duration checks). */
    public int pcmBytesRecorded() {
        return buffer != null ? buffer.size() : 0;
    }

    private void captureLoop() {
        byte[] chunk = new byte[4096];
        while (line != null && line.isOpen()) {
            int read = line.read(chunk, 0, chunk.length);
            if (read > 0) {
                buffer.write(chunk, 0, read);
                if (buffer.size() >= MAX_BYTES) {
                    onLimitReached.run();
                    break;
                }
            }
        }
    }

    private static byte[] toWav(byte[] pcm) {
        try {
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
