package jp.touringhub.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommunicationRouteGuardTest {
    private static final class Fake implements CommunicationRouteGuard.Backend {
        boolean available = true, selected, restoreSucceeds = true;
        int restores, failures;
        Runnable deadline;
        @Override public boolean targetAvailable() { return available; }
        @Override public boolean targetSelected() { return selected; }
        @Override public boolean restoreTarget() { restores++; return restoreSucceeds; }
        @Override public void scheduleCheck(Runnable check) { deadline = check; }
        @Override public void cancelCheck(Runnable check) { if (deadline == check) deadline = null; }
        CommunicationRouteGuard guard() { return new CommunicationRouteGuard(this, () -> failures++); }
    }
    @Test public void temporarySpeakerDeselectionDuringReadbackRecovers() {
        Fake audio = new Fake(); CommunicationRouteGuard guard = audio.guard();
        guard.changed(false);
        assertEquals(1, audio.restores); assertEquals(0, audio.failures);
        audio.selected = true; guard.changed(true);
        assertNull(audio.deadline); assertEquals(0, audio.failures);
    }
    @Test public void repeatedRouteNotificationsDoNotCreateRestoreLoop() {
        Fake audio = new Fake(); CommunicationRouteGuard guard = audio.guard();
        guard.changed(false); guard.changed(false); guard.changed(false);
        assertEquals(1, audio.restores);
        audio.deadline.run(); assertEquals(1, audio.failures);
    }
    @Test public void actualBluetoothRemovalStopsImmediatelyWithoutFallback() {
        Fake audio = new Fake(); audio.available = false;
        audio.guard().changed(false);
        assertEquals(0, audio.restores); assertEquals(1, audio.failures);
    }
    @Test public void physicalRemovalDuringRecoveryStillStopsOnce() {
        Fake audio = new Fake(); CommunicationRouteGuard guard = audio.guard();
        guard.changed(false); Runnable oldCheck = audio.deadline;
        guard.removed(); guard.removed(); oldCheck.run();
        assertEquals(1, audio.failures); assertNull(audio.deadline);
    }
    @Test public void rejectedRestoreOrCallInterruptionCannotContinue() {
        Fake audio = new Fake(); audio.restoreSucceeds = false;
        audio.guard().changed(false);
        assertEquals(1, audio.failures); assertNull(audio.deadline);
    }
    @Test public void delayedCallbackIsCheckedAgainstActualRouteAtDeadline() {
        Fake audio = new Fake(); audio.guard().changed(false);
        audio.selected = true; audio.deadline.run();
        assertEquals(0, audio.failures); assertNull(audio.deadline);
    }
    @Test public void teardownDoesNotRecoverOrReportLateRouteEvents() {
        Fake audio = new Fake(); CommunicationRouteGuard guard = audio.guard();
        guard.changed(false); Runnable oldCheck = audio.deadline; guard.close();
        oldCheck.run(); guard.changed(false); guard.removed();
        assertEquals(1, audio.restores); assertEquals(0, audio.failures);
    }
}
