package jp.touringhub.assistant;

/** Uses provider fields only. A search query is never evidence of a facility name. */
final class ProviderNames {
    private ProviderNames() {}

    static String facilityName(String value, String address, String... addressParts) {
        if (value == null || value.isBlank() || value.length() > 160) return "";
        String name = JapaneseInput.normalize(value);
        if (name.equals(JapaneseInput.normalize(address))) return "";
        // Geocoders can return a house number, street, locality, or a combination
        // of address components in featureName rather than a facility name.
        String remainder = name;
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String part : addressParts) {
            if (part != null && !part.isBlank()) parts.add(JapaneseInput.normalize(part));
        }
        parts.sort(java.util.Comparator.comparingInt(String::length).reversed());
        for (String part : parts) remainder = remainder.replace(part, "");
        if (remainder.matches("[0-9\\p{P}\\sー丁目番地号〒]*")) return "";
        return value.trim();
    }

    static Destination geocoded(String feature, String premises, String address,
                               double latitude, double longitude, String area, String... parts) {
        String name = facilityName(feature, address, parts);
        if (name.isEmpty()) name = facilityName(premises, address, parts);
        return name.isEmpty() ? Destination.addressCandidate(address, latitude, longitude, area)
                : new Destination(name, address, latitude, longitude, area);
    }
}
