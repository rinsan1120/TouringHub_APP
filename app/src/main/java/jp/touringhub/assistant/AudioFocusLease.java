package jp.touringhub.assistant;

/** A focus lease for our playback, not for the external recognition service's recording. */
final class AudioFocusLease implements AutoCloseable {
    interface Backend {
        boolean request(Runnable onLoss);
        void abandon();
    }
    private final Backend backend;
    private final Runnable interrupted;
    private boolean held;
    private boolean closed;
    private int generation;

    AudioFocusLease(Backend backend, Runnable interrupted) {
        this.backend = backend;
        this.interrupted = interrupted;
    }

    boolean acquire() {
        if (closed) return false;
        if (held) return true;
        int request = ++generation;
        held = true;
        boolean granted = backend.request(() -> {
            if (!closed && held && request == generation) interrupted.run();
        });
        if (!granted) release();
        return granted && held && !closed;
    }

    void release() {
        // Invalidate first: even already queued loss callbacks must not cancel a later turn.
        ++generation;
        boolean wasHeld = held;
        held = false;
        if (wasHeld) backend.abandon();
    }

    @Override public void close() {
        closed = true;
        release();
    }
}
