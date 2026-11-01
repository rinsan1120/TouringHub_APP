package jp.touringhub.assistant;

import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public class WhisperModelsTest {
    @Test public void baseFilesArePinned() {
        List<WhisperModels.FileSpec> files = WhisperModels.files(WhisperModels.Size.BASE);
        assertEquals(3, files.size());
        assertEquals("base-encoder.int8.onnx", files.get(0).name());
        assertEquals(29_120_534L, files.get(0).bytes());
        assertEquals("base-decoder.int8.onnx", files.get(1).name());
        assertEquals(130_672_026L, files.get(1).bytes());
        assertEquals("base-tokens.txt", files.get(2).name());
        assertEquals(816_730L, files.get(2).bytes());
        assertEquals(160_609_290L, WhisperModels.totalBytes(WhisperModels.Size.BASE));
        for (WhisperModels.FileSpec file : files) {
            assertEquals("https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/"
                    + file.name(), file.url());
        }
    }

    @Test public void smallFilesArePinned() {
        List<WhisperModels.FileSpec> files = WhisperModels.files(WhisperModels.Size.SMALL);
        assertEquals(3, files.size());
        assertEquals(112_442_483L, files.get(0).bytes());
        assertEquals(262_226_114L, files.get(1).bytes());
        assertEquals(816_730L, files.get(2).bytes());
        assertEquals(375_485_327L, WhisperModels.totalBytes(WhisperModels.Size.SMALL));
        for (WhisperModels.FileSpec file : files) {
            assertEquals("https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main/"
                    + file.name(), file.url());
        }
    }

    @Test public void sizeFlagMapping() {
        assertEquals(WhisperModels.Size.BASE, WhisperModels.size(false));
        assertEquals(WhisperModels.Size.SMALL, WhisperModels.size(true));
        assertEquals("base", WhisperModels.prefix(WhisperModels.Size.BASE));
        assertEquals("small", WhisperModels.prefix(WhisperModels.Size.SMALL));
    }

    @Test public void installedRequiresEveryFileWithExactSize() throws Exception {
        File root = Files.createTempDirectory("whisper-models").toFile();
        WhisperModels.Size size = WhisperModels.Size.BASE;
        assertFalse(WhisperModels.installed(root, size));
        File dir = WhisperModels.directory(root, size);
        assertTrue(dir.mkdirs());
        for (WhisperModels.FileSpec spec : WhisperModels.files(size)) {
            try (RandomAccessFile file = new RandomAccessFile(new File(dir, spec.name()), "rw")) {
                file.setLength(spec.bytes());
            }
        }
        assertTrue(WhisperModels.installed(root, size));
        File tokens = new File(dir, "base-tokens.txt");
        try (RandomAccessFile file = new RandomAccessFile(tokens, "rw")) {
            file.setLength(816_729L);
        }
        assertFalse(WhisperModels.installed(root, size));
    }
}
