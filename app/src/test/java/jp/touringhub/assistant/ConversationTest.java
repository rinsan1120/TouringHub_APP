package jp.touringhub.assistant;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ConversationTest {
    private static final Destination DOSHI = new Destination("道の駅どうし", "山梨県南都留郡道志村", 35.5, 139.0, "山梨県道志村");
    private static final Destination OTHER = new Destination("同名施設", "静岡県御殿場市", 35.3, 138.9);
    private static final class Fake implements Conversation.Host {
        String prompt, query, finish;
        int request, launches;
        Destination destination;
        @Override public void prompt(String message) { prompt = message; }
        @Override public void search(String text, int token) { query = text; request = token; }
        @Override public void navigate(Destination value) { launches++; destination = value; }
        @Override public void finish(String message) { finish = message; }
    }
    @Test public void onlyExplicitConfirmationLaunchesOnce() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("道の駅どうしに行きたい");
        assertEquals("道の駅どうし", host.query);
        c.heard("はい"); assertEquals(0, host.launches); // no confirmation before search
        c.found(host.request, List.of(DOSHI));
        assertEquals("山梨県道志村の道の駅どうし、でよろしいですか。はい、または、違う、とお答えください。", host.prompt);
        for (String unsafe : List.of("はい、違います", "はいじゃない", "多分", "はい山梨ではない")) c.heard(unsafe);
        assertEquals(0, host.launches);
        c.heard("はい"); c.heard("はい");
        assertEquals(1, host.launches); assertEquals(DOSHI, host.destination);
    }
    @Test public void choiceStillNeedsConfirmation() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("同名施設"); c.found(host.request, List.of(DOSHI, OTHER));
        c.heard("はい"); assertEquals(Conversation.State.CHOICE, c.state());
        c.heard("2番"); assertEquals(Conversation.State.CONFIRM, c.state());
        assertEquals(0, host.launches); assertTrue(host.prompt.contains(OTHER.address()));
        c.heard("はい"); assertEquals(OTHER, host.destination);
    }
    @Test public void rejectedDestinationCannotBeLaunched() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("どうし"); c.found(host.request, List.of(DOSHI));
        c.heard("違う"); c.heard("はい");
        assertEquals(Conversation.State.DESTINATION, c.state()); assertEquals(0, host.launches);
        c.heard("御殿場駅"); c.found(host.request, List.of(OTHER)); c.heard("お願いします");
        assertEquals(OTHER, host.destination);
    }
    @Test public void staleResultAfterTimeoutCannotConfirm() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("古い場所"); int old = host.request;
        c.searchFailed(old); c.heard("新しい場所");
        c.found(old, List.of(DOSHI)); assertEquals(Conversation.State.SEARCHING, c.state());
        c.found(host.request, List.of(OTHER)); c.heard("はい"); assertEquals(OTHER, host.destination);
    }
    @Test public void cancelledSearchIgnoresLateResults() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("道志村"); c.stop("中止"); c.found(host.request, List.of(DOSHI)); c.heard("はい");
        assertEquals(Conversation.State.FINISHED, c.state()); assertEquals(0, host.launches);
    }
    @Test public void emptyAndFailedSearchNeverNavigate() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("不明"); c.found(host.request, List.of()); c.heard("はい");
        assertEquals(0, host.launches); assertEquals(Conversation.State.DESTINATION, c.state());
        c.heard("場所"); c.searchFailed(host.request); assertEquals(Conversation.State.DESTINATION, c.state());
    }
    @Test public void spokenCancelFinishes() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("キャンセル"); assertNotNull(host.finish); assertEquals(0, host.launches);
    }
    @Test public void naturalDestinationPhrasesKeepPlaceName() {
        assertEquals("道の駅どうし", JapaneseInput.destination("道の駅どうしに行きたい"));
        assertEquals("東京駅", JapaneseInput.destination("東京駅まで案内してください"));
        assertEquals("富士山", JapaneseInput.destination("目的地は富士山に行きたいです。"));
        assertEquals("新宿駅", JapaneseInput.destination("新宿駅を目的地にして"));
        assertEquals("国立公園", JapaneseInput.destination("国立公園"));
        assertEquals("道の駅どうし", JapaneseInput.destination("道の駅どうしに行きたいんだけど"));
    }
    @Test public void numbersAndOutOfRangeChoices() {
        assertEquals(0, JapaneseInput.choice("１番でお願いします", 3));
        assertEquals(1, JapaneseInput.choice("二番目", 3));
        assertEquals(2, JapaneseInput.choice("さんばん", 3));
        assertEquals(-1, JapaneseInput.choice("5番", 3));
        assertEquals(-1, JapaneseInput.choice("1番と2番", 3));
    }
    @Test public void repeatDoesNotConfirm() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("道志"); c.found(host.request, List.of(DOSHI));
        String prompt = host.prompt; c.heard("もう一度言って");
        assertEquals(prompt, host.prompt); assertEquals(0, host.launches);
    }
}
