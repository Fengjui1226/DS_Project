package app.bl;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Constants v2.2 - 完整版 (修復編譯錯誤)
 */
public final class Constants {

    private Constants() {} // 防止實例化

    // ==================== 城市相關 ====================
    
    public static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"), Map.entry("台北市", "台北"),
        Map.entry("新北", "新北"), Map.entry("新北市", "新北"), Map.entry("板橋", "新北"),
        Map.entry("桃園", "桃園"), Map.entry("中壢", "桃園"), Map.entry("taoyuan", "桃園"),
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"), Map.entry("西屯", "台中"), Map.entry("南屯", "台中"),
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"), Map.entry("tainan", "台南"),
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹"), Map.entry("苗栗", "苗栗"),
        Map.entry("彰化", "彰化"), Map.entry("南投", "南投"), Map.entry("雲林", "雲林"),
        Map.entry("嘉義", "嘉義"), Map.entry("屏東", "屏東"), Map.entry("宜蘭", "宜蘭"),
        Map.entry("花蓮", "花蓮"), Map.entry("台東", "台東"), Map.entry("臺東", "台東"),
        Map.entry("澎湖", "澎湖"), Map.entry("金門", "金門"), Map.entry("馬祖", "馬祖"),
        Map.entry("連江", "馬祖")
    );

    public static final Set<String> TAIWAN_CITIES = Set.of(
        "台北", "新北", "桃園", "台中", "台南", "高雄", "基隆", "新竹",
        "苗栗", "彰化", "南投", "雲林", "嘉義", "屏東", "宜蘭", "花蓮", "台東",
        "澎湖", "金門", "馬祖"
    );

    // ==================== 場館相關 ====================
    
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
    
    public static final Set<String> TAIWAN_LANDMARKS = Set.of(
        "華山", "松菸", "駁二", "衛武營", "小巨蛋", "大巨蛋", "信義區", "西門町", "草悟道", "愛河"
    );

    // ==================== 關鍵字擴充 ====================

    public static final Set<String> AUTHORITY_DOMAINS = Set.of(
        "accupass.com", "opentix.life", "tixcraft.com", "kktix.com",
        "indievox.com", "ticket.com.tw", "udnfunlife.com", "kham.com.tw",
        "ticket.ibon.com.tw", "famiport.com.tw"
    );

    public static final Set<String> NOISE_KEYWORDS = Set.of(
        "決算書", "預算書", "財報", "報表", "公告", "標案", "決標", "開標", "會議記錄", "公報",
        "有線電視", "第四台", "寬頻", "光纖", "維修", "客服", "安裝", "費率", "繳費",
        "徵才", "職缺", "招募", "求職", "人力銀行", "工讀", "實習",
        "新聞網", "日報", "快訊", "社論", "專欄", "懶人包"
    );

    public static final Set<String> FOREIGN_KEYWORDS_CORE = Set.of(
        "東京", "大阪", "京都", "北海道", "首爾", "釜山", "曼谷", "機票", "入境", "簽證"
    );

    // ★ QueryUnderstanding 需要這個
    public static final Set<String> EVENT_TYPES = Set.of(
        "演唱會", "音樂會", "音樂節", "展覽", "特展", "市集", "夜市",
        "講座", "工作坊", "派對", "路跑", "馬拉松", "親子", "電影",
        "舞台劇", "音樂劇", "脫口秀", "相聲", "魔術", "馬戲",
        "快閃店", "見面會", "簽書會"
    );

    public static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "快閃店", "見面會", "簽書會", "發表會", "體驗會",
        "生活節", "音樂祭", "派對", "路跑", "馬拉松",
        "festival", "concert", "exhibition", "event", "pop-up"
    );

    public static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("快閃店", 1.4),
        Map.entry("市集", 1.3), Map.entry("生活節", 1.3),
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        Map.entry("見面會", 1.3), Map.entry("簽書會", 1.2),
        Map.entry("親子", 1.2), Map.entry("免費", 1.1)
    );

    // ★ EventInfoExtractor 可能需要這個 (如果它用這個名字)
    public static final Map<String, String> EVENT_TYPE_KEYWORDS = Map.ofEntries(
        Map.entry("演唱會", "演唱會"), Map.entry("concert", "演唱會"),
        Map.entry("音樂會", "音樂會"), Map.entry("音樂節", "音樂節"),
        Map.entry("展覽", "展覽"), Map.entry("exhibition", "展覽"),
        Map.entry("市集", "市集"), Map.entry("market", "市集"),
        Map.entry("講座", "講座"), Map.entry("工作坊", "工作坊"),
        Map.entry("路跑", "路跑"), Map.entry("馬拉松", "路跑"),
        Map.entry("親子", "親子活動"), Map.entry("體驗", "親子活動")
    );

    public static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.ofEntries(
        Map.entry("市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "二手市集")),
        Map.entry("展覽", List.of("特展", "回顧展", "快閃店", "體驗展")),
        Map.entry("音樂", List.of("音樂節", "音樂祭", "live house", "聽團")),
        Map.entry("親子", List.of("親子活動", "體驗營", "繪本", "說故事")),
        Map.entry("戶外", List.of("露營", "野餐", "登山", "健行", "路跑"))
    );

    public static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
        Map.entry("演唱會", List.of("音樂會", "live", "演出")),
        Map.entry("展覽", List.of("特展", "展出", "展示")),
        Map.entry("市集", List.of("文創市集", "假日市集", "market")),
        Map.entry("音樂節", List.of("音樂祭", "festival")),
        Map.entry("週末", List.of("假日", "星期六", "星期日")),
        Map.entry("跨年", List.of("新年", "元旦"))
    );

    public static final Set<String> EXCLUDED_DOMAINS = Set.of(
        "x.com", "twitter.com", "ptt.cc", "dcard.tw", 
        "amazon.co.jp", "rakuten.co.jp", "yahoo.co.jp", 
        "booking.com", "agoda.com", "trivago.com", "hotels.com"
    );

    public static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件",
        "招標", "採購", "規定", "須知", "下載", "表單"
    );

    public static final Set<String> PROPER_NOUNS = Set.of(
        "華山", "松菸", "駁二", "小巨蛋", "大巨蛋", "兩廳院", "故宮",
        "北美館", "衛武營", "高流", "北流", "科博館", "奇美博物館",
        "簡單生活節", "大港開唱", "春浪", "太魯閣峽谷音樂節",
        "信義區", "西門町", "草悟道", "勤美", "審計新村"
    );

    public static final Map<String, Integer> WEEKDAY_MAP = Map.ofEntries(
        Map.entry("週一", 1), Map.entry("週二", 2), Map.entry("週三", 3),
        Map.entry("週四", 4), Map.entry("週五", 5), Map.entry("週六", 6), Map.entry("週日", 7),
        Map.entry("星期一", 1), Map.entry("星期二", 2), Map.entry("星期三", 3),
        Map.entry("星期四", 4), Map.entry("星期五", 5), Map.entry("星期六", 6), Map.entry("星期日", 7)
    );
    
    // ==================== 工具方法 ====================
    public static String normalizeCity(String city) {
        if (city == null) return null;
        String c = city.trim();
        if (CITY_ALIASES.containsKey(c)) return CITY_ALIASES.get(c);
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (c.contains(e.getKey())) return e.getValue();
        }
        return c;
    }

    public static String extractCity(String text) {
        if (text == null) return null;
        for (Map.Entry<String, String> e : VENUE_CITY.entrySet()) {
            if (text.contains(e.getKey())) return e.getValue();
        }
        for (String c : TAIWAN_CITIES) {
            if (text.contains(c)) return c;
        }
        return null;
    }

    public static boolean hasTaiwanLocation(String text) {
        if (text == null) return false;
        if (text.contains("台灣") || text.contains("台北")) return true;
        return extractCity(text) != null;
    }
    
    public static boolean isLikelyForeign(String text) {
        if (text == null) return false;
        if (hasTaiwanLocation(text)) return false;
        int count = 0;
        for (String k : FOREIGN_KEYWORDS_CORE) {
            if (text.contains(k)) count++;
        }
        return count >= 2;
    }
    
    public static String getCityByVenue(String venue) {
        return VENUE_CITY.get(venue);
    }
}