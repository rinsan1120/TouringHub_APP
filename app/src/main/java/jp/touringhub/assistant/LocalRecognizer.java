package jp.touringhub.assistant;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import java.io.File;

/**
 * Offline Whisper recognition through sherpa-onnx, fully on-device:
 * recorded audio never leaves the phone and no account or fee is involved.
 * Construction loads the model and may take a moment; call it off the main thread.
 * transcribe() blocks; call it off the main thread as well.
 */
public final class LocalRecognizer implements AutoCloseable {
    private final OfflineRecognizer recognizer;

    public LocalRecognizer(File directory, WhisperModels.Size size) {
        String prefix = WhisperModels.prefix(size);
        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(new File(directory, prefix + "-encoder.int8.onnx").getAbsolutePath());
        whisper.setDecoder(new File(directory, prefix + "-decoder.int8.onnx").getAbsolutePath());
        whisper.setLanguage("ja");
        whisper.setTask("transcribe");
        OfflineModelConfig model = new OfflineModelConfig();
        model.setWhisper(whisper);
        model.setTokens(new File(directory, prefix + "-tokens.txt").getAbsolutePath());
        model.setModelType("whisper");
        model.setNumThreads(4);
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setModelConfig(model);
        recognizer = new OfflineRecognizer(null, config);
    }

    /** Returns the recognized text, trimmed; empty when nothing intelligible was heard. */
    public String transcribe(float[] samples) {
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, UtteranceRecorder.SAMPLE_RATE);
            recognizer.decode(stream);
            String text = recognizer.getResult(stream).getText();
            return text == null ? "" : text.trim();
        } finally {
            stream.release();
        }
    }

    @Override public void close() {
        recognizer.release();
    }
}
