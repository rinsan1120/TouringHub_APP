package jp.touringhub.assistant;

import org.junit.Test;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class CandidateRankingTest {
    @Test public void matchingNamedResultReplacesAddressWithoutMixingTheirDataInEitherOrder() {
        Destination address = Destination.addressCandidate("東京都大田区東糀谷1-2-3", 35, 139, "東京都大田区");
        Destination facility = new Destination("モトラッド羽田", "東京都大田区東糀谷4-5-6", 35.0001, 139.0001, "東京都大田区");
        for (List<Destination> results : List.of(List.of(address, facility), List.of(facility, address))) {
            assertSame(facility, CandidateRanking.select(results, new CandidateRanking.Origin(35, 139), "モトラッド羽田").get(0));
        }
    }
    @Test public void unrelatedNearbyBusinessCannotLabelAnUnconfirmedAddress() {
        Destination address = Destination.addressCandidate("東京都大田区東糀谷", 35, 139, "東京都大田区");
        Destination shop = new Destination("コンビニ", "東京都大田区別番地", 35.0001, 139);
        assertEquals(List.of(address), CandidateRanking.select(List.of(address, shop), null, "モトラッド羽田"));
        assertEquals(List.of(address), CandidateRanking.select(List.of(address), null, "モトラッド羽田"));
    }
    private Destination at(String name, double latitude) {
        return new Destination(name, "テスト用住所", latitude, 139);
    }
    @Test public void ranksAllResultsBeforeLimitingToThree() {
        Destination far = at("遠方", 37), medium = at("中間", 36), near = at("近い", 35.1), closest = at("最寄り", 35.01);
        assertEquals(List.of(closest, near, medium), CandidateRanking.select(List.of(far, medium, near, closest), new CandidateRanking.Origin(35, 139)));
    }
    @Test public void localAndGlobalDuplicatesDoNotOccupyMultipleSlots() {
        Destination first = at("同じ施設", 35);
        Destination duplicate = new Destination("同じ施設", "住所表記の別形式", 35, 139);
        Destination other = at("別の施設", 36);
        assertEquals(List.of(first, other), CandidateRanking.select(List.of(first, duplicate, other), null));
    }
    @Test public void missingLocationKeepsProviderOrder() {
        Destination one = at("1", 37), two = at("2", 35), three = at("3", 36), four = at("4", 34);
        assertEquals(List.of(one, two, three), CandidateRanking.select(List.of(one, two, three, four), null));
    }
    @Test public void differentFacilitiesAtSameCoordinatesShareOneSlot() {
        Destination one = at("施設A", 35), two = at("施設B", 35);
        assertEquals(List.of(one), CandidateRanking.select(List.of(one, two), null));
    }
    private Destination metersEast(String name, double meters) {
        return new Destination(name, "テスト用住所", 0, Math.toDegrees(meters / 6371000));
    }
    @Test public void nearbyResultsAreSkippedAndLaterResultsFillThreeSlots() {
        Destination first = metersEast("本施設", 0), shop = metersEast("店舗", 100);
        Destination annex = metersEast("関連施設", 500), second = metersEast("別地点", 2000);
        Destination third = metersEast("さらに別地点", 4000), fourth = metersEast("4件目", 6000);
        assertEquals(List.of(first, second, third), CandidateRanking.select(
                List.of(first, shop, annex, second, third, fourth), null));
    }
    @Test public void proximityIsCheckedAgainstEveryAcceptedCandidate() {
        Destination first = metersEast("1", 0), second = metersEast("2", 3000);
        Destination nearFirst = metersEast("1の近く", 500), nearSecond = metersEast("2の近く", 3500);
        Destination third = metersEast("3", 6000);
        assertEquals(List.of(first, second, third), CandidateRanking.select(
                List.of(first, second, nearFirst, nearSecond, third), null));
    }
    @Test public void rejectedCandidateDoesNotExcludeItsNeighbors() {
        Destination first = metersEast("採用1", 0), rejected = metersEast("除外", 750);
        Destination second = metersEast("採用2", 1500);
        assertEquals(List.of(first, second), CandidateRanking.select(List.of(first, rejected, second), null));
    }
    @Test public void exactlyOneKilometerIsAcceptedButLessIsExcluded() {
        Destination first = metersEast("基準", 0), inside = metersEast("内側", 999.999);
        // Use the outer representable coordinate at the boundary to avoid rounding
        // just below 1000 m during degree/radian conversion.
        Destination boundary = new Destination("境界", "テスト用住所", 0,
                Math.nextUp(Math.toDegrees(1000.0 / 6371000)));
        Destination outside = metersEast("外側", 1000.001);
        double boundaryDistance = new CandidateRanking.Origin(0, 0).distanceTo(boundary);
        assertEquals(1000, boundaryDistance, 1e-9);
        assertTrue(boundaryDistance >= 1000);
        assertEquals(List.of(first), CandidateRanking.select(List.of(first, inside), null));
        assertEquals(List.of(first, boundary), CandidateRanking.select(List.of(first, boundary), null));
        assertEquals(List.of(first, outside), CandidateRanking.select(List.of(first, outside), null));
    }
    @Test public void nearestPriorityIsAppliedBeforeProximityExclusion() {
        Destination farther = metersEast("遠い関連施設", 500), nearest = metersEast("最寄り", 100);
        Destination other = metersEast("別地点", 2000);
        assertEquals(List.of(nearest, other), CandidateRanking.select(
                List.of(farther, other, nearest), new CandidateRanking.Origin(0, 0)));
    }
    @Test public void clusterCanLeaveOnlyOneOrTwoRealCandidates() {
        Destination first = metersEast("1", 0), nearFirst = metersEast("近接", 200);
        Destination second = metersEast("2", 2000), nearSecond = metersEast("近接2", 2200);
        assertEquals(List.of(first), CandidateRanking.select(List.of(first, nearFirst), null));
        assertEquals(List.of(first, second), CandidateRanking.select(List.of(first, nearFirst, second, nearSecond), null));
    }
    @Test public void rankingAndDeduplicationAreLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Destination one = at("施設", 35.123456), two = at("施設", 35.123456);
            assertEquals(List.of(one), CandidateRanking.select(List.of(one, two), new CandidateRanking.Origin(35, 139)));
        } finally { Locale.setDefault(original); }
    }
    @Test public void longitudeWrapUsesShortDistanceAcrossDateLine() {
        Destination near = new Destination("近い", "テスト", 0, -179.9);
        Destination far = new Destination("遠い", "テスト", 0, 170);
        assertEquals(near, CandidateRanking.select(List.of(far, near), new CandidateRanking.Origin(0, 179.9)).get(0));
    }
    @Test public void neverPadsResultsWithInventedPlaces() {
        assertTrue(CandidateRanking.select(List.of(), null).isEmpty());
        Destination only = at("1件のみ", 35);
        assertSame(only, CandidateRanking.select(List.of(only), null).get(0));
        assertEquals(1, CandidateRanking.select(List.of(only), null).size());
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidLocation() {
        new CandidateRanking.Origin(Double.NaN, 139);
    }
    @Test public void preciseSensorCoordinatesAreRoundedBeforeSearch() {
        CandidateRanking.Origin original = new CandidateRanking.Origin(35.681236, 139.767125);
        CandidateRanking.Origin approximate = original.approximate();
        assertEquals(35.68, approximate.latitude(), 0);
        assertEquals(139.76, approximate.longitude(), 0);
        assertEquals(approximate, approximate.approximate());
        assertNotEquals(original, approximate);
    }
    @Test public void approximationRespectsGeographicLimits() {
        assertEquals(new CandidateRanking.Origin(90, 180), new CandidateRanking.Origin(89.99999, 179.99999).approximate());
        assertEquals(new CandidateRanking.Origin(-90, -180), new CandidateRanking.Origin(-89.99999, -179.99999).approximate());
    }
    @Test public void exactFacilityNameReplacesEarlierNearbyShopsAndStillFillsThreeSlots() {
        Destination kitchen = metersEast("道の駅どうし てづくりキッチン", 0);
        Destination shop = metersEast("道の駅どうし 売店", 100);
        Destination other = metersEast("別地点", 3000), third = metersEast("さらに別地点", 6000);
        Destination main = metersEast("道の駅どうし", 200);
        assertEquals(List.of(main, other, third), CandidateRanking.select(
                List.of(kitchen, shop, other, third, main), new CandidateRanking.Origin(0, 0), "道の駅どうし"));
    }
    @Test public void exactNameUsesExistingWidthWhitespaceAndCaseNormalization() {
        Destination shop = metersEast("ABC公園 売店", 0), main = metersEast("ＡＢＣ 公園", 100);
        assertEquals(List.of(main), CandidateRanking.select(List.of(shop, main), null, "abc公園"));
    }
    @Test public void closerNameWinsWithoutExactMatchAndRetainsActualProviderData() {
        Destination kitchen = metersEast("道の駅どうし てづくりキッチン", 0);
        Destination shop = metersEast("道の駅どうし 売店", 100);
        List<Destination> selected = CandidateRanking.select(List.of(kitchen, shop), null, "道の駅どうし");
        assertEquals(List.of(shop), selected);
        assertSame(shop, selected.get(0));
        assertEquals("道の駅どうし 売店", selected.get(0).name());
    }
    @Test public void equallySimilarNamesKeepExistingProviderAndDistancePriority() {
        Destination first = metersEast("公園A", 0), second = metersEast("公園B", 100);
        assertEquals(List.of(first), CandidateRanking.select(List.of(first, second), null, "公園"));
        assertEquals(List.of(second), CandidateRanking.select(
                List.of(first, second), new CandidateRanking.Origin(0, second.longitude()), "公園"));
    }
    @Test public void distantExactMatchDoesNotReorderDistinctPlaces() {
        Destination near = metersEast("公園 売店", 0), far = metersEast("公園", 3000);
        assertEquals(List.of(near, far), CandidateRanking.select(List.of(near, far), null, "公園"));
    }
    @Test public void representativeMovementCannotViolateSeparationOrPreventLaterRefill() {
        Destination first = metersEast("公園", 0), seed = metersEast("公園 売店", 1600);
        Destination conflicting = metersEast("公園", 800), second = metersEast("公園 別館", 2200);
        Destination third = metersEast("別地点", 4000);
        List<Destination> selected = CandidateRanking.select(
                List.of(first, seed, conflicting, second, third), null, "公園");
        assertEquals(List.of(first, seed, third), selected);
        for (int i = 0; i < selected.size(); i++) {
            for (int j = i + 1; j < selected.size(); j++) {
                Destination point = selected.get(i);
                assertTrue(new CandidateRanking.Origin(point.latitude(), point.longitude()).distanceTo(selected.get(j)) >= 1000);
            }
        }
    }
    @Test public void nearbyChainsDoNotMergeDistantPlacesTransitively() {
        Destination first = metersEast("公園", 0), middle = metersEast("公園 売店", 750);
        Destination last = metersEast("公園", 1500);
        assertEquals(List.of(first, last), CandidateRanking.select(List.of(first, middle, last), null, "公園"));
    }
    @Test public void rejectedSeedDoesNotSuppressAPlaceFarEnoughFromChosenRepresentative() {
        Destination seed = metersEast("公園 売店", 0), main = metersEast("公園", 900);
        Destination other = metersEast("別地点", -900);
        assertEquals(List.of(main, other), CandidateRanking.select(List.of(seed, main, other), null, "公園"));
    }
}
