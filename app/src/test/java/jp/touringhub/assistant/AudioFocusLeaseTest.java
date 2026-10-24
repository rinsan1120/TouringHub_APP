package jp.touringhub.assistant;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class AudioFocusLeaseTest {
    private static final class Fake implements AudioFocusLease.Backend {
        final List<Runnable> losses = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        boolean granted = true;
        int interrupted;
        @Override public boolean request(Runnable onLoss) {
            events.add("acquire"); losses.add(onLoss); return granted;
        }
        @Override public void abandon() { events.add("abandon"); }
        AudioFocusLease lease() { return new AudioFocusLease(this, () -> interrupted++); }
    }
    @Test public void recognizerFocusRequestAfterHandoffDoesNotEndConversation() {
        Fake audio = new Fake(); AudioFocusLease lease = audio.lease();
        assertTrue(lease.acquire()); // prompt playback
        lease.release(); // before starting the remote recognizer
        audio.events.add("startListening");
        audio.losses.get(0).run(); // queued transient loss from recognition service
        assertEquals(List.of("acquire", "abandon", "startListening"), audio.events);
        assertEquals(0, audio.interrupted);
    }
    @Test public void nextSpokenTurnReacquiresFocusAndStillHonorsInterruption() {
        Fake audio = new Fake(); AudioFocusLease lease = audio.lease();
        lease.acquire(); lease.release(); assertTrue(lease.acquire());
        audio.losses.get(0).run(); // delayed callback for the previous prompt
        assertEquals(0, audio.interrupted);
        audio.losses.get(1).run(); // actual focus loss during the new prompt
        assertEquals(1, audio.interrupted);
    }
    @Test public void genuineLossDuringPlaybackStillStopsConversation() {
        Fake audio = new Fake(); AudioFocusLease lease = audio.lease();
        lease.acquire(); audio.losses.get(0).run(); assertEquals(1, audio.interrupted);
    }
    @Test public void deniedFocusCannotStartPlayback() {
        Fake audio = new Fake(); audio.granted = false;
        assertFalse(audio.lease().acquire());
        assertEquals(List.of("acquire", "abandon"), audio.events);
    }
    @Test public void shutdownAbandonsOnceAndRejectsLateEvents() {
        Fake audio = new Fake(); AudioFocusLease lease = audio.lease();
        lease.acquire(); lease.close(); lease.close(); audio.losses.get(0).run();
        assertFalse(lease.acquire()); assertEquals(0, audio.interrupted);
        assertEquals(List.of("acquire", "abandon"), audio.events);
    }
    @Test public void repeatedPlaybackDoesNotDuplicateFocusRequests() {
        Fake audio = new Fake(); AudioFocusLease lease = audio.lease();
        assertTrue(lease.acquire()); assertTrue(lease.acquire());
        assertEquals(List.of("acquire"), audio.events);
    }
    @Test public void synchronousInterruptionCanCloseLeaseDuringRequest() {
        final AudioFocusLease[] lease = new AudioFocusLease[1];
        lease[0] = new AudioFocusLease(new AudioFocusLease.Backend() {
            @Override public boolean request(Runnable onLoss) { onLoss.run(); return true; }
            @Override public void abandon() {}
        }, () -> lease[0].close());
        assertFalse(lease[0].acquire());
    }
    @Test public void telephoneModesStopDuringRecognitionButOurCommunicationModeDoesNot() {
        assertTrue(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_RINGTONE));
        assertTrue(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_IN_CALL));
        assertTrue(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_CALL_SCREENING));
        assertTrue(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_CALL_REDIRECT));
        assertTrue(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_COMMUNICATION_REDIRECT));
        assertFalse(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_NORMAL));
        assertFalse(AudioRouter.isTelephoneMode(android.media.AudioManager.MODE_IN_COMMUNICATION));
    }
    @Test public void handsetStopsForVoipWhileBluetoothOwnCommunicationModeIsAllowed() {
        assertTrue(AudioRouter.isInterruptingMode(android.media.AudioManager.MODE_IN_COMMUNICATION, false));
        assertFalse(AudioRouter.isInterruptingMode(android.media.AudioManager.MODE_IN_COMMUNICATION, true));
        assertTrue(AudioRouter.isInterruptingMode(android.media.AudioManager.MODE_RINGTONE, false));
        assertTrue(AudioRouter.isInterruptingMode(android.media.AudioManager.MODE_RINGTONE, true));
        assertFalse(AudioRouter.isInterruptingMode(android.media.AudioManager.MODE_NORMAL, false));
    }
}
