package jp.touringhub.assistant;

public record Destination(String name, String address, double latitude, double longitude, String area) {
    public Destination(String name, String address, double latitude, double longitude) {
        this(name, address, latitude, longitude, "");
    }
    public Destination {
        if (name == null || name.isBlank() || address == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid destination");
        }
        area = area == null ? "" : area.trim();
    }
    public String spokenLabel() {
        return address.isBlank() || address.equals(name) ? name : name + "、" + address;
    }
    public String coordinates() {
        return Double.toString(latitude) + "," + Double.toString(longitude);
    }
    public String candidateLabel() {
        return area.isBlank() ? spokenLabel() : area + "、" + name;
    }
    public String confirmationLabel() {
        if (!area.isBlank()) {
            if (name.contains(area)) return name;
            if (area.endsWith(name)) return area;
            return area + "の" + name;
        }
        // If structured locality data is missing, retain the address rather than guess
        // where a municipality name ends (e.g. 四日市市, 市川市).
        String fallback = address.replaceFirst("^日本[、,\\s]*", "")
                .replaceFirst("^〒?\\d{3}[-ー]\\d{4}[、,\\s]*", "").trim();
        return fallback.isBlank() || fallback.equals(name) ? name : name + "、" + fallback;
    }
    static String area(String prefecture, String locality, String subLocality) {
        String region = prefecture == null ? "" : prefecture.trim();
        String city = locality == null ? "" : locality.trim();
        // 郡 is redundant in the short spoken confirmation; the original address is intact.
        city = city.replaceFirst("^[^市区町村]*郡", "");
        if (!city.startsWith(region)) region += city;
        else region = city;
        if (subLocality != null && subLocality.endsWith("区") && !region.endsWith(subLocality)) {
            region += subLocality;
        }
        return region;
    }
}
