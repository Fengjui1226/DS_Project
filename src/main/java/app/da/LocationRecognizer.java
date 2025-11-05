package app.da;

public class LocationRecognizer {
    public static String normalize(String rawCity) {
        // TODO: 簡單正規化地名
        if (rawCity.contains("台北")) return "台北";
        if (rawCity.contains("台中")) return "台中";
        if (rawCity.contains("高雄")) return "高雄";
        return "其他";
    }
}