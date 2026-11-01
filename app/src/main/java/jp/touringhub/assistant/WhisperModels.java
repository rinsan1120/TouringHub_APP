package jp.touringhub.assistant;

import java.io.File;
import java.util.List;

/**
 * Pinned on-device Whisper models: the sherpa-onnx int8 builds mirrored on Hugging Face.
 * Free to download and run locally; no API key, account, or per-use fee is involved.
 * Exact byte sizes double as the integrity check for downloaded files.
 */
public final class WhisperModels {
    public enum Size { BASE, SMALL }

    public record FileSpec(String name, String url, long bytes) {}

    private static final long TOKENS_BYTES = 816_730L;
    private static final long BASE_ENCODER_BYTES = 29_120_534L;
    private static final long BASE_DECODER_BYTES = 130_672_026L;
    private static final long SMALL_ENCODER_BYTES = 112_442_483L;
    private static final long SMALL_DECODER_BYTES = 262_226_114L;

    private WhisperModels() {}

    public static Size size(boolean small) { return small ? Size.SMALL : Size.BASE; }

    public static String prefix(Size size) { return size == Size.SMALL ? "small" : "base"; }

    public static List<FileSpec> files(Size size) {
        String prefix = prefix(size);
        String repo = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-" + prefix + "/resolve/main/";
        long encoder = size == Size.SMALL ? SMALL_ENCODER_BYTES : BASE_ENCODER_BYTES;
        long decoder = size == Size.SMALL ? SMALL_DECODER_BYTES : BASE_DECODER_BYTES;
        return List.of(
                new FileSpec(prefix + "-encoder.int8.onnx", repo + prefix + "-encoder.int8.onnx", encoder),
                new FileSpec(prefix + "-decoder.int8.onnx", repo + prefix + "-decoder.int8.onnx", decoder),
                new FileSpec(prefix + "-tokens.txt", repo + prefix + "-tokens.txt", TOKENS_BYTES));
    }

    public static File directory(File root, Size size) {
        return new File(root, "whisper-" + prefix(size));
    }

    /** Installed means every required file is present with its exact expected size. */
    public static boolean installed(File root, Size size) {
        File dir = directory(root, size);
        for (FileSpec spec : files(size)) {
            File file = new File(dir, spec.name());
            if (!file.isFile() || file.length() != spec.bytes()) return false;
        }
        return true;
    }

    public static long totalBytes(Size size) {
        long total = 0;
        for (FileSpec spec : files(size)) total += spec.bytes();
        return total;
    }
}
