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
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
        f = feature(); f.getJSONObject("properties").remove("city");
        assertTrue(PhotonSearch.parse(response(f)).isEmpty());
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
}
