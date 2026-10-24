package jp.touringhub.assistant;

/** Separates a temporarily unselected route from removal of the chosen device. */
final class CommunicationRouteGuard implements AutoCloseable {
    interface Backend {
        boolean targetAvailable();
        boolean targetSelected();
        boolean restoreTarget();
        void scheduleCheck(Runnable check);
        void cancelCheck(Runnable check);
    }
    private final Backend backend;
    private final Runnable disconnected;
    private boolean recovering;
    private boolean closed;
    private Runnable deadline;

    CommunicationRouteGuard(Backend backend, Runnable disconnected) {
        this.backend = backend;
        this.disconnected = disconnected;
    }

    void changed(boolean targetSelected) {
        if (closed) return;
        if (targetSelected) {
            recovering = false;
            if (deadline != null) backend.cancelCheck(deadline);
            return;
        }
        if (!backend.targetAvailable()) { removed(); return; }
        if (recovering) return;
        recovering = true;
        deadline = () -> {
            if (closed || !recovering) return;
            if (backend.targetAvailable() && backend.targetSelected()) changed(true);
            else removed();
        };
        backend.scheduleCheck(deadline);
        // Keep the recovery bounded and set its state before the API can call back.
        if (!backend.restoreTarget()) removed();
    }

    void removed() {
        if (closed) return;
        close();
        disconnected.run();
    }

    @Override public void close() {
        closed = true;
        if (deadline != null) backend.cancelCheck(deadline);
    }
}
