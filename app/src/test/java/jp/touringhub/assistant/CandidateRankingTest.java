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
    @Test public void differentFacilitiesAtSameCoordinatesRemainSelectable() {
        Destination one = at("施設A", 35), two = at("施設B", 35);
        assertEquals(2, CandidateRanking.select(List.of(one, two), null).size());
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
