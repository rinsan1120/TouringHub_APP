package jp.touringhub.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class DestinationTest {
    @Test public void facilityConfirmationOmitsPostalAddressButRetainsNavigationData() {
        String address = "日本、〒402-0219 山梨県南都留郡道志村9745番地";
        Destination d = new Destination("道の駅どうし", address, 35.5, 139.0,
                Destination.area("山梨県", "南都留郡道志村", "9745番地"));
        assertEquals("山梨県道志村の道の駅どうし", d.confirmationLabel());
        assertEquals(address, d.address()); assertEquals("35.5,139.0", d.coordinates());
        assertTrue(d.spokenLabel().contains("9745番地")); // candidate disambiguation keeps detail
    }
    @Test public void cityNamesContainingCityCharacterAreNotTruncated() {
        assertEquals("三重県四日市市", Destination.area("三重県", "四日市市", null));
        assertEquals("千葉県市川市", Destination.area("千葉県", "市川市", null));
    }
    @Test public void wardsAreKeptWithoutDuplicatingPrefectureOrWard() {
        assertEquals("神奈川県横浜市中区", Destination.area("神奈川県", "横浜市", "中区"));
        assertEquals("東京都千代田区", Destination.area("東京都", "千代田区", "千代田区"));
        assertEquals("東京都千代田区", Destination.area("東京都", "東京都千代田区", null));
    }
    @Test public void localityDestinationDoesNotRepeatItsName() {
        assertEquals("山梨県道志村", new Destination("道志村", "住所", 35, 139, "山梨県道志村").confirmationLabel());
    }
    @Test public void missingStructuredAreaKeepsStreetToAvoidInventedLocation() {
        Destination d = new Destination("施設", "日本、〒100-0001 東京都千代田区千代田1-1", 35, 139);
        assertEquals("施設、東京都千代田区千代田1-1", d.confirmationLabel());
    }
    @Test public void addressesAndEmptyAddressesDoNotDuplicateName() {
        assertEquals("東京駅", new Destination("東京駅", "", 35, 139).confirmationLabel());
        assertEquals("千代田1-1", new Destination("千代田1-1", "千代田1-1", 35, 139).confirmationLabel());
    }
}
