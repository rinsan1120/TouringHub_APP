package jp.touringhub.assistant;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Device geocoding backend; replace this adapter if a different search provider is needed. */
public final class DestinationResolver {
    public interface Callback {
        void success(List<Destination> destinations);
        void failure();
    }
    private final Geocoder geocoder;
    private final Map<String, List<Destination>> cache = new LinkedHashMap<>();
    public DestinationResolver(Context context) { geocoder = new Geocoder(context, Locale.JAPAN); }
    public boolean available() { return Geocoder.isPresent(); }
    public void search(String query, Callback callback) {
        if (cache.containsKey(query)) { callback.success(cache.get(query)); return; }
        geocoder.getFromLocationName(query, 5, new Geocoder.GeocodeListener() {
            @Override public void onGeocode(List<Address> addresses) {
                List<Destination> results = new ArrayList<>();
                for (Address address : addresses) {
                    if (!address.hasLatitude() || !address.hasLongitude()) continue;
                    String name = address.getFeatureName();
                    if (name == null || name.isBlank() || name.matches("[0-9０-９\\-ー丁目番地号]+")) name = query;
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
                    Destination destination = new Destination(name, line, address.getLatitude(), address.getLongitude());
                    if (!results.contains(destination)) results.add(destination);
                }
                // Backend callbacks can arrive off the UI thread. The Activity owns all cache access.
                callback.success(List.copyOf(results));
            }
            @Override public void onError(String message) { callback.failure(); }
        });
    }
    public void remember(String query, List<Destination> results) {
        if (results.isEmpty()) return;
        if (cache.size() >= 30) cache.remove(cache.keySet().iterator().next());
        cache.put(query, List.copyOf(results));
    }
}
