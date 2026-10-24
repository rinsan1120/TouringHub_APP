package jp.touringhub.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.text.method.LinkMovementMethod;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    private CheckBox bluetooth;
    private RadioGroup navigation;
    private RadioButton google;
    private RadioButton movilink;
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
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 28, 40));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(content);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(dp(24) + bars.left, dp(16) + bars.top, dp(24) + bars.right, dp(24) + bars.bottom);
            return insets;
        });
        TextView eyebrow = text("TOURING HUB  /  VOICE NAVI", 12, MINT);
        eyebrow.setLetterSpacing(0.12f);
        content.addView(eyebrow);
        TextView title = text("次の目的地を、\n声で決めよう。", 30, WHITE);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);
        content.addView(text("停車中に準備して開始してください。\nアプリの表示中は画面が点灯し、会話の聞き取りは自動で続きます。", 15, MUTED));

        bluetooth = new CheckBox(this);
        bluetooth.setText(R.string.bluetooth_mode);
        bluetooth.setTextColor(WHITE);
        bluetooth.setMinHeight(dp(56));
        bluetooth.setChecked(getPreferences(MODE_PRIVATE).getBoolean("bluetooth", true));
        content.addView(bluetooth);
        navigation = new RadioGroup(this);
        google = new RadioButton(this); google.setId(View.generateViewId());
        google.setText(R.string.google_mode); google.setTextColor(WHITE); google.setMinHeight(dp(52));
        movilink = new RadioButton(this); movilink.setId(View.generateViewId());
        movilink.setText(R.string.movilink_mode); movilink.setTextColor(WHITE); movilink.setMinHeight(dp(52));
        navigation.addView(google); navigation.addView(movilink);
        navigation.check(getPreferences(MODE_PRIVATE).getBoolean("movilink", false) ? movilink.getId() : google.getId());
        content.addView(navigation);

        start = new Button(this);
        start.setText("会話をはじめる"); start.setTextSize(25); start.setAllCaps(false);
        start.setTypeface(null, Typeface.BOLD); start.setTextColor(Color.rgb(16, 28, 40));
        GradientDrawable background = new GradientDrawable();
        background.setColor(MINT); background.setCornerRadius(dp(28));
        start.setBackground(background);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(152));
        buttonParams.setMargins(0, dp(20), 0, dp(16));
        content.addView(start, buttonParams);
        start.setOnClickListener(view -> {
            if (active) { end("会話を終了しました。"); return; }
            requestStart();
        });
        status = text("待機中", 20, MINT); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        candidateList = new LinearLayout(this);
        candidateList.setOrientation(LinearLayout.VERTICAL);
        candidateList.setVisibility(View.GONE);
        content.addView(candidateList);
        locationStatus = text("", 13, MUTED);
        locationStatus.setVisibility(View.GONE);
        content.addView(locationStatus);
        transcript = text("", 16, WHITE);
        content.addView(transcript);
        addHelpSection(content, R.string.help_usage_title, getString(R.string.help_usage));
        addHelpSection(content, R.string.help_notices_title, getString(R.string.location_explanation)
                + "\n\n" + getString(R.string.help_notices)
                + "\n\n" + getString(R.string.search_service_notice));
        TextView attribution = text("", 12, MUTED);
        attribution.setText(android.text.Html.fromHtml(getString(R.string.search_attribution), android.text.Html.FROM_HTML_MODE_LEGACY));
        attribution.setMovementMethod(LinkMovementMethod.getInstance());
        attribution.setLinkTextColor(MINT);
        content.addView(attribution);
        setContentView(scroll);
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
        if (bluetooth.isChecked() && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
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
                || (bluetooth.isChecked() && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            status.setText("マイク・インカムの権限が必要です。許可できない場合は端末設定から確認してください。");
            return;
        }
        // Permission dialogs can resume the Activity just after the callback.
        handler.post(() -> { if (foreground && !active) begin(); });
    }
    private void begin() {
        if (active || !foreground) return;
        useMovilink = navigation.getCheckedRadioButtonId() == movilink.getId();
        if (!Navigator.available(this, useMovilink)) {
            status.setText(useMovilink ? "moviLinkの共有先が見つかりません。インストールと初期設定を確認してください。"
                    : "Googleマップが見つかりません。インストールと初期設定を確認してください。"); return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("bluetooth", bluetooth.isChecked())
                .putBoolean("movilink", useMovilink).apply();
        active = true;
        int session = ++generation;
        misses = 0;
        start.setText("会話を終了する");
        bluetooth.setEnabled(false); google.setEnabled(false); movilink.setEnabled(false);
        status.setText("音声を準備しています…");
        conversation = new Conversation(new Conversation.Host() {
            @Override public void candidatesChanged(List<Destination> candidates) {
                if (valid(session)) renderCandidates(session);
            }
            @Override public void prompt(String message) {
                if (!valid(session)) return;
                status.setText("読み上げ中");
                transcript.setText(message);
                speech.speak(message, () -> { if (valid(session)) { status.setText("聞き取りを開始しています…"); speech.listen(); } });
            }
            @Override public void search(String query, int request) {
                if (!valid(session)) return;
                status.setText("目的地を検索中…"); transcript.setText(query);
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
                status.setText("目的地を確定しました"); transcript.setText(destination.spokenLabel());
                speech.speak(spokenMessage, () -> {
                    if (!valid(session)) return;
                    boolean targetMovilink = useMovilink;
                    end(message);
                    if (!Navigator.launch(MainActivity.this, destination, targetMovilink)) {
                        status.setText("ナビアプリを開けませんでした。アプリのインストールと設定を確認してください。");
                    }
                });
            }
            @Override public void finish(String message) {
                if (!valid(session)) return;
                status.setText(message);
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
                    router.open(bluetooth.isChecked(), () -> { if (valid(session)) conversation.start(); },
                            message -> { if (valid(session)) end(message); });
                } catch (RuntimeException ex) { end("音声経路を設定できませんでした。Bluetooth接続と権限を確認してください。"); }
            }
            @Override public void listening() { if (valid(session)) status.setText("聞いています • 話してください"); }
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
        List<String> labels = conversation.candidateLabels();
        candidateList.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            Button choice = new Button(this);
            choice.setText(getString(R.string.candidate_button, i + 1, labels.get(i)));
            choice.setAllCaps(false);
            choice.setTextSize(18);
            choice.setMinHeight(dp(80));
            choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            choice.setOnClickListener(view -> {
                if (valid(session) && conversation.state() == Conversation.State.CHOICE) {
                    misses = 0;
                    conversation.choose(index);
                }
            });
            candidateList.addView(choice, new LinearLayout.LayoutParams(-1, -2));
        }
        if (!labels.isEmpty()) candidateList.post(() -> {
            if (valid(session) && conversation.state() == Conversation.State.CHOICE) {
                candidateList.requestRectangleOnScreen(new Rect(0, 0, candidateList.getWidth(), candidateList.getHeight()), true);
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
        start.setText("会話をはじめる");
        bluetooth.setEnabled(true); google.setEnabled(true); movilink.setEnabled(true);
        status.setText(message);
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
