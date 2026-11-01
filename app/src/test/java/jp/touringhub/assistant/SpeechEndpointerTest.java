package jp.touringhub.assistant;

import org.junit.Test;
import static org.junit.Assert.*;
import jp.touringhub.assistant.SpeechEndpointer.Event;

public class SpeechEndpointerTest {
    private static final double QUIET = 0.0;
    private static final double LOUD = 0.2;

    @Test public void silenceOnlyGivesUpAtStartTimeout() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        assertEquals(Event.NONE, endpointer.accept(QUIET, 1000));
        assertEquals(Event.NONE, endpointer.accept(QUIET, 6999));
        assertEquals(Event.NO_SPEECH, endpointer.accept(QUIET, 7000));
    }

    @Test public void speechThenTrailingSilenceCompletes() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        assertEquals(Event.NONE, endpointer.accept(QUIET, 100));
        assertEquals(Event.SPEECH_STARTED, endpointer.accept(LOUD, 200));
        assertEquals(Event.NONE, endpointer.accept(LOUD, 500));
        assertEquals(Event.NONE, endpointer.accept(QUIET, 1399));
        assertEquals(Event.COMPLETE, endpointer.accept(QUIET, 1400));
    }

    @Test public void shortQuietGapInsideSpeechDoesNotComplete() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        assertEquals(Event.SPEECH_STARTED, endpointer.accept(LOUD, 200));
        assertEquals(Event.NONE, endpointer.accept(QUIET, 600));
        assertEquals(Event.NONE, endpointer.accept(LOUD, 1000));
        assertEquals(Event.NONE, endpointer.accept(QUIET, 1899));
        assertEquals(Event.COMPLETE, endpointer.accept(QUIET, 1900));
    }

    @Test public void continuousSpeechIsCapped() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        assertEquals(Event.SPEECH_STARTED, endpointer.accept(LOUD, 100));
        for (long t = 1000; t < 20000; t += 1000) {
            assertEquals(Event.NONE, endpointer.accept(LOUD, t));
        }
        assertEquals(Event.COMPLETE, endpointer.accept(LOUD, 20000));
    }

    @Test public void noSpeechByTheCapIsReportedAsNoSpeech() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        assertEquals(Event.NO_SPEECH, endpointer.accept(QUIET, 20000));
    }

    @Test public void speechStartingAtTheTimeoutStillCounts() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        assertEquals(Event.NONE, endpointer.accept(QUIET, 6999));
        assertEquals(Event.SPEECH_STARTED, endpointer.accept(LOUD, 7000));
    }

    @Test public void eventsStopAfterFinishing() {
        SpeechEndpointer endpointer = new SpeechEndpointer();
        endpointer.start(0);
        endpointer.accept(LOUD, 100);
        assertEquals(Event.COMPLETE, endpointer.accept(QUIET, 1000));
        assertEquals(Event.NONE, endpointer.accept(LOUD, 1100));
        assertEquals(Event.NONE, endpointer.accept(QUIET, 30000));
    }
}
