package jp.touringhub.assistant;

import java.util.List;
import java.util.stream.Collectors;

/** Voice selection requires confirmation; tapping a listed candidate explicitly confirms it. */
public final class Conversation {
    public static final int MAX_CANDIDATES = 3;
    public enum State { IDLE, DESTINATION, SEARCHING, CHOICE, CONFIRM, FINISHED }
    public interface Host {
        void prompt(String message);
        void search(String query, int request);
        void navigate(Destination destination);
        void finish(String message);
        default void candidatesChanged(List<Destination> candidates) {}
    }
    private final Host host;
    private State state = State.IDLE;
    private int request;
    private List<Destination> candidates = List.of();
    private Destination selected;
    private boolean detailedConfirmation;
    private boolean selectedByTap;
    private String lastPrompt = "";
    public Conversation(Host host) { this.host = host; }
    public State state() { return state; }
    public List<Destination> candidates() { return candidates; }
    public boolean isTapSelection() { return selectedByTap; }
    public List<String> candidateLabels() {
        return candidates.stream().map(destination -> {
            String label = destination.candidateLabel();
            return candidates.stream().filter(other -> other.candidateLabel().equals(label)).count() > 1
                    ? destination.spokenLabel() : label;
        }).collect(Collectors.toList());
    }
    public void start() {
        if (state != State.IDLE) return;
        askDestination("どこへ行きたいですか。中止する場合はキャンセルと言ってください。");
    }
    private void say(String text) { lastPrompt = text; host.prompt(text); }
    private void askDestination(String text) {
        selected = null;
        candidates = List.of();
        detailedConfirmation = false;
        selectedByTap = false;
        host.candidatesChanged(candidates);
        state = State.DESTINATION;
        say(text);
    }
    public void heard(String text) {
        if (state == State.IDLE || state == State.FINISHED || state == State.SEARCHING) return;
        if (JapaneseInput.cancel(text)) { stop("会話を終了しました。"); return; }
        if (JapaneseInput.repeat(text)) { say(lastPrompt); return; }
        if (state == State.CONFIRM) {
            if (JapaneseInput.yes(text)) {
                Destination confirmed = selected;
                state = State.FINISHED;
                ++request;
                host.navigate(confirmed);
            } else if (JapaneseInput.no(text)) {
                askDestination("目的地を言い直してください。地域名も付けると探しやすくなります。");
            } else {
                say("まだ確定していません。" + confirmationPrompt());
            }
            return;
        }
        if (state == State.CHOICE) {
            if (JapaneseInput.no(text)) {
                askDestination("別の目的地を、地域名と一緒に話してください。");
                return;
            }
            int index = JapaneseInput.choice(text, candidates.size());
            if (index >= 0) choose(index, true);
            else say("候補を番号で選んでください。" + choices());
            return;
        }
        String query = JapaneseInput.destination(text);
        if (query.isBlank() || JapaneseInput.yes(text) || JapaneseInput.no(text) || query.length() > 160) {
            say("施設名や住所を、地域名と一緒に話してください。");
            return;
        }
        state = State.SEARCHING;
        host.search(query, ++request);
    }
    public void found(int token, List<Destination> results) {
        if (state != State.SEARCHING || token != request) return;
        if (results == null || results.isEmpty()) {
            askDestination("目的地が見つかりませんでした。市町村名を付けるか、住所で言い直してください。");
        } else if (results.size() == 1) {
            confirm(results.get(0), false);
        } else {
            candidates = List.copyOf(results.subList(0, Math.min(MAX_CANDIDATES, results.size())));
            state = State.CHOICE;
            host.candidatesChanged(candidates);
            say("候補を" + candidates.size() + "件お伝えします。" + choices()
                    + "何番にしますか。どれでもなければ、違う、と答えてください。");
        }
    }
    public void searchFailed(int token) {
        if (state == State.SEARCHING && token == request) {
            askDestination("検索できませんでした。通信状態を確認して、目的地を言い直してください。");
        }
    }
    private String choices() {
        StringBuilder text = new StringBuilder();
        List<String> labels = candidateLabels();
        for (int i = 0; i < labels.size(); i++) text.append(i + 1).append("番、").append(labels.get(i)).append("。");
        return text.toString();
    }
    public boolean choose(int index) {
        return choose(index, false);
    }
    private boolean choose(int index, boolean repeat) {
        if (state != State.CHOICE || index < 0 || index >= candidates.size()) return false;
        Destination destination = candidates.get(index);
        boolean detailed = candidates.stream()
                .filter(other -> other.candidateLabel().equals(destination.candidateLabel())).count() > 1;
        if (repeat) confirm(destination, detailed);
        else {
            selected = destination;
            selectedByTap = true;
            candidates = List.of();
            state = State.FINISHED;
            ++request;
            host.candidatesChanged(candidates);
            host.navigate(destination);
        }
        return true;
    }
    private void confirm(Destination destination, boolean detailed) {
        selected = destination;
        detailedConfirmation = detailed;
        selectedByTap = false;
        candidates = List.of();
        state = State.CONFIRM;
        host.candidatesChanged(candidates);
        say(confirmationPrompt());
    }
    private String confirmationPrompt() {
        String label = detailedConfirmation ? selected.spokenLabel() : selected.confirmationLabel();
        return label + "、でよろしいですか。はい、または、違う、とお答えください。";
    }
    public void retry() {
        if (state != State.IDLE && state != State.SEARCHING && state != State.FINISHED) {
            host.prompt("聞き取れませんでした。" + lastPrompt);
        }
    }
    public void stop(String text) {
        if (state == State.FINISHED) return;
        ++request;
        selected = null;
        candidates = List.of();
        host.candidatesChanged(candidates);
        state = State.FINISHED;
        host.finish(text);
    }
}
