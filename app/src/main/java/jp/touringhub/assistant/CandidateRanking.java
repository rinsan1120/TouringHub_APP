package jp.touringhub.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CandidateRanking {
    record Origin(double latitude, double longitude) {
        Origin {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                throw new IllegalArgumentException("Invalid origin");
            }
        }
        double distanceTo(Destination destination) {
            double lat1 = Math.toRadians(latitude), lat2 = Math.toRadians(destination.latitude());
            double dlat = lat2 - lat1, dlon = Math.toRadians(destination.longitude() - longitude);
            double a = Math.pow(Math.sin(dlat / 2), 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
            return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
        }
        Origin approximate() {
            // About 2 km grid spacing. Never forward a precise sensor fix to search services.
            return new Origin(Math.round(latitude * 50) / 50.0, Math.round(longitude * 50) / 50.0);
        }
    }
    private CandidateRanking() {}
    static List<Destination> select(List<Destination> results, Origin origin) {
        Map<String, Destination> unique = new LinkedHashMap<>();
        for (Destination destination : results) {
            String key = JapaneseInput.normalize(destination.name()) + "|"
                    + String.format(Locale.ROOT, "%.5f,%.5f", destination.latitude(), destination.longitude());
            unique.putIfAbsent(key, destination);
        }
        List<Destination> ranked = new ArrayList<>(unique.values());
        if (origin != null) ranked.sort(Comparator.comparingDouble(origin::distanceTo));
        return List.copyOf(ranked.subList(0, Math.min(Conversation.MAX_CANDIDATES, ranked.size())));
    }
}
