package jp.touringhub.assistant;

import java.util.List;

/** Platform independent dialog. Only CONFIRM + an explicit affirmative can navigate. */
public final class Conversation {
    public enum State { IDLE, DESTINATION, SEARCHING, CHOICE, CONFIRM, FINISHED }
    public interface Host {
        void prompt(String message);
        void search(String query, int request);
        void navigate(Destination destination);
        void finish(String message);
    }
    private final Host host;
    private State state = State.IDLE;
    private int request;
    private List<Destination> candidates = List.of();
    private Destination selected;
    private String lastPrompt = "";
    public Conversation(Host host) { this.host = host; }
    public State state() { return state; }
    public void start() {
        if (state != State.IDLE) return;
        askDestination("どこへ行きたいですか。中止する場合はキャンセルと言ってください。");
    }
    private void say(String text) { lastPrompt = text; host.prompt(text); }
    private void askDestination(String text) {
        selected = null;
        candidates = List.of();
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
            if (index >= 0) confirm(candidates.get(index));
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
            confirm(results.get(0));
        } else {
            candidates = List.copyOf(results.subList(0, Math.min(5, results.size())));
            state = State.CHOICE;
            say("複数の候補があります。" + choices() + "番号で答えてください。どれでもなければ、違う、と答えてください。");
        }
    }
    public void searchFailed(int token) {
        if (state == State.SEARCHING && token == request) {
            askDestination("検索できませんでした。通信状態を確認して、目的地を言い直してください。");
        }
    }
    private String choices() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) text.append(i + 1).append("番、").append(candidates.get(i).spokenLabel()).append("。");
        return text.toString();
    }
    private void confirm(Destination destination) {
        selected = destination;
        state = State.CONFIRM;
        say(confirmationPrompt());
    }
    private String confirmationPrompt() {
        return selected.confirmationLabel() + "、でよろしいですか。はい、または、違う、とお答えください。";
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
        state = State.FINISHED;
        host.finish(text);
    }
}
