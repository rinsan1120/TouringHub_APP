package jp.touringhub.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import java.util.function.Consumer;

/** Owns communication routing only for the duration of a foreground conversation. */
public final class AudioRouter implements AutoCloseable {
    public static final AudioAttributes VOICE = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioManager manager;
    private final Handler handler;
    private AudioFocusRequest focus;
    private AudioManager.OnCommunicationDeviceChangedListener listener;
    private int oldMode;
    private int targetId = -1;
    private boolean owned;
    private boolean ready;
    private Runnable timeout;
    public AudioRouter(Context context, Handler handler) {
        manager = context.getSystemService(AudioManager.class);
        this.handler = handler;
    }
    public void open(boolean bluetooth, Runnable onReady, Consumer<String> onFailure) {
        if (manager.getMode() != AudioManager.MODE_NORMAL) {
            onFailure.accept("通話中などのため音声を使用できません。通話を終了してから開始してください。"); return;
        }
        AudioDeviceInfo target = null;
        for (AudioDeviceInfo device : manager.getAvailableCommunicationDevices()) {
            if (bluetooth && (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET)) { target = device; break; }
            if (!bluetooth && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) target = device;
        }
        if (target == null) {
            onFailure.accept(bluetooth ? "インカムの通話用接続が見つかりません。Bluetooth設定でB+COMの通話音声を接続してください。" : "端末の音声入出力が見つかりません。"); return;
        }
        oldMode = manager.getMode();
        owned = true;
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(VOICE).setOnAudioFocusChangeListener(change -> {
                    if (owned && change < 0) onFailure.accept("ほかの通話や音声が優先されたため、会話を終了しました。");
                }, handler).build();
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onFailure.accept("音声を使用できません。ほかの音声アプリを確認してください。"); return;
        }
        targetId = target.getId();
        listener = device -> {
            if (!owned) return;
            if (device != null && device.getId() == targetId) {
                if (!ready) {
                    ready = true;
                    if (timeout != null) handler.removeCallbacks(timeout);
                    onReady.run();
                }
            } else if (ready) {
                onFailure.accept("音声の接続が切れたため、会話を終了しました。接続を確認してください。");
            }
        };
        manager.addOnCommunicationDeviceChangedListener(handler::post, listener);
        manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (!manager.setCommunicationDevice(target)) {
            onFailure.accept("音声の接続先を設定できませんでした。接続を確認してください。"); return;
        }
        timeout = () -> {
            if (owned && !ready) onFailure.accept("インカムの音声接続が時間内に完了しませんでした。");
        };
        handler.postDelayed(timeout, 6000);
        listener.onCommunicationDeviceChanged(manager.getCommunicationDevice());
    }
    @Override public void close() {
        if (timeout != null) handler.removeCallbacks(timeout);
        if (!owned) return;
        owned = false;
        if (listener != null) manager.removeOnCommunicationDeviceChangedListener(listener);
        manager.clearCommunicationDevice();
        if (manager.getMode() == AudioManager.MODE_IN_COMMUNICATION) manager.setMode(oldMode);
        if (focus != null) manager.abandonAudioFocusRequest(focus);
    }
}
