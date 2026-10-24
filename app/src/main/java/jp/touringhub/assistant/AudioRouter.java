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
    public static AudioAttributes voiceAttributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }
    private final AudioManager manager;
    private final Handler handler;
    private AudioFocusRequest focus;
    private AudioFocusLease focusLease;
    private AudioManager.OnCommunicationDeviceChangedListener listener;
    private AudioManager.OnModeChangedListener modeListener;
    private Consumer<String> failure;
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
        failure = onFailure;
        focusLease = new AudioFocusLease(new AudioFocusLease.Backend() {
            @Override public boolean request(Runnable onLoss) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(voiceAttributes()).setOnAudioFocusChangeListener(change -> {
                            if (change < 0) onLoss.run();
                        }, handler).build();
                return manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            @Override public void abandon() {
                AudioFocusRequest previous = focus;
                focus = null;
                if (previous != null) manager.abandonAudioFocusRequest(previous);
            }
        }, () -> {
            if (owned) onFailure.accept("ほかの通話や音声が優先されたため、会話を終了しました。");
        });
        // We yield focus to the recognition service during capture. Calls must still stop
        // the conversation in that interval, without mistaking recognition's focus for a call.
        modeListener = mode -> {
            if (owned && isTelephoneMode(mode)) {
                onFailure.accept("電話の着信や通話のため、会話を終了しました。");
            }
        };
        manager.addOnModeChangedListener(handler::post, modeListener);
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
    static boolean isTelephoneMode(int mode) {
        return mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_CALL_SCREENING || mode == AudioManager.MODE_CALL_REDIRECT
                || mode == AudioManager.MODE_COMMUNICATION_REDIRECT;
    }
    private boolean canUseAudio() {
        if (!owned) return false;
        if (isTelephoneMode(manager.getMode())) {
            failure.accept("電話の着信や通話のため、会話を終了しました。");
            return false;
        }
        return true;
    }
    public boolean prepareSpeech() {
        if (!canUseAudio()) return false;
        if (!focusLease.acquire()) {
            if (owned) failure.accept("音声を使用できません。ほかの音声アプリを確認してください。");
            return false;
        }
        return true;
    }
    public boolean prepareRecognition() {
        if (!canUseAudio()) return false;
        // The recognizer runs in another package and may acquire its own exclusive focus.
        // Abandon ours before startListening; do not tear down the Bluetooth audio route.
        focusLease.release();
        return canUseAudio();
    }
    @Override public void close() {
        if (timeout != null) handler.removeCallbacks(timeout);
        if (!owned) return;
        owned = false;
        if (focusLease != null) focusLease.close();
        if (modeListener != null) manager.removeOnModeChangedListener(modeListener);
        if (listener != null) manager.removeOnCommunicationDeviceChangedListener(listener);
        manager.clearCommunicationDevice();
        // Remove our mode request even if Android has temporarily made it inactive.
        // A real phone call's higher-priority mode remains owned by Telecom.
        manager.setMode(oldMode);
    }
}
