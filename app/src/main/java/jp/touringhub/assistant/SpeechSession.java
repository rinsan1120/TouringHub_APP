package jp.touringhub.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

/** Alternates TTS and one-shot recognition; never listens while speaking. */
public final class SpeechSession implements AutoCloseable {
    public interface Events {
        void ready();
        void listening();
        void heard(String text);
        void missed();
        void fatal(String message);
    }
    private final Handler handler;
    private final Context context;
    private final Events events;
    private final AudioRouter router;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean closed;
    private int utterance;
    private Runnable afterSpeech;
    private Runnable speechTimeout;
    private Runnable listenTimeout;
    private boolean listening;
    public SpeechSession(Context context, Handler handler, AudioRouter router, Events events) {
        this.context = context;
        this.handler = handler;
        this.events = events;
        this.router = router;
    }
    public void initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            events.fatal("音声認識サービスがありません。Googleアプリと日本語の音声認識を準備してください。"); return;
        }
        tts = new TextToSpeech(context, status -> handler.post(() -> {
            if (closed) return;
            if (status != TextToSpeech.SUCCESS) {
                events.fatal("読み上げを初期化できません。端末設定で日本語の音声合成を準備してください。"); return;
            }
            int language = tts.setLanguage(Locale.JAPAN);
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                events.fatal("日本語の読み上げデータがありません。端末の音声合成設定を確認してください。"); return;
            }
            tts.setSpeechRate(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { handler.post(() -> speechDone(id)); }
                @Override public void onError(String id) { handler.post(() -> {
                    if (!closed && id.equals(Integer.toString(utterance))) events.fatal("読み上げに失敗しました。目的地は確定していません。");
                }); }
            });
            events.ready();
        }));
    }
    public void speak(String text, Runnable next) {
        if (closed) return;
        destroyRecognizer();
        if (!router.prepareSpeech() || closed) return;
        tts.setAudioAttributes(router.speechAttributes());
        if (speechTimeout != null) handler.removeCallbacks(speechTimeout);
        afterSpeech = next;
        String id = Integer.toString(++utterance);
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
            events.fatal("読み上げを開始できませんでした。"); return;
        }
        speechTimeout = () -> { if (!closed) events.fatal("読み上げが時間内に完了しませんでした。"); };
        handler.postDelayed(speechTimeout, 90000);
    }
    private void speechDone(String id) {
        if (closed || !id.equals(Integer.toString(utterance))) return;
        if (speechTimeout != null) handler.removeCallbacks(speechTimeout);
        Runnable next = afterSpeech;
        afterSpeech = null;
        int completedTurn = utterance;
        // A short acoustic tail prevents the prompt itself being recognized as the answer.
        handler.postDelayed(() -> {
            if (!closed && completedTurn == utterance && next != null) next.run();
        }, 350);
    }
    public void listen() {
        if (closed) return;
        destroyRecognizer();
        if (!router.prepareRecognition() || closed) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        SpeechRecognizer current = recognizer;
        recognizer.setRecognitionListener(new RecognitionListener() {
            private boolean active() { return !closed && listening && recognizer == current; }
            @Override public void onReadyForSpeech(Bundle params) { if (active()) events.listening(); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle results) {}
            @Override public void onEvent(int type, Bundle params) {}
            @Override public void onResults(Bundle result) {
                if (!active()) return;
                listening = false;
                handler.removeCallbacks(listenTimeout);
                ArrayList<String> texts = result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                destroyRecognizer();
                // Recognition has finished; protect search/confirmation and the next spoken turn.
                if (!router.prepareSpeech() || closed) return;
                // Only final, top-ranked recognition can confirm a destination.
                if (texts == null || texts.isEmpty() || texts.get(0).isBlank()) events.missed();
                else events.heard(texts.get(0));
            }
            @Override public void onError(int error) {
                if (!active()) return;
                listening = false;
                handler.removeCallbacks(listenTimeout);
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) events.missed();
                else events.fatal(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ? "マイクの権限を許可してください。"
                        : "音声認識が利用できません。通信状態と日本語の音声認識設定を確認してください。（" + error + "）");
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        listenTimeout = () -> {
            if (!closed && listening && recognizer == current) {
                destroyRecognizer();
                events.missed();
            }
        };
        handler.postDelayed(listenTimeout, 20000);
        try { recognizer.startListening(intent); }
        catch (RuntimeException ex) { events.fatal("音声認識を開始できませんでした。音声認識サービスを確認してください。"); }
    }
    private void destroyRecognizer() {
        listening = false;
        if (listenTimeout != null) handler.removeCallbacks(listenTimeout);
        SpeechRecognizer previous = recognizer;
        recognizer = null;
        if (previous != null) { previous.cancel(); previous.destroy(); }
    }
    @Override public void close() {
        closed = true;
        afterSpeech = null;
        if (speechTimeout != null) handler.removeCallbacks(speechTimeout);
        destroyRecognizer();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
