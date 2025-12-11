package app.da;

import java.util.Set;

import static app.bl.Constants.TAIWAN_CITIES;
import static app.bl.Constants.normalizeCity;

/**
 * LocationRecognizer v2.0 - 重構版（使用統一常數）
 */
public class LocationRecognizer {

    /**
     * 正規化地名
     */
    public static String normalize(String rawCity) {
        if (rawCity == null || rawCity.isEmpty()) {
            return "其他";
        }
        String result = normalizeCity(rawCity);
        return (result != null) ? result : "其他";
    }

    /**
     * 從文字中提取城市
     */
    public static String extractCity(String text) {
        return app.bl.Constants.extractCity(text);
    }

    /**
     * 取得所有城市
     */
    public static Set<String> getAllCities() {
        return TAIWAN_CITIES;
    }
}