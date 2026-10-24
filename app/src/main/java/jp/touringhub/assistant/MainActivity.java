package jp.touringhub.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int MINT = Color.rgb(102, 227, 196);
    private static final int WHITE = Color.rgb(238, 245, 250);
    private static final int MUTED = Color.rgb(172, 190, 206);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView transcript;
    private TextView locationStatus;
    private LinearLayout candidateList;
    private Button start;
    private static final int BACKGROUND = Color.rgb(16, 28, 40);
    private static final int SURFACE = Color.rgb(24, 42, 58);
    private static final int STOP = Color.rgb(255, 184, 173);
    private LinearLayout preparation;
    private ScrollView preparationScroll;
    private TextView settingsSummary;
    private LinearLayout sessionScreen;
    private TextView phaseTitle;
    private TextView phaseHint;
    private TextView phaseAudio;
    private Button bluetoothOn;
    private Button bluetoothOff;
    private Button google;
    private Button movilink;
    private boolean useBluetooth;
    private boolean selectedMovilink;
    private ScrollView sessionScroll;
    private Conversation conversation;
    private SpeechSession speech;
    private AudioRouter router;
    private DestinationResolver resolver;
    private NearbyLocation nearbyLocation;
    private Runnable sessionTimeout;
    private Runnable initTimeout;
    private Runnable searchTimeout;
    private boolean active;
    private boolean foreground;
    private boolean useMovilink;
    private int generation;
    private int misses;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolver = new DestinationResolver(this);
        nearbyLocation = new NearbyLocation(this, handler);
        buildScreen();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private GradientDrawable surface(int color, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(20));
        if (border != 0) shape.setStroke(dp(2), border);
        return shape;
    }
    private Button action(String label, int color, int foregroundColor, int minimumHeight) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(20);
        button.setTextColor(foregroundColor);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinHeight(dp(minimumHeight));
        button.setMinimumHeight(dp(minimumHeight));
        button.setPadding(dp(20), dp(18), dp(20), dp(18));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)),
                surface(color, 0), null));
        return button;
    }
    private void addSpaced(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, 0);
        parent.addView(view, params);
    }
    private TextView heading(String title, int size) {
        TextView view = text(title, size, WHITE);
        view.setTypeface(null, Typeface.BOLD);
        view.setAccessibilityHeading(true);
        return view;
    }
    private void buildScreen() {
        useBluetooth = getPreferences(MODE_PRIVATE).getBoolean("bluetooth", true);
        selectedMovilink = getPreferences(MODE_PRIVATE).getBoolean("movilink", false);
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(20), dp(12), dp(20), dp(16));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, dp(16) + bars.bottom);
            return insets;
        });
        TextView brand = text("TOURING HUB  /  VOICE NAVI", 12, MINT);
        brand.setLetterSpacing(0.10f);
        root.addView(brand);

        // One Activity and one session owner. Switching these views has no lifecycle effects.
        android.widget.FrameLayout pages = new android.widget.FrameLayout(this);
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        preparationScroll = new ScrollView(this);
        preparationScroll.setFillViewport(true);
        preparation = column();
        preparationScroll.addView(preparation);
        pages.addView(preparationScroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        sessionScroll = new ScrollView(this);
        sessionScroll.setFillViewport(true);
        sessionScreen = column();
        sessionScroll.addView(sessionScreen);
        sessionScroll.setVisibility(View.GONE);
        pages.addView(sessionScroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        // Pane metadata announces screen changes; hidden controls cannot receive accessibility focus.
        preparationScroll.setAccessibilityPaneTitle("出発前の準備");
        sessionScroll.setAccessibilityPaneTitle("音声で目的地を決める");
        preparation.addView(heading("出発前の準備", 28));
        preparation.addView(text("停車中に開始してください", 16, MUTED));
        sessionScreen.addView(heading("音声で目的地を決める", 24));

        status = text("", 17, WHITE);
        status.setPadding(dp(16), dp(16), dp(16), dp(16));
        status.setBackground(surface(SURFACE, MINT));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        preparation.addView(status);
        settingsSummary = text("", 18, WHITE);
        settingsSummary.setPadding(dp(16), dp(16), dp(16), dp(16));
        settingsSummary.setBackground(surface(SURFACE, 0));
        settingsSummary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addSpaced(preparation, settingsSummary);
        Button editSettings = action("▸ 音声・ナビ設定を変更", SURFACE, MINT, 64);
        editSettings.setTextSize(17);
        editSettings.setStateDescription(getString(R.string.help_collapsed));
        addSpaced(preparation, editSettings);
        LinearLayout settings = column();
        settings.setVisibility(View.GONE);
        preparation.addView(settings);
        editSettings.setOnClickListener(view -> {
            if (active) return;
            boolean expanded = settings.getVisibility() != View.VISIBLE;
            settings.setVisibility(expanded ? View.VISIBLE : View.GONE);
            editSettings.setText(expanded ? "▾ 音声・ナビ設定を閉じる" : "▸ 音声・ナビ設定を変更");
            editSettings.setStateDescription(getString(expanded ? R.string.help_expanded : R.string.help_collapsed));
        });
        settings.addView(heading("音声の入出力", 19));
        bluetoothOn = action("", SURFACE, WHITE, 80);
        bluetoothOff = action("", SURFACE, WHITE, 80);
        addSpaced(settings, bluetoothOn);
        addSpaced(settings, bluetoothOff);
        bluetoothOn.setOnClickListener(view -> selectSettings(true, selectedMovilink));
        bluetoothOff.setOnClickListener(view -> selectSettings(false, selectedMovilink));
        addSpaced(settings, heading("ナビ連携先", 19));
        google = action("", SURFACE, WHITE, 88);
        movilink = action("", SURFACE, WHITE, 88);
        addSpaced(settings, google);
        addSpaced(settings, movilink);
        google.setOnClickListener(view -> selectSettings(useBluetooth, false));
        movilink.setOnClickListener(view -> selectSettings(useBluetooth, true));
        refreshSettings();
        addSpaced(preparation, text("画面での操作は停車中に行ってください。", 14, MUTED));
        addHelpSection(preparation, R.string.help_usage_title, getString(R.string.help_usage));
        addHelpSection(preparation, R.string.help_notices_title, getString(R.string.location_explanation)
                + "\n\n" + getString(R.string.help_notices)
                + "\n\n" + getString(R.string.search_service_notice));
        Button downloadUpdate = action(getString(R.string.download_update), SURFACE, MINT, 56);
        downloadUpdate.setTextSize(17);
        downloadUpdate.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        downloadUpdate.setContentDescription(getString(R.string.download_update_description));
        addSpaced(preparation, downloadUpdate);
        downloadUpdate.setOnClickListener(view -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(getString(R.string.download_update_url))));
            } catch (android.content.ActivityNotFoundException | SecurityException ex) {
                android.widget.Toast.makeText(this, R.string.download_update_unavailable,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
        TextView attribution = text("", 13, MUTED);
        attribution.setText(android.text.Html.fromHtml(getString(R.string.search_attribution), android.text.Html.FROM_HTML_MODE_LEGACY));
        attribution.setMovementMethod(LinkMovementMethod.getInstance());
        attribution.setLinkTextColor(MINT);
        preparation.addView(attribution);

        LinearLayout phase = column();
        phase.setPadding(dp(20), dp(16), dp(20), dp(16));
        phase.setBackground(surface(SURFACE, MINT));
        phaseTitle = heading("", 26);
        phaseTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        phaseHint = text("", 18, WHITE);
        phaseAudio = text("", 15, MINT);
        phaseAudio.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        phase.addView(phaseTitle);
        phase.addView(phaseHint);
        phase.addView(phaseAudio);
        sessionScreen.addView(phase);
        candidateList = column();
        candidateList.setVisibility(View.GONE);
        addSpaced(sessionScreen, candidateList);
        transcript = text("", 18, WHITE);
        transcript.setVisibility(View.GONE);
        addSpaced(sessionScreen, transcript);
        locationStatus = text("", 14, MUTED);
        locationStatus.setVisibility(View.GONE);
        sessionScreen.addView(locationStatus);
        sessionScreen.addView(text("音声で操作できます。画面操作は停車中に。", 14, MUTED));

        sessionScreen.addView(text("「キャンセル」と話しても終了できます。", 14, MUTED));
        start = action("▶  会話をはじめる", MINT, BACKGROUND, 88);
        start.setContentDescription("会話をはじめる");
        addSpaced(root, start);
        start.setOnClickListener(view -> {
            if (active) end("会話を終了しました。");
            else requestStart();
        });
        setContentView(root);
    }
    private void selectSettings(boolean bluetooth, boolean movilink) {
        if (active) return;
        useBluetooth = bluetooth;
        selectedMovilink = movilink;
        getPreferences(MODE_PRIVATE).edit().putBoolean("bluetooth", bluetooth)
                .putBoolean("movilink", movilink).apply();
        refreshSettings();
    }
    private void settingCard(Button view, String title, String detail, boolean selected) {
        view.setText(getString(R.string.setting_card, selected ? "✓ 選択中" : "○", title, detail));
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setTextSize(17);
        view.setSelected(selected);
        view.setStateDescription(selected ? "選択中" : "未選択");
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)),
                surface(selected ? Color.rgb(27, 65, 64) : SURFACE, selected ? MINT : 0), null));
    }
    private void refreshSettings() {
        settingsSummary.setText(getString(R.string.settings_summary,
                useBluetooth ? getString(R.string.bluetooth_mode) : "スマートフォン本体を使う",
                getString(selectedMovilink ? R.string.movilink_mode : R.string.google_mode)));
        settingCard(bluetoothOn, "Bluetoothインカム", "接続済みインカムで話す・聞く", useBluetooth);
        settingCard(bluetoothOff, "スマートフォン本体", "本体のマイク・スピーカーを使う", !useBluetooth);
        settingCard(google, "Google マップ", "目的地を渡して案内へ", !selectedMovilink);
        settingCard(movilink, "moviLink", "施設を共有・ルート操作は停車中に", selectedMovilink);
    }
    private void showSession(boolean showing) {
        preparationScroll.setVisibility(showing ? View.GONE : View.VISIBLE);
        sessionScroll.setVisibility(showing ? View.VISIBLE : View.GONE);
        start.setText(showing ? "■  会話を終了する" : "▶  会話をはじめる");
        start.setContentDescription(showing ? "会話を終了する" : "会話をはじめる");
        start.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)),
                surface(showing ? STOP : MINT, 0), null));
        bluetoothOn.setEnabled(!showing); bluetoothOff.setEnabled(!showing);
        google.setEnabled(!showing); movilink.setEnabled(!showing);
        if (showing) sessionScroll.scrollTo(0, 0);
    }
    private void showResult(String message) {
        status.setText(getString(R.string.session_result, message));
        status.setVisibility(View.VISIBLE);
        preparationScroll.post(() -> preparationScroll.smoothScrollTo(0, 0));
    }
    private void phase(String title, String hint, String audio) {
        phaseTitle.setText(title);
        phaseHint.setText(hint);
        phaseAudio.setText(audio);
        phaseAudio.setVisibility(audio.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void voicePhase(boolean listening) {
        String audio = listening ? "● 聞き取り中" : "♫ 読み上げ中";
        if (conversation.state() == Conversation.State.CHOICE) {
            phase("≡ 候補選択中", "番号で答えてください。画面で選ぶ場合は停車中に。", audio);
        } else if (conversation.state() == Conversation.State.CONFIRM) {
            phase("✓ 目的地確認中", "正しければ「はい」、言い直す場合は「違う」と答えてください。", audio);
        } else {
            phase(listening ? "● 聞き取り中" : "♫ 読み上げ中",
                    listening ? "目的地の施設名や住所を、地域名と一緒に話してください。" : "読み上げが終わったら話してください。", "");
        }
    }
    private void addHelpSection(LinearLayout parent, int titleResource, String explanation) {
        String title = getString(titleResource);
        Button heading = new Button(this);
        heading.setText(getString(R.string.help_closed, title));
        heading.setTextColor(MINT);
        heading.setTextSize(17);
        heading.setAllCaps(false);
        heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setMinHeight(dp(56));
        heading.setPadding(dp(16), dp(8), dp(16), dp(8));
        heading.setAccessibilityHeading(true);
        heading.setStateDescription(getString(R.string.help_collapsed));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(24, 42, 58));
        background.setCornerRadius(dp(12));
        heading.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, 0);
        parent.addView(heading, params);
        TextView body = text(explanation, 13, MUTED);
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        body.setVisibility(View.GONE);
        parent.addView(body);
        heading.setOnClickListener(view -> {
            boolean expanded = body.getVisibility() != View.VISIBLE;
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            heading.setText(getString(expanded ? R.string.help_open : R.string.help_closed, title));
            heading.setStateDescription(getString(expanded ? R.string.help_expanded : R.string.help_collapsed));
        });
    }
    private void requestStart() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO);
        if (useBluetooth && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("location_requested", false)) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            getPreferences(MODE_PRIVATE).edit().putBoolean("location_requested", true).apply();
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("fine_location_requested", false)) {
            if (!permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            getPreferences(MODE_PRIVATE).edit().putBoolean("fine_location_requested", true).apply();
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 10);
            return;
        }
        begin();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != 10) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || (useBluetooth && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            showResult("マイク・インカムの権限が必要です。許可できない場合は端末設定から確認してください。");
            return;
        }
        // Permission dialogs can resume the Activity just after the callback.
        handler.post(() -> { if (foreground && !active) begin(); });
    }
    private void begin() {
        if (active || !foreground) return;
        useMovilink = selectedMovilink;
        if (!Navigator.available(this, useMovilink)) {
            showResult(useMovilink ? "moviLinkの共有先が見つかりません。インストールと初期設定を確認してください。"
                    : "Googleマップが見つかりません。インストールと初期設定を確認してください。"); return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("bluetooth", useBluetooth)
                .putBoolean("movilink", useMovilink).apply();
        active = true;
        int session = ++generation;
        misses = 0;
        status.setVisibility(View.GONE);
        transcript.setText(""); transcript.setVisibility(View.GONE);
        locationStatus.setText(""); locationStatus.setVisibility(View.GONE);
        showSession(true);
        phase("◷ 音声準備中", "音声の接続が整うまでお待ちください。", "");
        conversation = new Conversation(new Conversation.Host() {
            @Override public void candidatesChanged(List<Destination> candidates) {
                if (valid(session)) renderCandidates(session);
            }
            @Override public void prompt(String message) {
                if (!valid(session)) return;
                voicePhase(false);
                transcript.setText(message); transcript.setVisibility(View.VISIBLE);
                speech.speak(message, () -> { if (valid(session)) { phaseAudio.setText("聞き取りを開始しています…"); phaseAudio.setVisibility(View.VISIBLE); speech.listen(); } });
            }
            @Override public void search(String query, int request) {
                if (!valid(session)) return;
                phase("⌕ 目的地検索中", "候補が見つかるまでお待ちください。", "");
                transcript.setText(getString(R.string.search_query, query)); transcript.setVisibility(View.VISIBLE);
                searchTimeout = () -> { if (valid(session)) conversation.searchFailed(request); };
                Runnable thisTimeout = searchTimeout;
                handler.postDelayed(thisTimeout, 20000);
                nearbyLocation.get(origin -> {
                    if (!valid(session)) return;
                    locationStatus.setText(origin == null ? R.string.nearby_unavailable : R.string.nearby_ready);
                    locationStatus.setVisibility(View.VISIBLE);
                    try {
                        resolver.search(query, origin, new DestinationResolver.Callback() {
                            @Override public void success(List<Destination> results) {
                                handler.post(() -> {
                                    if (!valid(session)) return;
                                    handler.removeCallbacks(thisTimeout);
                                    conversation.found(request, results);
                                });
                            }
                            @Override public void failure() {
                                handler.post(() -> {
                                    if (!valid(session)) return;
                                    handler.removeCallbacks(thisTimeout);
                                    conversation.searchFailed(request);
                                });
                            }
                        });
                    } catch (RuntimeException ex) {
                        handler.removeCallbacks(thisTimeout);
                        conversation.searchFailed(request);
                    }
                });
            }
            @Override public void navigate(Destination destination) {
                if (!valid(session)) return;
                String message = useMovilink ? "moviLinkへ施設を共有します。ルート操作は停車中に行ってください。"
                        : "Googleマップに目的地を渡します。";
                String spokenMessage = conversation.isTapSelection() ? "この目的地で案内します。"
                        : useMovilink ? "モビリンクへ施設を共有します。ルート操作は停車中におこなってください。" : message;
                phase("✓ 目的地を確定しました", message, "♫ 読み上げ中");
                transcript.setText(destination.spokenLabel());
                speech.speak(spokenMessage, () -> {
                    if (!valid(session)) return;
                    boolean targetMovilink = useMovilink;
                    end(message);
                    if (!Navigator.launch(MainActivity.this, destination, targetMovilink)) {
                        showResult("ナビアプリを開けませんでした。アプリのインストールと設定を確認してください。");
                    }
                });
            }
            @Override public void finish(String message) {
                if (!valid(session)) return;
                phase("■ 会話を終了します", message, "♫ 読み上げ中");
                speech.speak(message, () -> { if (valid(session)) end(message); });
            }
        });
        sessionTimeout = () -> { if (valid(session)) end("3分経過したため会話を終了しました。"); };
        handler.postDelayed(sessionTimeout, 180000);
        initTimeout = () -> { if (valid(session)) end("音声の準備が時間内に完了しませんでした。音声合成設定を確認してください。"); };
        handler.postDelayed(initTimeout, 20000);
        router = new AudioRouter(MainActivity.this, handler);
        speech = new SpeechSession(this, handler, router, new SpeechSession.Events() {
            @Override public void ready() {
                if (!valid(session)) return;
                handler.removeCallbacks(initTimeout);
                try {
                    router.open(useBluetooth, () -> { if (valid(session)) conversation.start(); },
                            message -> { if (valid(session)) end(message); });
                } catch (RuntimeException ex) { end("音声経路を設定できませんでした。Bluetooth接続と権限を確認してください。"); }
            }
            @Override public void listening() { if (valid(session)) voicePhase(true); }
            @Override public void heard(String value) {
                if (!valid(session)) return;
                misses = 0;
                transcript.setText(getString(R.string.heard, value));
                conversation.heard(value);
            }
            @Override public void missed() {
                if (!valid(session)) return;
                if (++misses >= 3) conversation.stop("聞き取りが続けてできなかったため、会話を終了します。");
                else conversation.retry();
            }
            @Override public void fatal(String message) { if (valid(session)) end(message); }
        });
        speech.initialize();
    }
    private void renderCandidates(int session) {
        candidateList.removeAllViews();
        Destination selected = conversation.selected();
        boolean confirming = conversation.state() == Conversation.State.CONFIRM && selected != null;
        List<Destination> candidates = conversation.candidates();
        boolean choosing = conversation.state() == Conversation.State.CHOICE && !candidates.isEmpty();
        candidateList.setVisibility(confirming || choosing ? View.VISIBLE : View.GONE);
        if (confirming) {
            candidateList.addView(heading("この目的地でよろしいですか", 20));
            candidateList.addView(heading(selected.displayName(), 24));
            candidateList.addView(text(selected.address().isBlank() ? "所在地情報なし" : selected.address(), 18, WHITE));
            if (conversation.isTapSelection()) {
                candidateList.addView(text("確認してから案内を開始します。", 15, MUTED));
                Button confirm = action("▶  案内する", MINT, BACKGROUND, 80);
                confirm.setContentDescription("この目的地で案内する");
                addSpaced(candidateList, confirm);
                confirm.setOnClickListener(view -> {
                    if (valid(session) && conversation.state() == Conversation.State.CONFIRM
                            && conversation.selected() == selected) {
                        misses = 0;
                        conversation.confirmSelection();
                    }
                });
                Button reselect = action("↶  選び直す", SURFACE, WHITE, 80);
                addSpaced(candidateList, reselect);
                reselect.setOnClickListener(view -> {
                    if (valid(session) && conversation.state() == Conversation.State.CONFIRM
                            && conversation.selected() == selected) {
                        misses = 0;
                        conversation.reselect();
                    }
                });
            }
        } else if (choosing) {
            candidateList.addView(heading("目的地候補 · " + candidates.size() + "件", 20));
            for (int i = 0; i < candidates.size(); i++) {
                int index = i;
                Destination destination = candidates.get(i);
                Button choice = action(getString(R.string.candidate_button, i + 1,
                        destination.displayName() + "\n" + (destination.address().isBlank() ? "所在地情報なし" : destination.address())),
                        SURFACE, WHITE, 96);
                choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                choice.setTextSize(18);
                choice.setOnClickListener(view -> {
                    if (valid(session) && conversation.state() == Conversation.State.CHOICE
                            && index < conversation.candidates().size()
                            && conversation.candidates().get(index) == destination) {
                        misses = 0;
                        conversation.choose(index);
                    }
                });
                addSpaced(candidateList, choice);
            }
        }
        if (confirming || choosing) candidateList.post(() -> {
            if (valid(session)) {
                // Align the beginning of the candidates, even if the list is taller than the viewport.
                sessionScroll.smoothScrollTo(0, candidateList.getTop());
            }
        });
    }
    private boolean valid(int session) { return active && foreground && session == generation; }
    private void end(String message) {
        active = false;
        ++generation;
        handler.removeCallbacksAndMessages(null);
        nearbyLocation.close();
        resolver.cancel();
        if (conversation != null) { conversation.stop(message); conversation = null; }
        if (speech != null) { speech.close(); speech = null; }
        if (router != null) { router.close(); router = null; }
        candidateList.removeAllViews();
        candidateList.setVisibility(View.GONE);
        showSession(false);
        showResult(message);
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStop() {
        foreground = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (active) end("画面を離れたため会話を終了しました。");
        super.onStop();
    }
    @Override protected void onDestroy() {
        foreground = false;
        if (active) end("会話を終了しました。");
        nearbyLocation.close();
        resolver.close();
        super.onDestroy();
    }
}
