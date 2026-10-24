package jp.touringhub.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CandidateRanking {
    private static final double MIN_SEPARATION_METERS = 1000;
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
        return select(results, origin, "");
    }
    static List<Destination> select(List<Destination> results, Origin origin, String query) {
        Map<String, Destination> unique = new LinkedHashMap<>();
        for (Destination destination : results) {
            String key = JapaneseInput.normalize(destination.name()) + "|"
                    + String.format(Locale.ROOT, "%.5f,%.5f", destination.latitude(), destination.longitude());
            unique.putIfAbsent(key, destination);
        }
        List<Destination> ranked = new ArrayList<>(unique.values());
        if (origin != null) ranked.sort(Comparator.comparingDouble(origin::distanceTo));
        String requestedName = JapaneseInput.normalize(query);
        List<Destination> selected = new ArrayList<>();
        for (Destination candidate : ranked) {
            if (isNearbyAny(candidate, selected)) continue;
            Destination representative = candidate;
            double bestMatch = nameSimilarity(requestedName, candidate.name());
            // Keep the existing geographic/provider order between distinct places.
            // Only promote a better name within this candidate's direct 1 km radius.
            for (Destination neighbor : ranked) {
                if (!isNearby(candidate, neighbor) || isNearbyAny(neighbor, selected)) continue;
                double match = nameSimilarity(requestedName, neighbor.name());
                if (match > bestMatch) {
                    representative = neighbor;
                    bestMatch = match;
                }
            }
            selected.add(representative);
            if (selected.size() == Conversation.MAX_CANDIDATES) break;
        }
        return List.copyOf(selected);
    }
    private static boolean isNearby(Destination first, Destination second) {
        return new Origin(first.latitude(), first.longitude()).distanceTo(second) < MIN_SEPARATION_METERS;
    }
    private static boolean isNearbyAny(Destination candidate, List<Destination> accepted) {
        for (Destination destination : accepted) if (isNearby(destination, candidate)) return true;
        return false;
    }
    private static double nameSimilarity(String requested, String name) {
        String normalized = JapaneseInput.normalize(name);
        if (requested.isEmpty() || normalized.isEmpty()) return 0;
        if (requested.equals(normalized)) return 1;
        // Normalized edit similarity: appended shop names lose to the requested
        // facility itself. Ties retain the pre-existing geographic/provider order.
        int[] previous = new int[normalized.length() + 1];
        for (int j = 0; j < previous.length; j++) previous[j] = j;
        for (int i = 1; i <= requested.length(); i++) {
            int[] current = new int[normalized.length() + 1];
            current[0] = i;
            for (int j = 1; j <= normalized.length(); j++) {
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (requested.charAt(i - 1) == normalized.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return 1.0 - (double) previous[normalized.length()] / Math.max(requested.length(), normalized.length());
    }
}
