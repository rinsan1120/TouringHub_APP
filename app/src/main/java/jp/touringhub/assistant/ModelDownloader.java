package jp.touringhub.assistant;

import android.os.Handler;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads the pinned Whisper model files with progress.
 * Each file lands as "<name>.part" first and is renamed only after its size matches
 * the pinned expectation, so an interrupted download never looks installed.
 * Listener callbacks are delivered on the supplied handler.
 */
public final class ModelDownloader implements AutoCloseable {
    public interface Listener {
        void progress(int percent);
        void done();
        void failed(String message);
    }

    private final Handler handler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;

    public ModelDownloader(Handler handler) {
        this.handler = handler;
    }

    public void download(File root, WhisperModels.Size size, Listener listener) {
        cancelled = false;
        executor.execute(() -> run(root, size, listener));
    }

    public void cancel() {
        cancelled = true;
    }

    @Override public void close() {
        cancelled = true;
        executor.shutdownNow();
    }

    private void run(File root, WhisperModels.Size size, Listener listener) {
        File dir = WhisperModels.directory(root, size);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            deliver(listener::failed, "モデルの保存先を作成できませんでした。");
            return;
        }
        long total = WhisperModels.totalBytes(size);
        long doneBytes = 0;
        int[] lastPercent = {-1};
        for (WhisperModels.FileSpec spec : WhisperModels.files(size)) {
            File target = new File(dir, spec.name());
            if (target.isFile() && target.length() == spec.bytes()) {
                doneBytes += spec.bytes();
                continue;
            }
            File part = new File(dir, spec.name() + ".part");
            try {
                doneBytes = downloadOne(spec, part, doneBytes, total, listener, lastPercent);
            } catch (IOException ex) {
                part.delete();
                deliver(listener::failed, "モデルのダウンロードに失敗しました。通信状態を確認して、もう一度お試しください。");
                return;
            }
            if (cancelled) {
                part.delete();
                return;
            }
            if (part.length() != spec.bytes() || !part.renameTo(target)) {
                part.delete();
                deliver(listener::failed, "モデルのダウンロードが不完全でした。もう一度お試しください。");
                return;
            }
        }
        if (!cancelled) handler.post(listener::done);
    }

    private long downloadOne(WhisperModels.FileSpec spec, File part, long doneBytes, long total,
                             Listener listener, int[] lastPercent) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(spec.url()).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + connection.getResponseCode());
        }
        try (InputStream input = connection.getInputStream();
             OutputStream output = new FileOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (cancelled) return doneBytes;
                output.write(buffer, 0, read);
                doneBytes += read;
                int percent = (int) (doneBytes * 100 / total);
                if (percent != lastPercent[0]) {
                    lastPercent[0] = percent;
                    if (!cancelled) handler.post(() -> listener.progress(percent));
                }
            }
        } finally {
            connection.disconnect();
        }
        return doneBytes;
    }

    private void deliver(java.util.function.Consumer<String> message, String text) {
        if (!cancelled) handler.post(() -> message.accept(text));
    }
}
