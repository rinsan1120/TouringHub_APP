package jp.touringhub.assistant;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ConversationTest {
    @Test public void recoveredProviderNameKeepsItsOwnAddressAndPointThroughSelectionAndLaunch() {
        Destination address = Destination.addressCandidate("東京都大田区東糀谷1-2-3", 35.55, 139.74, "東京都大田区");
        Destination named = ProviderNames.geocoded("1-2-4", "モトラッド羽田", "東京都大田区東糀谷1-2-4",
                35.5501, 139.7401, "東京都大田区", "東京都", "大田区", "東糀谷", "1-2-4");
        for (boolean tap : new boolean[]{false, true}) {
            Fake host = new Fake(); Conversation c = new Conversation(host);
            c.start(); c.heard("モトラッド羽田");
            c.found(host.request, CandidateRanking.select(List.of(address, named, OTHER), null, host.query));
            assertSame(named, c.candidates().get(0));
            assertTrue(host.prompt.contains("モトラッド羽田"));
            if (tap) c.choose(0); else c.heard("1番");
            assertSame(named, c.selected()); assertEquals(0, host.launches);
            c.heard("はい"); assertSame(named, host.destination);
            assertEquals("東京都大田区東糀谷1-2-4", host.destination.address());
            assertEquals("35.5501,139.7401", host.destination.coordinates());
        }
    }
    @Test public void unconfirmedAddressRemainsUnconfirmedThroughVoiceAndTapConfirmation() {
        Destination address = Destination.addressCandidate("東京都大田区東糀谷1-2-3", 35.55, 139.74, "東京都大田区");
        for (boolean tap : new boolean[]{false, true}) {
            Fake host = new Fake(); Conversation c = new Conversation(host);
            c.start(); c.heard("モトラッド羽田"); c.found(host.request, List.of(address, OTHER));
            assertTrue(host.prompt.contains("施設名未確認")); assertTrue(host.prompt.contains(address.address()));
            if (tap) c.choose(0); else c.heard("1番");
            assertSame(address, c.selected()); assertEquals(0, host.launches);
            assertTrue(host.prompt.contains("施設名未確認")); assertTrue(host.prompt.contains(address.address()));
            assertFalse(host.prompt.contains("モトラッド羽田"));
            c.heard("はい"); assertSame(address, host.destination);
            assertFalse(host.destination.hasFacilityName()); assertEquals("35.55,139.74", host.destination.coordinates());
        }
    }
    @Test public void singleUnconfirmedAddressNeedsExplicitConfirmation() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination address = Destination.addressCandidate("東京都大田区東糀谷", 35.55, 139.74, "東京都大田区");
        c.start(); c.heard("モトラッド羽田"); c.found(host.request, List.of(address));
        assertEquals(Conversation.State.CONFIRM, c.state()); assertEquals(0, host.launches);
        assertTrue(host.prompt.contains("施設名未確認"));
        c.heard("はい"); assertSame(address, host.destination);
    }
    private static final Destination DOSHI = new Destination("道の駅どうし", "山梨県南都留郡道志村", 35.5, 139.0, "山梨県道志村");
    private static final Destination OTHER = new Destination("同名施設", "静岡県御殿場市", 35.3, 138.9);
    private static final class Fake implements Conversation.Host {
        String prompt, query, finish;
        int request, launches;
        Destination destination;
        List<Destination> shown = List.of();
        @Override public void prompt(String message) { prompt = message; }
        @Override public void search(String text, int token) { query = text; request = token; }
        @Override public void navigate(Destination value) { launches++; destination = value; }
        @Override public void finish(String message) { finish = message; }
        @Override public void candidatesChanged(List<Destination> values) { shown = values; }
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
    @Test public void proximityFilteredSingleResultSkipsNumberChoiceButRequiresConfirmation() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination shop = new Destination("施設内店舗", "山梨県道志村", 35.5001, 139.0);
        c.start(); c.heard("道の駅どうしに行きたい");
        c.found(host.request, CandidateRanking.select(List.of(DOSHI, shop), null));
        assertEquals(Conversation.State.CONFIRM, c.state());
        assertTrue(host.shown.isEmpty()); assertFalse(host.prompt.contains("何番"));
        assertTrue(host.prompt.contains("道の駅どうし")); assertEquals(0, host.launches);
        c.heard("はい"); assertEquals(DOSHI, host.destination); assertEquals(1, host.launches);
    }
    @Test public void representativeSelectionConfirmsTheActualFacilityRatherThanItsShop() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination shop = new Destination("道の駅どうし 売店", "山梨県道志村", 35.5001, 139.0);
        c.start(); c.heard("道の駅どうしに行きたい");
        c.found(host.request, CandidateRanking.select(List.of(shop, DOSHI), null, host.query));
        assertEquals(Conversation.State.CONFIRM, c.state());
        assertFalse(host.prompt.contains("売店")); assertEquals(0, host.launches);
        c.heard("はい"); assertSame(DOSHI, host.destination); assertEquals(1, host.launches);
    }
    @Test public void missingMainFacilityConfirmsReturnedShopNameWithoutInventingMainFacility() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination shop = new Destination("道の駅どうし 売店", "山梨県道志村", 35.5001, 139.0);
        c.start(); c.heard("道の駅どうしに行きたい");
        c.found(host.request, CandidateRanking.select(List.of(shop), null, host.query));
        assertEquals(Conversation.State.CONFIRM, c.state());
        assertTrue(host.prompt.contains("売店")); assertEquals(0, host.launches);
        c.heard("違う"); assertEquals(Conversation.State.DESTINATION, c.state());
        assertEquals(0, host.launches);
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
    @Test public void voiceChoiceAndNaturalAffirmativeStillRequireTwoSteps() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(DOSHI, OTHER));
        c.heard("2番でお願い");
        assertEquals(Conversation.State.CONFIRM, c.state()); assertEquals(0, host.launches);
        assertTrue(host.shown.isEmpty());
        c.heard("お願い"); assertEquals(OTHER, host.destination);
    }
    @Test public void tappingChoiceNeedsExplicitConfirmationAndLaunchesExactlyOnce() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(DOSHI, OTHER));
        assertFalse(c.confirmSelection()); assertFalse(c.reselect());
        assertFalse(c.choose(-1)); assertFalse(c.choose(2));
        assertTrue(c.choose(0)); assertFalse(c.choose(1));
        assertEquals(0, host.launches); assertTrue(host.shown.isEmpty());
        assertTrue(c.isTapSelection()); assertSame(DOSHI, c.selected());
        assertEquals(Conversation.State.CONFIRM, c.state());
        assertTrue(host.prompt.contains("はい、または、違う"));
        assertTrue(c.confirmSelection()); assertFalse(c.confirmSelection());
        assertFalse(c.reselect()); c.heard("はい");
        assertEquals(1, host.launches); assertSame(DOSHI, host.destination);
    }
    @Test public void reselectRestoresOriginalCandidatesAndCanConfirmAnotherDestinationByVoice() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        List<Destination> original = List.of(DOSHI, OTHER);
        c.start(); c.heard("候補を探す"); c.found(host.request, original);
        c.choose(0); assertTrue(c.reselect());
        assertNull(c.selected()); assertFalse(c.isTapSelection());
        assertEquals(Conversation.State.CHOICE, c.state());
        assertEquals(original, host.shown); assertFalse(c.confirmSelection());
        assertEquals(0, host.launches);
        c.heard("2番"); assertFalse(c.isTapSelection()); assertFalse(c.reselect());
        assertSame(OTHER, c.selected()); c.heard("はい");
        assertSame(OTHER, host.destination); assertEquals(1, host.launches);
    }
    @Test public void tapConfirmationAcceptsVoiceYesButRejectsAmbiguousAnswers() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(DOSHI, OTHER));
        c.choose(1); c.heard("はいじゃない");
        assertEquals(0, host.launches); assertEquals(Conversation.State.CONFIRM, c.state());
        c.heard("はい"); assertEquals(1, host.launches); assertSame(OTHER, host.destination);
    }
    @Test public void tapRejectionAndSessionEndInvalidateScreenConfirmation() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(DOSHI, OTHER));
        c.choose(0); c.heard("違う");
        assertEquals(Conversation.State.DESTINATION, c.state());
        assertNull(c.selected()); assertFalse(c.confirmSelection()); assertFalse(c.reselect());
        c.heard("探し直す"); c.found(host.request, List.of(DOSHI, OTHER));
        c.choose(1); c.stop("タイムアウト");
        assertNull(c.selected()); assertFalse(c.confirmSelection()); assertFalse(c.reselect());
        c.heard("はい"); assertEquals(0, host.launches);
    }
    @Test public void repeatedTapConfirmationKeepsDetailedAddressAndDoesNotNavigate() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination a = new Destination("同名施設", "東京都港区一丁目", 35, 139, "東京都港区");
        Destination b = new Destination("同名施設", "東京都港区二丁目", 35.1, 139, "東京都港区");
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(a, b));
        c.choose(1); String prompt = host.prompt;
        assertTrue(prompt.contains("二丁目")); c.heard("もう一度言って");
        assertEquals(prompt, host.prompt); assertSame(b, c.selected());
        assertEquals(0, host.launches); assertTrue(c.reselect());
        c.choose(0); assertTrue(host.prompt.contains("一丁目"));
        c.confirmSelection(); assertSame(a, host.destination); assertEquals(1, host.launches);
    }
    @Test public void screenAndVoicePresentSameThreeCandidates() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination third = new Destination("3件目", "東京都千代田区", 35, 139, "東京都千代田区");
        Destination fourth = new Destination("4件目", "東京都港区", 35, 139, "東京都港区");
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(DOSHI, OTHER, third, fourth));
        assertEquals(List.of(DOSHI, OTHER, third), host.shown);
        List<String> labels = c.candidateLabels();
        for (int i = 0; i < labels.size(); i++) assertTrue(host.prompt.contains((i + 1) + "番、" + labels.get(i)));
        assertFalse(host.prompt.contains("4件目")); assertFalse(c.choose(3));
        c.heard("1番じゃない"); assertEquals(Conversation.State.CHOICE, c.state());
    }
    @Test public void sameCityAndFacilityNameRetainDetailedAddressForDisambiguation() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        Destination a = new Destination("同名施設", "東京都港区一丁目", 35, 139, "東京都港区");
        Destination b = new Destination("同名施設", "東京都港区二丁目", 35.1, 139, "東京都港区");
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(a, b));
        assertTrue(host.prompt.contains("一丁目")); assertTrue(host.prompt.contains("二丁目"));
        c.heard("2番"); assertTrue(host.prompt.contains("二丁目"));
        assertEquals(0, host.launches);
    }
    @Test public void cancellingOrRejectingCandidatesClearsButtonsAndRejectsStaleTap() {
        Fake host = new Fake(); Conversation c = new Conversation(host);
        c.start(); c.heard("候補を探す"); c.found(host.request, List.of(DOSHI, OTHER));
        c.heard("違う"); assertTrue(host.shown.isEmpty()); assertFalse(c.choose(0));
        c.heard("もう一度検索"); c.found(host.request, List.of(DOSHI, OTHER));
        c.heard("キャンセル"); assertTrue(host.shown.isEmpty()); assertFalse(c.choose(1));
        assertEquals(0, host.launches);
    }
    @Test public void naturalNumberChoicesAreStrictAboutNegationAndMultipleNumbers() {
        for (String value : List.of("2番でお願い", "二番目でお願いします", "2番がいいです", "2番をお願い", "2ばんにしてください")) {
            assertEquals(value, 1, JapaneseInput.choice(value, 3));
        }
        for (String value : List.of("2番じゃない", "2番か3番", "2番ではなく1番", "4番でお願い")) {
            assertEquals(value, -1, JapaneseInput.choice(value, 3));
        }
    }
}
