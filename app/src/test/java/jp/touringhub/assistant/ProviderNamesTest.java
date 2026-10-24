package jp.touringhub.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProviderNamesTest {
    private static final String ADDRESS = "日本、〒144-0033 東京都大田区東糀谷1-2-3";
    private Destination result(String feature, String premises) {
        return ProviderNames.geocoded(feature, premises, ADDRESS, 35.55, 139.74, "東京都大田区",
                "日本", "東京都", "大田区", "東糀谷", "1-2-3", "144-0033");
    }
    @Test public void missingNumberAndAddressFeaturesStayExplicitlyUnconfirmed() {
        for (String feature : new String[]{null, "", "１－２－３", "東京都大田区東糀谷1-2-3", "東糀谷", ADDRESS}) {
            Destination d = result(feature, null);
            assertFalse(String.valueOf(feature), d.hasFacilityName());
            assertEquals(ADDRESS, d.address());
            assertTrue(d.spokenLabel().contains("施設名未確認"));
            assertTrue(d.confirmationLabel().contains(ADDRESS));
            assertEquals("35.55,139.74", d.coordinates());
        }
    }
    @Test public void providerPremisesRecoversNameWithoutUsingSearchInput() {
        Destination d = result("1-2-3", "モトラッド羽田");
        assertTrue(d.hasFacilityName()); assertEquals("モトラッド羽田", d.name());
        assertEquals(ADDRESS, d.address()); assertEquals("35.55,139.74", d.coordinates());
    }
    @Test public void actualFeatureWinsOverDifferentPremisesAndNumbersInNamesAreValid() {
        assertEquals("施設123", result("施設123", "別の施設").name());
        assertEquals("モトラッド柏", result("モトラッド柏", "モトラッド羽田").name());
        assertFalse(result(null, "東糀谷1-2-3").hasFacilityName());
    }
}
