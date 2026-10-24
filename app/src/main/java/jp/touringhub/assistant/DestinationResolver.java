package jp.touringhub.assistant;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Combines device geocoding with the owner-approved, free Photon demo search. */
public final class DestinationResolver implements AutoCloseable {
    public interface Callback {
        void success(List<Destination> destinations);
        void failure();
    }
    private final Geocoder geocoder;
    private record Cached(List<Destination> results, long savedAt) {}
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PhotonSearch photon = new PhotonSearch(handler);
    private int generation;
    private Runnable timeout;
    public DestinationResolver(Context context) { geocoder = new Geocoder(context, Locale.JAPAN); }
    public void search(String query, CandidateRanking.Origin origin, Callback callback) {
        cancel();
        String key = query + (origin == null ? "|global" : String.format(Locale.ROOT, "|%.2f,%.2f", origin.latitude(), origin.longitude()));
        Cached cached = cache.get(key);
        if (cached != null && SystemClock.elapsedRealtime() - cached.savedAt() < 600000) {
            callback.success(CandidateRanking.select(cached.results(), origin, query)); return;
        }
        int request = generation;
        List<Destination> collected = new ArrayList<>();
        boolean[] succeeded = {false};
        boolean[] finished = {false};
        int[] remaining = {(Geocoder.isPresent() ? (origin == null ? 1 : 2) : 0) + 1};
        Runnable finish = () -> {
            if (finished[0] || generation != request) return;
            finished[0] = true;
            handler.removeCallbacks(timeout);
            if (succeeded[0]) {
                if (cache.size() >= 30) cache.remove(cache.keySet().iterator().next());
                if (!collected.isEmpty()) cache.put(key, new Cached(List.copyOf(collected), SystemClock.elapsedRealtime()));
                callback.success(CandidateRanking.select(collected, origin, query));
            } else callback.failure();
        };
        timeout = finish;
        handler.postDelayed(finish, 12000);
        photon.search(query, origin, results -> {
            if (finished[0] || generation != request) return;
            succeeded[0] = true;
            collected.addAll(results);
            if (--remaining[0] == 0) finish.run();
        }, () -> {
            if (!finished[0] && generation == request && --remaining[0] == 0) finish.run();
        });
        if (!Geocoder.isPresent()) return;
        Geocoder.GeocodeListener listener = new Geocoder.GeocodeListener() {
            @Override public void onGeocode(List<Address> addresses) {
                List<Destination> results = new ArrayList<>();
                for (Address address : addresses) {
                    if (!address.hasLatitude() || !address.hasLongitude()) continue;
                    String line = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : "";
                    if (line == null || line.isBlank()) {
                        StringBuilder fallback = new StringBuilder();
                        for (String part : new String[]{address.getAdminArea(), address.getLocality(), address.getSubLocality(), address.getThoroughfare(), address.getSubThoroughfare()}) {
                            if (part != null) fallback.append(part);
                        }
                        line = fallback.toString();
                    }
                    // Never confirm a location with no address to distinguish similarly named facilities.
                    if (line.isBlank()) continue;
                    String area = Destination.area(address.getAdminArea(), address.getLocality(), address.getSubLocality());
                    Destination destination;
                    try {
                        destination = ProviderNames.geocoded(address.getFeatureName(), address.getPremises(), line,
                                address.getLatitude(), address.getLongitude(), area, address.getCountryName(),
                                address.getAdminArea(), address.getSubAdminArea(), address.getLocality(),
                                address.getSubLocality(), address.getThoroughfare(), address.getSubThoroughfare(),
                                address.getPostalCode());
                    } catch (IllegalArgumentException ignored) { continue; }
                    if (!results.contains(destination)) results.add(destination);
                }
                handler.post(() -> {
                    if (finished[0] || generation != request) return;
                    succeeded[0] = true;
                    collected.addAll(results);
                    if (--remaining[0] == 0) finish.run();
                });
            }
            @Override public void onError(String message) {
                handler.post(() -> {
                    if (!finished[0] && generation == request && --remaining[0] == 0) finish.run();
                });
            }
        };
        try { geocoder.getFromLocationName(query, 5, listener); }
        catch (RuntimeException ex) { listener.onError(""); }
        if (origin != null) {
            // Bounding box biases one search to roughly 150 km around the user, while
            // the unrestricted search retains explicit destinations farther away.
            double latSpan = 1.35;
            double lonSpan = Math.min(180, latSpan / Math.max(0.01, Math.cos(Math.toRadians(origin.latitude()))));
            try {
                geocoder.getFromLocationName(query, 5, Math.max(-90, origin.latitude() - latSpan),
                        Math.max(-180, origin.longitude() - lonSpan), Math.min(90, origin.latitude() + latSpan),
                        Math.min(180, origin.longitude() + lonSpan), listener);
            } catch (RuntimeException ex) { listener.onError(""); }
        }
    }
    public void cancel() {
        ++generation;
        if (timeout != null) handler.removeCallbacks(timeout);
        photon.cancel();
    }
    @Override public void close() { cancel(); photon.close(); }
}
