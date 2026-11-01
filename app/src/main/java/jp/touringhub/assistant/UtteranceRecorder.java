package jp.touringhub.assistant;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Records one spoken turn and cuts it at trailing silence.
 * The input device is pinned explicitly (the Bluetooth communication device chosen by
 * AudioRouter), so capture never silently falls back to the handset microphone.
 * All callbacks are delivered on the supplied handler; each instance is single-use.
 * RECORD_AUDIO is enforced by MainActivity before any session can start.
 */
@android.annotation.SuppressLint("MissingPermission")
public final class UtteranceRecorder {
    public interface Callback {
        /** The microphone is open and recording. */
        void started();
        /** Speech was captured: 16 kHz mono samples in [-1, 1]. */
        void captured(float[] samples);
        /** Nothing was said before the start timeout. */
        void noSpeech();
        /** Recording could not run. */
        void failed(String message);
    }

    public static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_SAMPLES = 512;
    private static final int PRE_ROLL_CHUNKS = 10; // about 320 ms before detected speech

    private final Handler handler;
    private final AudioDeviceInfo preferred;
    private volatile boolean cancelled;
    private volatile AudioRecord record;
    private Thread thread;

    public UtteranceRecorder(Handler handler, AudioDeviceInfo preferred) {
        this.handler = handler;
        this.preferred = preferred;
    }

    public void start(Callback callback) {
        cancelled = false;
        thread = new Thread(() -> run(callback), "utterance-recorder");
        thread.start();
    }

    /** Stops recording without delivering any further callback. */
    public void cancel() {
        cancelled = true;
        AudioRecord current = record;
        if (current != null) {
            try { current.stop(); } catch (IllegalStateException ignored) {}
        }
    }

    private void run(Callback callback) {
        AudioRecord created = null;
        try {
            int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) {
                deliver(callback::failed, "マイクを初期化できませんでした。");
                return;
            }
            AudioRecord.Builder builder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minBuffer, CHUNK_SAMPLES * 2 * 4));
            created = builder.build();
            record = created;
            if (created.getState() != AudioRecord.STATE_INITIALIZED) {
                deliver(callback::failed, "マイクを初期化できませんでした。接続を確認してください。");
                return;
            }
            // Pin the input to the router's communication device on top of the system
            // communication route, so capture cannot drift to the handset microphone.
            if (preferred != null) created.setPreferredDevice(preferred);
            created.startRecording();
            if (created.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                deliver(callback::failed, "録音を開始できませんでした。");
                return;
            }
            if (!cancelled) handler.post(callback::started);
            recordLoop(created, callback);
        } catch (SecurityException ex) {
            deliver(callback::failed, "マイクの権限を許可してください。");
        } catch (RuntimeException ex) {
            deliver(callback::failed, "録音を開始できませんでした。");
        } finally {
            record = null;
            if (created != null) {
                try { created.stop(); } catch (IllegalStateException ignored) {}
                created.release();
            }
        }
    }

    private void recordLoop(AudioRecord audioRecord, Callback callback) {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(SystemClock.elapsedRealtime());
        short[] buffer = new short[CHUNK_SAMPLES];
        ArrayDeque<float[]> preRoll = new ArrayDeque<>();
        List<float[]> chunks = new ArrayList<>();
        boolean speaking = false;
        while (!cancelled) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                deliver(callback::failed, "マイクとの接続が切れました。");
                return;
            }
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    deliver(callback::failed, "録音が中断されました。");
                    return;
                }
                continue;
            }
            double squares = 0;
            for (int i = 0; i < read; i++) {
                double value = buffer[i] / 32768.0;
                squares += value * value;
            }
            SpeechEndpointer.Event event =
                    endpointer.accept(Math.sqrt(squares / read), SystemClock.elapsedRealtime());
            float[] samples = new float[read];
            for (int i = 0; i < read; i++) samples[i] = buffer[i] / 32768f;
            if (!speaking) {
                preRoll.addLast(samples);
                while (preRoll.size() > PRE_ROLL_CHUNKS) preRoll.removeFirst();
                if (event == SpeechEndpointer.Event.SPEECH_STARTED) {
                    speaking = true;
                    chunks.addAll(preRoll);
                    preRoll.clear();
                }
            } else {
                chunks.add(samples);
            }
            if (event == SpeechEndpointer.Event.COMPLETE) {
                float[] all = assemble(chunks);
                if (!cancelled) handler.post(() -> callback.captured(all));
                return;
            }
            if (event == SpeechEndpointer.Event.NO_SPEECH) {
                if (!cancelled) handler.post(callback::noSpeech);
                return;
            }
        }
    }

    private static float[] assemble(List<float[]> chunks) {
        int total = 0;
        for (float[] chunk : chunks) total += chunk.length;
        float[] all = new float[total];
        int offset = 0;
        for (float[] chunk : chunks) {
            System.arraycopy(chunk, 0, all, offset, chunk.length);
            offset += chunk.length;
        }
        return all;
    }

    private void deliver(java.util.function.Consumer<String> message, String text) {
        if (!cancelled) handler.post(() -> message.accept(text));
    }
}
