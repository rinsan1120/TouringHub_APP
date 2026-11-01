package jp.touringhub.assistant;

/**
 * Decides when one spoken turn is over, from per-chunk loudness only.
 * Pure and clock-injected so the timing rules can be unit-tested without audio hardware.
 */
final class SpeechEndpointer {
    enum Event { NONE, SPEECH_STARTED, COMPLETE, NO_SPEECH }

    /** Chunk RMS (0..1) at or above this counts as speech. Close-talk headset mics sit well above it. */
    static final double SPEECH_RMS = 0.03;
    /** Give up when nothing was said within this time after listening started. */
    static final long START_TIMEOUT_MS = 7000;
    /** Trailing silence after speech that ends the turn. */
    static final long END_SILENCE_MS = 900;
    /** Hard cap for one turn, matching the session's listening watchdog. */
    static final long MAX_UTTERANCE_MS = 20000;

    private long startedAt = -1;
    private long speechAt = -1;
    private long lastLoudAt = -1;
    private boolean finished;

    void start(long nowMs) {
        startedAt = nowMs;
        speechAt = -1;
        lastLoudAt = -1;
        finished = false;
    }

    Event accept(double rms, long nowMs) {
        if (finished || startedAt < 0) return Event.NONE;
        if (nowMs - startedAt >= MAX_UTTERANCE_MS) {
            finished = true;
            return speechAt >= 0 ? Event.COMPLETE : Event.NO_SPEECH;
        }
        boolean loud = rms >= SPEECH_RMS;
        if (speechAt < 0) {
            if (loud) {
                speechAt = nowMs;
                lastLoudAt = nowMs;
                return Event.SPEECH_STARTED;
            }
            if (nowMs - startedAt >= START_TIMEOUT_MS) {
                finished = true;
                return Event.NO_SPEECH;
            }
            return Event.NONE;
        }
        if (loud) lastLoudAt = nowMs;
        if (nowMs - lastLoudAt >= END_SILENCE_MS) {
            finished = true;
            return Event.COMPLETE;
        }
        return Event.NONE;
    }
}
