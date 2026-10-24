package jp.touringhub.assistant;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Small, user-triggered searches only; the free public demo has no availability guarantee. */
final class PhotonSearch implements AutoCloseable {
    private final Handler handler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> pending;
    private int generation;
    private long lastRequestAt = -1000;
    private long blockedUntil;
    PhotonSearch(Handler handler) { this.handler = handler; }
    void search(String query, CandidateRanking.Origin origin, Consumer<List<Destination>> success, Runnable failure) {
        cancel();
        long now = SystemClock.elapsedRealtime();
        if (now < blockedUntil || now - lastRequestAt < 1000) { failure.run(); return; }
        lastRequestAt = now;
        int request = generation;
        Uri.Builder builder = new Uri.Builder().scheme("https").authority("photon.komoot.io").path("api/")
                .appendQueryParameter("q", query).appendQueryParameter("limit", "10")
                .appendQueryParameter("countrycode", "JP");
        if (origin != null) {
            CandidateRanking.Origin approximate = origin.approximate();
            builder.appendQueryParameter("lat", Double.toString(approximate.latitude()))
                    .appendQueryParameter("lon", Double.toString(approximate.longitude()));
        }
        String url = builder.build().toString();
        pending = executor.submit(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "TouringHub/0.1 (personal Android destination assistant)");
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status != 200) {
                    if (status == 403 || status == 429) handler.post(() -> blockedUntil = SystemClock.elapsedRealtime() + 600000);
                    throw new java.io.IOException("Search unavailable");
                }
                byte[] body;
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] chunk = new byte[4096];
                    int count;
                    while ((count = input.read(chunk)) != -1) {
                        if (Thread.currentThread().isInterrupted() || buffer.size() + count > 262144) throw new java.io.IOException("Search cancelled or oversized");
                        buffer.write(chunk, 0, count);
                    }
                    body = buffer.toByteArray();
                }
                List<Destination> results = parse(new JSONObject(new String(body, StandardCharsets.UTF_8)));
                handler.post(() -> { if (request == generation) success.accept(results); });
            } catch (Exception ex) {
                handler.post(() -> { if (request == generation) failure.run(); });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }
    static List<Destination> parse(JSONObject json) {
        List<Destination> results = new ArrayList<>();
        JSONArray features = json.optJSONArray("features");
        if (features == null) return results;
        for (int i = 0; i < Math.min(10, features.length()); i++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null) continue;
            JSONObject properties = feature.optJSONObject("properties"), geometry = feature.optJSONObject("geometry");
            if (properties == null || geometry == null || !"JP".equalsIgnoreCase(value(properties, "countrycode"))) continue;
            JSONArray point = geometry.optJSONArray("coordinates");
            if (point == null || point.length() < 2 || !"Point".equals(value(geometry, "type"))) continue;
            String name = value(properties, "name"), state = value(properties, "state"), city = value(properties, "city");
            if (name.length() > 160) continue;
            String district = value(properties, "district");
            String county = value(properties, "county"), street = value(properties, "street");
            String house = value(properties, "housenumber");
            String locality = city.isBlank() ? county : city;
            String area = Destination.area(state, locality, district);
            String address = state + locality + district + street + house;
            // Missing city is not missing address: some POIs supply county/district
            // instead. Retain only the address fields actually returned with this point.
            if (address.isBlank() || address.length() > 500) continue;
            String facility = ProviderNames.facilityName(name, address, state, city, county, district, street, house);
            String key = value(properties, "osm_key");
            if (key.equals("place") || key.equals("boundary")
                    || (key.equals("highway") && !value(properties, "osm_value").equals("bus_stop"))) facility = "";
            try {
                double lat = point.optDouble(1, Double.NaN), lon = point.optDouble(0, Double.NaN);
                results.add(facility.isEmpty() ? Destination.addressCandidate(address, lat, lon, area)
                        : new Destination(facility, address, lat, lon, area));
            }
            catch (IllegalArgumentException ignored) { /* Invalid source coordinates are unusable. */ }
        }
        return List.copyOf(results);
    }
    private static String value(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "").trim();
    }
    void cancel() {
        ++generation;
        if (pending != null) { pending.cancel(true); pending = null; }
    }
    @Override public void close() { cancel(); executor.shutdownNow(); }
}
