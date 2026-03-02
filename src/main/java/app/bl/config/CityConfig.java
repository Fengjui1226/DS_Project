package app.bl.config;

import java.util.Map;
import java.util.Set;

/**
 * CityConfig - 城市與地點相關常數
 * 
 * 包含：
 * - 城市別名對照
 * - 台灣縣市列表
 * - 場館城市對應
 * - 台灣地標
 * - 行政區座標
 */
public final class CityConfig {

    private CityConfig() {}

    // ==================== 城市別名 ====================
    public static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), 
        Map.entry("taipei", "台北"), Map.entry("台北市", "台北"),
        Map.entry("新北", "新北"), Map.entry("新北市", "新北"), Map.entry("板橋", "新北"),
        Map.entry("桃園", "桃園"), Map.entry("中壢", "桃園"), Map.entry("taoyuan", "桃園"),
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), 
        Map.entry("taichung", "台中"), Map.entry("西屯", "台中"), Map.entry("南屯", "台中"),
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"), Map.entry("tainan", "台南"),
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹"), Map.entry("苗栗", "苗栗"),
        Map.entry("彰化", "彰化"), Map.entry("南投", "南投"), Map.entry("雲林", "雲林"),
        Map.entry("嘉義", "嘉義"), Map.entry("屏東", "屏東"), Map.entry("宜蘭", "宜蘭"),
        Map.entry("花蓮", "花蓮"), Map.entry("台東", "台東"), Map.entry("臺東", "台東"),
        Map.entry("澎湖", "澎湖"), Map.entry("金門", "金門"), Map.entry("馬祖", "馬祖"),
        Map.entry("連江", "馬祖")
    );

    // ==================== 台灣縣市 ====================
    public static final Set<String> TAIWAN_CITIES = Set.of(
        "台北", "新北", "桃園", "台中", "台南", "高雄", "基隆", "新竹",
        "苗栗", "彰化", "南投", "雲林", "嘉義", "屏東", "宜蘭", "花蓮", "台東",
        "澎湖", "金門", "馬祖"
    );

    // ==================== 場館城市對應 ====================
    public static final Map<String, String> VENUE_CITY = Map.ofEntries(
        Map.entry("華山1914", "台北"), Map.entry("松山文創", "台北"), Map.entry("松菸", "台北"),
        Map.entry("台北小巨蛋", "台北"), Map.entry("台北大巨蛋", "台北"), Map.entry("南港展覽館", "台北"),
        Map.entry("北流", "台北"), Map.entry("兩廳院", "台北"), Map.entry("國家音樂廳", "台北"),
        Map.entry("世貿", "台北"), Map.entry("花博公園", "台北"),
        Map.entry("駁二", "高雄"), Map.entry("衛武營", "高雄"), Map.entry("高流", "高雄"),
        Map.entry("台中國家歌劇院", "台中"), Map.entry("科博館", "台中"),
        Map.entry("台南美術館", "台南"), Map.entry("奇美博物館", "台南"),
        Map.entry("蘭陽博物館", "宜蘭"), Map.entry("傳藝中心", "宜蘭")
    );

    // ==================== 台灣地標 ====================
    public static final Set<String> TAIWAN_LANDMARKS = Set.of(
        // 台北
        "華山", "松菸", "小巨蛋", "大巨蛋", "信義區", "西門町", "中正紀念堂", "國父紀念館",
        "兩廳院", "南港展覽館", "世貿", "北流", "北美館", "當代館", "大稻埕", "迪化街",
        "圓山", "花博", "寶藏巖", "美麗華", "三創", "光華", "公館", "師大", "赤峰街",
        // 新北
        "耶誕城", "板橋車站", "碧潭", "淡水老街", "漁人碼頭", "九份", "平溪", "十分",
        "新月橋", "435藝文特區", "鶯歌", "三峽老街",
        // 台中
        "勤美", "草悟道", "審計新村", "歌劇院", "秋紅谷", "科博館", "逢甲", "一中街",
        "光復新村", "綠園道", "帝國製糖廠", "高美濕地", "東海大學",
        // 台南
        "藍晒圖", "奇美博物館", "南美館", "漁光島", "神農街", "河樂廣場", "安平古堡",
        "赤崁樓", "國華街", "正興街", "十鼓",
        // 高雄
        "駁二", "衛武營", "高流", "愛河", "西子灣", "棧貳庫", "巨蛋", "蓮池潭", "大東", "旗津",
        // 其他
        "鐵花村", "檜意森活村", "傳藝中心", "蘭陽博物館", "羅東林業文化園區", "勝利星村"
    );

    // ==================== 行政區座標 ====================
    public static final Map<String, double[]> DISTRICT_COORDS = Map.ofEntries(
        Map.entry("信義區", new double[]{25.0328, 121.5680}),
        Map.entry("大安區", new double[]{25.0260, 121.5430}),
        Map.entry("中正區", new double[]{25.0326, 121.5198}),
        Map.entry("松山區", new double[]{25.0571, 121.5570}),
        Map.entry("萬華區", new double[]{25.0260, 121.4973}),
        Map.entry("內湖區", new double[]{25.0830, 121.5750}),
        Map.entry("士林區", new double[]{25.0928, 121.5246}),
        Map.entry("文山區", new double[]{24.9889, 121.5703})
    );

    // ==================== 17縣市中心座標 ====================
    public static final Map<String, double[]> CITY_COORDS = Map.ofEntries(
        Map.entry("台北", new double[]{25.0330, 121.5654}),
        Map.entry("新北", new double[]{24.9936, 121.4617}),
        Map.entry("桃園", new double[]{24.9936, 121.3010}),
        Map.entry("台中", new double[]{24.1477, 120.6736}),
        Map.entry("台南", new double[]{22.9999, 120.2269}),
        Map.entry("高雄", new double[]{22.6273, 120.3014}),
        Map.entry("基隆", new double[]{25.1276, 121.7392}),
        Map.entry("新竹", new double[]{24.8138, 120.9675}),
        Map.entry("苗栗", new double[]{24.5602, 120.8214}),
        Map.entry("彰化", new double[]{24.0796, 120.5362}),
        Map.entry("南投", new double[]{23.9609, 120.9718}),
        Map.entry("雲林", new double[]{23.7092, 120.4313}),
        Map.entry("嘉義", new double[]{23.4801, 120.4491}),
        Map.entry("屏東", new double[]{22.6726, 120.4871}),
        Map.entry("宜蘭", new double[]{24.7021, 121.7377}),
        Map.entry("花蓮", new double[]{23.9872, 121.6016}),
        Map.entry("台東", new double[]{22.7583, 121.1444})
    );

    // ==================== 工具方法 ====================

    /**
     * 正規化城市名稱
     */
    public static String normalize(String city) {
        if (city == null) return null;
        String c = city.trim();
        if (CITY_ALIASES.containsKey(c)) return CITY_ALIASES.get(c);
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (c.contains(e.getKey())) return e.getValue();
        }
        return c;
    }

    /**
     * 從文字中提取城市
     */
    public static String extract(String text) {
        if (text == null) return null;
        // 先檢查場館
        for (Map.Entry<String, String> e : VENUE_CITY.entrySet()) {
            if (text.contains(e.getKey())) return e.getValue();
        }
        // 再檢查城市名
        for (String c : TAIWAN_CITIES) {
            if (text.contains(c)) return c;
        }
        return null;
    }

    /**
     * 從文字中提取行政區
     */
    public static String extractDistrict(String text) {
        if (text == null) return null;
        for (String district : DISTRICT_COORDS.keySet()) {
            if (text.contains(district)) return district;
        }
        return null;
    }

    /**
     * 檢查是否包含台灣地點
     */
    public static boolean hasTaiwanLocation(String text) {
        if (text == null) return false;
        if (text.contains("台灣") || text.contains("臺灣")) return true;
        if (extract(text) != null) return true;
        for (String landmark : TAIWAN_LANDMARKS) {
            if (text.contains(landmark)) return true;
        }
        return false;
    }

    /**
     * 根據場館名稱取得城市
     */
    public static String getCityByVenue(String venue) {
        return VENUE_CITY.get(venue);
    }

    /**
     * Haversine 距離（公里）
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2.0 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    /**
     * 從 GPS 座標找最近縣市
     */
    public static String nearestCity(double lat, double lng) {
        String nearest = "台北";
        double minDist = Double.MAX_VALUE;
        for (Map.Entry<String, double[]> e : CITY_COORDS.entrySet()) {
            double dist = haversineKm(lat, lng, e.getValue()[0], e.getValue()[1]);
            if (dist < minDist) {
                minDist = dist;
                nearest = e.getKey();
            }
        }
        return nearest;
    }
}