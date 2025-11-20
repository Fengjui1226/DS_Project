package app.da;

import java.util.*;

/**
 * LocationRecognizer - 辨識並標準化地區名稱
 */
public class LocationRecognizer {
    
    private static final Map<String, String> CITY_ALIASES = new HashMap<>();
    
    static {
        // 台北
        CITY_ALIASES.put("台北", "台北");
        CITY_ALIASES.put("臺北", "台北");
        CITY_ALIASES.put("taipei", "台北");
        CITY_ALIASES.put("台北市", "台北");
        
        // 新北
        CITY_ALIASES.put("新北", "新北");
        CITY_ALIASES.put("新北市", "新北");
        
        // 桃園
        CITY_ALIASES.put("桃園", "桃園");
        CITY_ALIASES.put("taoyuan", "桃園");
        
        // 台中
        CITY_ALIASES.put("台中", "台中");
        CITY_ALIASES.put("臺中", "台中");
        CITY_ALIASES.put("taichung", "台中");
        
        // 台南
        CITY_ALIASES.put("台南", "台南");
        CITY_ALIASES.put("臺南", "台南");
        CITY_ALIASES.put("tainan", "台南");
        
        // 高雄
        CITY_ALIASES.put("高雄", "高雄");
        CITY_ALIASES.put("kaohsiung", "高雄");
        
        // 其他縣市
        CITY_ALIASES.put("基隆", "基隆");
        CITY_ALIASES.put("新竹", "新竹");
        CITY_ALIASES.put("苗栗", "苗栗");
        CITY_ALIASES.put("彰化", "彰化");
        CITY_ALIASES.put("南投", "南投");
        CITY_ALIASES.put("雲林", "雲林");
        CITY_ALIASES.put("嘉義", "嘉義");
        CITY_ALIASES.put("屏東", "屏東");
        CITY_ALIASES.put("宜蘭", "宜蘭");
        CITY_ALIASES.put("花蓮", "花蓮");
        CITY_ALIASES.put("台東", "台東");
        CITY_ALIASES.put("臺東", "台東");
        CITY_ALIASES.put("澎湖", "澎湖");
        CITY_ALIASES.put("金門", "金門");
        CITY_ALIASES.put("馬祖", "馬祖");
    }
    
    /**
     * 正規化地名
     */
    public static String normalize(String rawCity) {
        if (rawCity == null || rawCity.isEmpty()) {
            return "其他";
        }
        
        String lower = rawCity.toLowerCase(Locale.ROOT).trim();
        
        // 直接比對
        String normalized = CITY_ALIASES.get(lower);
        if (normalized != null) {
            return normalized;
        }
        
        // 部分比對
        for (Map.Entry<String, String> entry : CITY_ALIASES.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return "其他";
    }
    
    /**
     * 從文字中提取城市
     */
    public static String extractCity(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        String lower = text.toLowerCase(Locale.ROOT);
        
        // 按長度排序，先比對較長的名稱
        List<String> keys = new ArrayList<>(CITY_ALIASES.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        
        for (String cityAlias : keys) {
            if (lower.contains(cityAlias.toLowerCase())) {
                return CITY_ALIASES.get(cityAlias);
            }
        }
        
        return null;
    }
    
    /**
     * 取得所有城市
     */
    public static Set<String> getAllCities() {
        return new HashSet<>(CITY_ALIASES.values());
    }
}