package jp.touringhub.assistant;

import android.content.Context;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Alternates TTS and one-shot on-device recognition; never listens while speaking.
 * Recording and recognition both run in this process: the recorder pins the Bluetooth
 * communication device, and the local Whisper engine transcribes what it captured.
 */
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
    private final LocalRecognizer recognizer;
    private final ExecutorService inference = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private UtteranceRecorder capture;
    private boolean closed;
    private int utterance;
    private int captureGeneration;
    private Runnable afterSpeech;
    private Runnable speechTimeout;
    private Runnable listenTimeout;
    private boolean listening;
    public SpeechSession(Context context, Handler handler, AudioRouter router,
                         LocalRecognizer recognizer, Events events) {
        this.context = context;
        this.handler = handler;
        this.router = router;
        this.recognizer = recognizer;
        this.events = events;
    }
    public void initialize() {
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
        stopCapture();
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
        stopCapture();
        if (!router.prepareRecognition() || closed) return;
        if (recognizer == null) {
            events.fatal("音声認識モデルが準備できていません。設定からモデルをダウンロードしてください。");
            return;
        }
        int generation = ++captureGeneration;
        listening = true;
        UtteranceRecorder recorder = new UtteranceRecorder(handler, router.communicationDevice());
        capture = recorder;
        listenTimeout = () -> {
            if (!closed && listening && generation == captureGeneration) {
                stopCapture();
                events.missed();
            }
        };
        handler.postDelayed(listenTimeout, 25000);
        recorder.start(new UtteranceRecorder.Callback() {
            @Override public void started() {
                if (!closed && listening && generation == captureGeneration) events.listening();
            }
            @Override public void captured(float[] samples) {
                if (closed || generation != captureGeneration) return;
                listening = false;
                if (listenTimeout != null) handler.removeCallbacks(listenTimeout);
                capture = null;
                inference.execute(() -> {
                    String text;
                    try { text = recognizer.transcribe(samples); }
                    catch (RuntimeException ex) { text = ""; }
                    String result = text;
                    handler.post(() -> {
                        if (closed || generation != captureGeneration) return;
                        // Recognition has finished; protect the next spoken turn.
                        if (!router.prepareSpeech() || closed) return;
                        if (result.isBlank()) events.missed();
                        else events.heard(result);
                    });
                });
            }
            @Override public void noSpeech() {
                if (closed || generation != captureGeneration) return;
                listening = false;
                if (listenTimeout != null) handler.removeCallbacks(listenTimeout);
                capture = null;
                events.missed();
            }
            @Override public void failed(String message) {
                if (closed || generation != captureGeneration) return;
                listening = false;
                if (listenTimeout != null) handler.removeCallbacks(listenTimeout);
                capture = null;
                events.fatal(message);
            }
        });
    }
    private void stopCapture() {
        ++captureGeneration;
        listening = false;
        if (listenTimeout != null) handler.removeCallbacks(listenTimeout);
        UtteranceRecorder current = capture;
        capture = null;
        if (current != null) current.cancel();
    }
    @Override public void close() {
        closed = true;
        afterSpeech = null;
        if (speechTimeout != null) handler.removeCallbacks(speechTimeout);
        stopCapture();
        inference.shutdownNow();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
