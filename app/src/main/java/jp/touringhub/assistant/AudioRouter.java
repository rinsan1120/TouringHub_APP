package jp.touringhub.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import java.util.function.Consumer;

/** Owns communication routing only for the duration of a foreground conversation. */
public final class AudioRouter implements AutoCloseable {
    public AudioAttributes speechAttributes() {
        return new AudioAttributes.Builder().setUsage(useBluetooth
                        ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }
    private final AudioManager manager;
    private final Handler handler;
    private AudioFocusRequest focus;
    private AudioFocusLease focusLease;
    private AudioManager.OnCommunicationDeviceChangedListener listener;
    private AudioManager.OnModeChangedListener modeListener;
    private AudioDeviceCallback deviceListener;
    private CommunicationRouteGuard routeGuard;
    private Consumer<String> failure;
    private int oldMode;
    private int targetId = -1;
    private boolean owned;
    private boolean ready;
    private boolean useBluetooth;
    private Runnable timeout;
    public AudioRouter(Context context, Handler handler) {
        manager = context.getSystemService(AudioManager.class);
        this.handler = handler;
    }
    public void open(boolean bluetooth, Runnable onReady, Consumer<String> onFailure) {
        if (manager.getMode() != AudioManager.MODE_NORMAL) {
            onFailure.accept("通話中などのため音声を使用できません。通話を終了してから開始してください。"); return;
        }
        useBluetooth = bluetooth;
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
                        .setAudioAttributes(speechAttributes()).setOnAudioFocusChangeListener(change -> {
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
            if (owned && isInterruptingMode(mode, useBluetooth)) {
                onFailure.accept("電話の着信や通話のため、会話を終了しました。");
            }
        };
        manager.addOnModeChangedListener(handler::post, modeListener);
        if (!useBluetooth) {
            // TTS is not a phone call. Keep the handset in its normal playback mode;
            // communication-route expiry/recovery can otherwise interrupt its audio.
            ready = true;
            onReady.run();
            return;
        }
        targetId = target.getId();
        routeGuard = new CommunicationRouteGuard(new CommunicationRouteGuard.Backend() {
            @Override public boolean targetAvailable() { return findTarget() != null; }
            @Override public boolean targetSelected() {
                AudioDeviceInfo device = manager.getCommunicationDevice();
                return device != null && device.getId() == targetId;
            }
            @Override public boolean restoreTarget() {
                if (!canUseAudio()) return false;
                AudioDeviceInfo device = findTarget();
                if (device == null) return false;
                // Android may expire an inactive communication route while TTS is playing
                // in another process. Restore only the original, still-connected device.
                manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                return manager.setCommunicationDevice(device);
            }
            @Override public void scheduleCheck(Runnable check) { handler.postDelayed(check, 1500); }
            @Override public void cancelCheck(Runnable check) { handler.removeCallbacks(check); }
        }, () -> {
            if (owned) onFailure.accept("音声の接続が切れたため、会話を終了しました。接続を確認してください。");
        });
        deviceListener = new AudioDeviceCallback() {
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) {
                if (!owned) return;
                for (AudioDeviceInfo device : devices) {
                    if (device.getId() == targetId) { routeGuard.removed(); return; }
                }
            }
        };
        manager.registerAudioDeviceCallback(deviceListener, handler);
        listener = device -> {
            if (!owned) return;
            if (device != null && device.getId() == targetId) {
                routeGuard.changed(true);
                if (!ready) {
                    ready = true;
                    if (timeout != null) handler.removeCallbacks(timeout);
                    onReady.run();
                }
            } else if (ready) {
                routeGuard.changed(false);
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
    private AudioDeviceInfo findTarget() {
        for (AudioDeviceInfo device : manager.getAvailableCommunicationDevices()) {
            if (device.getId() == targetId) return device;
        }
        return null;
    }
    static boolean isTelephoneMode(int mode) {
        return mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_CALL_SCREENING || mode == AudioManager.MODE_CALL_REDIRECT
                || mode == AudioManager.MODE_COMMUNICATION_REDIRECT;
    }
    static boolean isInterruptingMode(int mode, boolean bluetooth) {
        return isTelephoneMode(mode) || (!bluetooth && mode == AudioManager.MODE_IN_COMMUNICATION);
    }
    private boolean canUseAudio() {
        if (!owned) return false;
        if (isInterruptingMode(manager.getMode(), useBluetooth)) {
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
        if (routeGuard != null) routeGuard.close();
        if (deviceListener != null) manager.unregisterAudioDeviceCallback(deviceListener);
        if (focusLease != null) focusLease.close();
        if (modeListener != null) manager.removeOnModeChangedListener(modeListener);
        if (listener != null) manager.removeOnCommunicationDeviceChangedListener(listener);
        if (useBluetooth) manager.clearCommunicationDevice();
        // Remove our mode request even if Android has temporarily made it inactive.
        // A real phone call's higher-priority mode remains owned by Telecom.
        if (useBluetooth) manager.setMode(oldMode);
    }
}
