package jp.touringhub.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotonSearchTest {
    private JSONObject feature() throws Exception {
        return new JSONObject().put("properties", new JSONObject().put("name", "中央公園")
                .put("state", "埼玉県").put("city", "熊谷市").put("countrycode", "JP"))
                .put("geometry", new JSONObject().put("type", "Point").put("coordinates", new JSONArray().put(139.4).put(36.1)));
    }
    private JSONObject response(JSONObject feature) throws Exception { return new JSONObject().put("features", new JSONArray().put(feature)); }
    @Test public void geoJsonLongitudeLatitudeOrderIsNotSwappedForNavigation() throws Exception {
        Destination d = PhotonSearch.parse(response(feature())).get(0);
        assertEquals(36.1, d.latitude(), 0); assertEquals(139.4, d.longitude(), 0);
        assertEquals("埼玉県熊谷市、中央公園", d.candidateLabel());
    }
    @Test public void neverInventsMissingFacilityNameOrCity() throws Exception {
        JSONObject f = feature(); f.getJSONObject("properties").remove("name");
        Destination unnamed = PhotonSearch.parse(response(f)).get(0);
        assertFalse(unnamed.hasFacilityName());
        assertEquals("住所候補（施設名未確認）、埼玉県熊谷市", unnamed.candidateLabel());
        f = feature(); f.getJSONObject("properties").remove("city");
        Destination named = PhotonSearch.parse(response(f)).get(0);
        assertTrue(named.hasFacilityName());
        assertEquals("中央公園", named.name());
        assertEquals("埼玉県", named.address());
    }
    @Test public void rejectsForeignResultsAndInvalidCoordinates() throws Exception {
        JSONObject f = feature(); f.getJSONObject("properties").put("countrycode", "TW");
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
        f = feature(); f.getJSONObject("geometry").put("coordinates", new JSONArray().put(139).put(100));
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
    }
    @Test public void skipsMalformedFeaturesButRetainsValidOnes() throws Exception {
        JSONObject json = new JSONObject().put("features", new JSONArray().put(JSONObject.NULL).put(new JSONObject()).put(feature()));
        assertEquals(1, PhotonSearch.parse(json).size());
        assertTrue(PhotonSearch.parse(new JSONObject()).isEmpty());
    }
    @Test public void doesNotTreatAreaGeometryAsDestinationPoint() throws Exception {
        JSONObject f = feature(); f.getJSONObject("geometry").put("type", "Polygon");
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
    }
    @Test public void limitsOverlongProviderNamesInsteadOfReadingUnboundedText() throws Exception {
        JSONObject f = feature(); f.getJSONObject("properties").put("name", "あ".repeat(161));
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
    }
    @Test public void facilityWithoutCityRetainsDistrictStreetAndItsOwnCoordinates() throws Exception {
        JSONObject f = feature();
        f.getJSONObject("properties").remove("city");
        f.getJSONObject("properties").put("name", "モトラッド羽田").put("osm_key", "shop")
                .put("state", "東京都").put("district", "大田区東糀谷").put("street", "通り").put("housenumber", "1-2");
        Destination d = PhotonSearch.parse(response(f)).get(0);
        assertTrue(d.hasFacilityName()); assertEquals("モトラッド羽田", d.name());
        assertEquals("東京都大田区東糀谷通り1-2", d.address());
        assertEquals(36.1, d.latitude(), 0); assertEquals(139.4, d.longitude(), 0);
        assertTrue(d.spokenLabel().contains(d.address()));
    }
    @Test public void localityAndStreetNamesAreNotFacilityNames() throws Exception {
        for (String key : new String[]{"place", "boundary", "highway"}) {
            JSONObject f = feature(); f.getJSONObject("properties").put("osm_key", key);
            assertFalse(PhotonSearch.parse(response(f)).get(0).hasFacilityName());
        }
        JSONObject f = feature(); f.getJSONObject("properties").put("name", "埼玉県熊谷市");
        assertFalse(PhotonSearch.parse(response(f)).get(0).hasFacilityName());
    }
    @Test public void missingAllAddressFieldsIsStillUnusable() throws Exception {
        JSONObject f = feature();
        f.getJSONObject("properties").remove("state"); f.getJSONObject("properties").remove("city");
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
    }
    @Test public void namedBusStopsRemainIdentifiable() throws Exception {
        JSONObject f = feature();
        f.getJSONObject("properties").put("name", "中央公園前").put("osm_key", "highway").put("osm_value", "bus_stop");
        Destination d = PhotonSearch.parse(response(f)).get(0);
        assertTrue(d.hasFacilityName()); assertEquals("中央公園前", d.name());
    }
    @Test public void actualAlternateShopResultIsNeverRenamedToRequestedShop() throws Exception {
        // Public Photon response for モトラッド羽田 on 2026-10-24; provider can return a different shop.
        JSONObject json = new JSONObject("""
                {"features":[{"properties":{"osm_key":"shop","osm_value":"motorcycle","type":"house",
                "name":"モトラッド柏","city":"流山市","state":"千葉県","countrycode":"JP"},
                "geometry":{"type":"Point","coordinates":[139.9400459,35.8364078]}}]}
                """);
        Destination d = CandidateRanking.select(PhotonSearch.parse(json), null, "モトラッド羽田").get(0);
        assertEquals("モトラッド柏", d.name()); assertEquals("千葉県流山市", d.address());
        assertEquals(35.8364078, d.latitude(), 0); assertEquals(139.9400459, d.longitude(), 0);
    }
}
