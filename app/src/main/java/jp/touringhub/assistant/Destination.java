package jp.touringhub.assistant;

public record Destination(String name, String address, double latitude, double longitude) {
    public Destination {
        if (name == null || name.isBlank() || address == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid destination");
        }
    }
    public String spokenLabel() {
        return address.isBlank() || address.equals(name) ? name : name + "、" + address;
    }
    public String coordinates() {
        return Double.toString(latitude) + "," + Double.toString(longitude);
    }
}
