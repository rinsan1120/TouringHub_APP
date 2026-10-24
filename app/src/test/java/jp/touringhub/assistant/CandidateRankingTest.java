package jp.touringhub.assistant;

import org.junit.Test;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class CandidateRankingTest {
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
}
