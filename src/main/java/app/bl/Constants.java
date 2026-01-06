package app.bl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import app.bl.config.CityConfig;
import app.bl.config.EventTypeConfig;
import app.bl.config.FilterConfig;

/**
 * Constants v5.0 - 向後相容 Facade
 * 
 * 此類別保持向後相容，實際實作已移至：
 * - CityConfig：城市與地點相關
 * - EventTypeConfig：活動類型相關
 * - FilterConfig：過濾規則相關
 * 
 * 設計模式：Facade Pattern
 * 重構手法：Extract Class + Preserve Backward Compatibility
 */
public final class Constants {

    private Constants() {}

    // ==================== 委派給 CityConfig ====================
    public static final Map<String, String> CITY_ALIASES = CityConfig.CITY_ALIASES;
    public static final Set<String> TAIWAN_CITIES = CityConfig.TAIWAN_CITIES;
    public static final Map<String, String> VENUE_CITY = CityConfig.VENUE_CITY;
    public static final Set<String> TAIWAN_LANDMARKS = CityConfig.TAIWAN_LANDMARKS;
    public static final Map<String, double[]> DISTRICT_COORDS = CityConfig.DISTRICT_COORDS;

    // ==================== 委派給 EventTypeConfig ====================
    public static final Set<String> EVENT_TYPES = EventTypeConfig.EVENT_TYPES;
    public static final List<String> EVENT_TERMS = EventTypeConfig.EVENT_TERMS;
    public static final Map<String, Double> EVENT_TYPE_BOOST = EventTypeConfig.EVENT_TYPE_BOOST;
    public static final Map<String, String> EVENT_TYPE_KEYWORDS = EventTypeConfig.EVENT_TYPE_KEYWORDS;
    public static final Map<String, List<String>> SYNONYMS = EventTypeConfig.SYNONYMS;
    public static final Map<String, List<String>> CATEGORY_EXPANSIONS = EventTypeConfig.CATEGORY_EXPANSIONS;

    // ==================== 委派給 FilterConfig ====================
    public static final Set<String> NOISE_KEYWORDS = FilterConfig.NOISE_KEYWORDS;
    public static final Set<String> FOREIGN_KEYWORDS_CORE = FilterConfig.FOREIGN_KEYWORDS;
    public static final Set<String> EXCLUDED_DOMAINS = FilterConfig.EXCLUDED_DOMAINS;
    public static final Set<String> SOCIAL_DOMAINS = FilterConfig.SOCIAL_DOMAINS;
    public static final Set<String> APPLICATION_KEYWORDS = FilterConfig.APPLICATION_KEYWORDS;
    public static final Set<String> PROPER_NOUNS = FilterConfig.PROPER_NOUNS;
    public static final Set<String> AUTHORITY_DOMAINS = FilterConfig.AUTHORITY_DOMAINS;

    // ==================== 其他常數 ====================
    public static final Map<String, Integer> WEEKDAY_MAP = Map.ofEntries(
        Map.entry("週一", 1), Map.entry("週二", 2), Map.entry("週三", 3),
        Map.entry("週四", 4), Map.entry("週五", 5), Map.entry("週六", 6), Map.entry("週日", 7),
        Map.entry("星期一", 1), Map.entry("星期二", 2), Map.entry("星期三", 3),
        Map.entry("星期四", 4), Map.entry("星期五", 5), Map.entry("星期六", 6), Map.entry("星期日", 7)
    );

    // ==================== 工具方法（委派）====================

    public static String normalizeCity(String city) {
        return CityConfig.normalize(city);
    }

    public static String extractCity(String text) {
        return CityConfig.extract(text);
    }

    public static String extractDistrict(String text) {
        return CityConfig.extractDistrict(text);
    }

    public static boolean hasTaiwanLocation(String text) {
        return CityConfig.hasTaiwanLocation(text);
    }

    public static String getCityByVenue(String venue) {
        return CityConfig.getCityByVenue(venue);
    }

    public static boolean isLikelyForeign(String text) {
        if (text == null) return false;
        if (CityConfig.hasTaiwanLocation(text)) return false;
        return FilterConfig.isForeign(text);
    }
}