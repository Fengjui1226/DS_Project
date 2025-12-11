package app.bl;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Constants - 統一管理所有常數
 * 避免各檔案重複定義相同資料
 */
public final class Constants {

    private Constants() {} // 防止實例化

    // ==================== 城市相關 ====================

    /**
     * 城市別名對照表（統一標準化）
     */
    public static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        // 台北
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
        Map.entry("台北市", "台北"),
        // 新北
        Map.entry("新北", "新北"), Map.entry("新北市", "新北"), Map.entry("板橋", "新北"),
        // 桃園
        Map.entry("桃園", "桃園"), Map.entry("中壢", "桃園"), Map.entry("taoyuan", "桃園"),
        // 台中
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
        Map.entry("西屯", "台中"), Map.entry("南屯", "台中"),
        // 台南
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"), Map.entry("tainan", "台南"),
        // 高雄
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        // 其他縣市
        Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹"), Map.entry("苗栗", "苗栗"),
        Map.entry("彰化", "彰化"), Map.entry("南投", "南投"), Map.entry("雲林", "雲林"),
        Map.entry("嘉義", "嘉義"), Map.entry("屏東", "屏東"), Map.entry("宜蘭", "宜蘭"),
        Map.entry("花蓮", "花蓮"), Map.entry("台東", "台東"), Map.entry("臺東", "台東"),
        Map.entry("澎湖", "澎湖"), Map.entry("金門", "金門"), Map.entry("馬祖", "馬祖"),
        Map.entry("連江", "馬祖")
    );

    /**
     * 所有台灣城市（用於快速判斷）
     */
    public static final Set<String> TAIWAN_CITIES = Set.of(
        "台北", "新北", "桃園", "台中", "台南", "高雄",
        "基隆", "新竹", "苗栗", "彰化", "南投", "雲林",
        "嘉義", "屏東", "宜蘭", "花蓮", "台東", "澎湖", "金門", "馬祖"
    );

    // ==================== 場館相關 ====================

    /**
     * 場館 -> 城市 對照表
     */
    public static final Map<String, String> VENUE_CITY = Map.ofEntries(
        // 台北
        Map.entry("華山1914", "台北"), Map.entry("華山文創", "台北"), Map.entry("華山", "台北"),
        Map.entry("松山文創", "台北"), Map.entry("松菸", "台北"),
        Map.entry("台北小巨蛋", "台北"), Map.entry("小巨蛋", "台北"),
        Map.entry("台北大巨蛋", "台北"), Map.entry("大巨蛋", "台北"),
        Map.entry("國家音樂廳", "台北"), Map.entry("兩廳院", "台北"), Map.entry("國家戲劇院", "台北"),
        Map.entry("台北流行音樂中心", "台北"), Map.entry("北流", "台北"),
        Map.entry("台北國際會議中心", "台北"), Map.entry("TICC", "台北"),
        Map.entry("南港展覽館", "台北"), Map.entry("世貿", "台北"),
        Map.entry("北美館", "台北"), Map.entry("台北市立美術館", "台北"),
        Map.entry("當代藝術館", "台北"), Map.entry("故宮", "台北"),
        Map.entry("中山堂", "台北"), Map.entry("西門紅樓", "台北"),
        Map.entry("誠品表演廳", "台北"), Map.entry("誠品信義", "台北"),
        Map.entry("Legacy", "台北"), Map.entry("The Wall", "台北"),
        Map.entry("國父紀念館", "台北"), Map.entry("中正紀念堂", "台北"),
        // 新北
        Map.entry("Zepp New Taipei", "新北"),
        // 台中
        Map.entry("台中國家歌劇院", "台中"), Map.entry("歌劇院", "台中"),
        Map.entry("台中巨蛋", "台中"),
        Map.entry("科博館", "台中"), Map.entry("國立自然科學博物館", "台中"),
        Map.entry("草悟道", "台中"), Map.entry("勤美術館", "台中"), Map.entry("勤美", "台中"),
        Map.entry("審計新村", "台中"), Map.entry("光復新村", "台中"),
        Map.entry("逢甲", "台中"), Map.entry("一中街", "台中"), Map.entry("東海", "台中"),
        Map.entry("秋紅谷", "台中"), Map.entry("美術館", "台中"), Map.entry("綠園道", "台中"),
        // 高雄
        Map.entry("駁二藝術特區", "高雄"), Map.entry("駁二", "高雄"),
        Map.entry("高雄流行音樂中心", "高雄"), Map.entry("高流", "高雄"),
        Map.entry("高雄巨蛋", "高雄"), Map.entry("高雄展覽館", "高雄"),
        Map.entry("衛武營", "高雄"), Map.entry("衛武營國家藝術文化中心", "高雄"),
        Map.entry("科工館", "高雄"), Map.entry("愛河", "高雄"),
        // 台南
        Map.entry("台南文化中心", "台南"), Map.entry("台南美術館", "台南"),
        Map.entry("藍晒圖", "台南"), Map.entry("十鼓文創", "台南"),
        // 其他
        Map.entry("海生館", "屏東"), Map.entry("國立海洋生物博物館", "屏東"),
        Map.entry("花蓮文創", "花蓮"),
        Map.entry("宜蘭傳藝", "宜蘭"), Map.entry("傳藝中心", "宜蘭")
    );

    /**
     * 台灣地標（用於判斷是否為台灣活動）
     */
    public static final Set<String> TAIWAN_LANDMARKS = Set.of(
        "華山", "松菸", "駁二", "高流", "北流", "小巨蛋", "大巨蛋", "兩廳院", "國家音樂廳",
        "故宮", "北美館", "當代藝術館", "科博館", "海生館", "衛武營", "信義區", "西門町",
        "忠孝", "中山", "士林", "淡水", "九份", "平溪", "陽明山", "墾丁", "日月潭",
        "阿里山", "太魯閣", "清境", "草悟道", "勤美", "逢甲", "一中街", "東海", "秋紅谷",
        "審計新村", "光復新村", "歌劇院", "美術館", "綠園道", "愛河",
        "新光三越", "sogo", "遠百", "誠品", "國父紀念館", "中正紀念堂"
    );

    // ==================== 海外關鍵字 ====================

    /**
     * 海外地名關鍵字（用於過濾非台灣活動）
     */
    public static final Set<String> FOREIGN_KEYWORDS = Set.of(
        // 日本
        "日本", "東京", "大阪", "北海道", "沖繩", "京都", "奈良", "名古屋", "福岡", "橫濱",
        "涉谷", "新宿", "銀座", "池袋", "原宿", "淺草", "秋葉原", "神戶", "廣島", "仙台",
        "金澤", "札幌", "函館", "輕井澤", "富士山", "鎌倉", "箱根", "河口湖",
        // 韓國
        "首爾", "釜山", "韓國", "濟州", "仁川", "明洞", "弘大", "江南", "東大門",
        // 中港澳
        "香港", "澳門", "上海", "北京", "深圳", "廣州", "成都", "杭州", "蘇州", "西安",
        // 東南亞
        "曼谷", "泰國", "新加坡", "馬來西亞", "越南", "峇里島", "印尼", "菲律賓", "吉隆坡",
        "清邁", "普吉島", "河內", "胡志明", "柬埔寨", "寮國", "緬甸",
        // 紐澳
        "紐西蘭", "新西蘭", "奧克蘭", "威靈頓", "基督城", "皇后鎮", "澳洲", "澳大利亞",
        "雪梨", "悉尼", "墨爾本", "布里斯本", "伯斯", "黃金海岸",
        // 歐美
        "美國", "歐洲", "倫敦", "巴黎", "紐約", "洛杉磯", "舊金山", "芝加哥", "西雅圖",
        "德國", "法國", "義大利", "西班牙", "荷蘭", "瑞士", "奧地利", "捷克",
        "羅馬", "米蘭", "佛羅倫斯", "巴塞隆納", "阿姆斯特丹", "維也納", "布拉格",
        // 旅遊相關
        "機票", "自由行", "入境", "簽證", "匯率", "代購", "旅遊攻略", "行程規劃",
        "海外", "出國", "國外", "境外", "跨境", "航班"
    );

    /**
     * 海外關鍵字精簡版（用於快速懲罰判斷）
     */
    public static final Set<String> FOREIGN_KEYWORDS_CORE = Set.of(
        "東京", "大阪", "京都", "北海道", "首爾", "釜山", "曼谷", "機票", "入境", "簽證"
    );

    // ==================== 活動類型 ====================

    /**
     * 活動類型關鍵字
     */
    public static final Set<String> EVENT_TYPES = Set.of(
        "演唱會", "音樂會", "音樂節", "展覽", "特展", "市集", "夜市",
        "講座", "工作坊", "派對", "路跑", "馬拉松", "親子", "電影",
        "舞台劇", "音樂劇", "脫口秀", "相聲", "魔術", "馬戲"
    );

    /**
     * 活動類型搜尋詞
     */
    public static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "festival", "concert", "exhibition", "event",
        "表演", "藝術", "體驗", "親子", "戶外", "講座"
    );

    /**
     * 活動類型評分加成
     */
    public static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("個展", 1.2),
        Map.entry("市集", 1.3), Map.entry("夜市", 1.2),
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        Map.entry("派對", 1.2), Map.entry("路跑", 1.3),
        Map.entry("親子", 1.2), Map.entry("免費", 1.1)
    );

    /**
     * 活動類型關鍵字 -> 標準類型
     */
    public static final Map<String, String> EVENT_TYPE_KEYWORDS = Map.ofEntries(
        Map.entry("演唱會", "演唱會"), Map.entry("concert", "演唱會"), Map.entry("專場", "演唱會"),
        Map.entry("音樂會", "音樂會"), Map.entry("音樂節", "音樂節"), Map.entry("祭", "音樂節"),
        Map.entry("展覽", "展覽"), Map.entry("特展", "展覽"), Map.entry("exhibition", "展覽"), Map.entry("個展", "展覽"),
        Map.entry("市集", "市集"), Map.entry("market", "市集"), Map.entry("快閃", "市集"),
        Map.entry("講座", "講座"), Map.entry("工作坊", "工作坊"), Map.entry("workshop", "工作坊"), Map.entry("分享會", "講座"),
        Map.entry("路跑", "路跑"), Map.entry("馬拉松", "路跑"),
        Map.entry("派對", "派對"), Map.entry("party", "派對"),
        Map.entry("舞台劇", "戲劇"), Map.entry("話劇", "戲劇"), Map.entry("音樂劇", "戲劇"), Map.entry("劇團", "戲劇"),
        Map.entry("電影", "電影"), Map.entry("影展", "電影"),
        Map.entry("親子", "親子活動"), Map.entry("兒童", "親子活動"), Map.entry("體驗", "親子活動"),
        Map.entry("露營", "戶外"), Map.entry("野餐", "戶外"), Map.entry("健行", "戶外")
    );

    // ==================== 查詢擴展 ====================

    /**
     * 類別擴展詞（搜尋時自動加入）
     */
    public static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.ofEntries(
        Map.entry("市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "週末市集", "二手市集", "快閃店", "創意市集", "跳蚤市集")),
        Map.entry("展覽", List.of("藝術展", "美術展", "攝影展", "設計展", "特展", "回顧展", "博覽會", "快閃展", "沈浸式體驗", "museum", "gallery", "美術館展覽", "主題展覽")),
        Map.entry("音樂", List.of("音樂會", "音樂節", "音樂祭", "樂團演出", "live house", "獨立音樂", "祭", "聽團", "管弦樂", "爵士", "live演出")),
        Map.entry("演唱會", List.of("演唱會", "巡迴演唱會", "見面會", "粉絲見面會", "專場")),
        Map.entry("親子", List.of("親子活動", "兒童劇", "DIY", "體驗營", "夏令營", "冬令營", "繪本", "說故事", "親子景點", "觀光工廠", "家庭活動", "兒童活動", "親子市集")),
        Map.entry("戶外", List.of("露營", "野餐", "登山", "健行", "SUP", "路跑", "馬拉松")),
        Map.entry("運動", List.of("路跑", "馬拉松", "健走", "運動賽事", "自行車")),
        Map.entry("festival", List.of("market", "carnival", "fair", "party"))
    );

    /**
     * 同義詞對照表
     */
    public static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
        // 活動類型
        Map.entry("演唱會", List.of("音樂會", "live", "演出", "concert")),
        Map.entry("展覽", List.of("特展", "展出", "展示", "藝術展", "美術展", "exhibition")),
        Map.entry("市集", List.of("文創市集", "假日市集", "手作市集", "創意市集", "market")),
        Map.entry("音樂節", List.of("音樂祭", "festival", "music festival")),
        Map.entry("講座", List.of("座談會", "分享會", "工作坊", "workshop")),
        Map.entry("路跑", List.of("馬拉松", "健走", "慢跑", "run", "marathon")),
        Map.entry("派對", List.of("party", "趴踢", "狂歡")),
        Map.entry("親子", List.of("兒童", "家庭", "小朋友", "kids", "family")),
        // 時間表達
        Map.entry("週末", List.of("周末", "假日", "星期六", "星期日")),
        Map.entry("跨年", List.of("新年", "元旦", "12/31", "1/1")),
        Map.entry("聖誕", List.of("聖誕節", "耶誕", "christmas", "12/25")),
        // 場地同義
        Map.entry("華山", List.of("華山1914", "華山文創園區")),
        Map.entry("松菸", List.of("松山文創", "松山菸廠")),
        Map.entry("駁二", List.of("駁二藝術特區", "pier-2")),
        Map.entry("小巨蛋", List.of("台北小巨蛋", "taipei arena"))
    );

    // ==================== 過濾規則 ====================

    /**
     * 排除的網域
     */
    public static final Set<String> EXCLUDED_DOMAINS = Set.of(
        "x.com", "twitter.com", "ptt.cc",
        "amazon.co.jp", "rakuten.co.jp", "yahoo.co.jp", "booking.com", "agoda.com"
    );

    /**
     * 申請/辦法類關鍵字（需要降權）
     */
    public static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件",
        "招標", "採購", "規定", "須知", "下載"
    );

    /**
     * 專有名詞（高權重匹配）
     */
    public static final Set<String> PROPER_NOUNS = Set.of(
        // 大學
        "政大", "台大", "師大", "清大", "交大", "成大", "中央", "中山", "中興", "北大",
        "輔大", "東吳", "淡江", "文化", "銘傳", "世新", "實踐", "逢甲", "元智", "長庚",
        // 場館
        "華山", "松菸", "駁二", "小巨蛋", "大巨蛋", "國家音樂廳", "兩廳院", "故宮",
        "北美館", "當代藝術館", "科博館", "科工館", "海生館", "衛武營", "高流", "北流",
        // 品牌活動
        "簡單生活節", "大港開唱", "覺醒音樂祭", "春浪", "貢寮海洋音樂祭",
        // 地標
        "信義區", "西門", "東區", "大安", "士林", "勤美", "草悟道"
    );

    // ==================== 時間相關 ====================

    /**
     * 星期對照表
     */
    public static final Map<String, Integer> WEEKDAY_MAP = Map.ofEntries(
        Map.entry("週一", 1), Map.entry("週二", 2), Map.entry("週三", 3),
        Map.entry("週四", 4), Map.entry("週五", 5), Map.entry("週六", 6),
        Map.entry("週日", 7),
        Map.entry("星期一", 1), Map.entry("星期二", 2), Map.entry("星期三", 3),
        Map.entry("星期四", 4), Map.entry("星期五", 5), Map.entry("星期六", 6),
        Map.entry("星期日", 7)
    );

    // ==================== 工具方法 ====================

    /**
     * 標準化城市名稱
     */
    public static String normalizeCity(String city) {
        if (city == null || city.trim().isEmpty()) return null;
        String lower = city.toLowerCase().trim();
        return CITY_ALIASES.getOrDefault(lower, 
               CITY_ALIASES.entrySet().stream()
                   .filter(e -> lower.contains(e.getKey().toLowerCase()))
                   .map(Map.Entry::getValue)
                   .findFirst()
                   .orElse(city.trim()));
    }

    /**
     * 從文字中提取城市
     */
    public static String extractCity(String text) {
        if (text == null || text.isEmpty()) return null;
        String lower = text.toLowerCase();
        
        // 先找場館
        for (Map.Entry<String, String> entry : VENUE_CITY.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        
        // 再找城市
        for (Map.Entry<String, String> entry : CITY_ALIASES.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * 判斷是否包含台灣地名
     */
    public static boolean hasTaiwanLocation(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        
        if (lower.contains("台灣") || lower.contains("臺灣") || lower.contains("taiwan")) {
            return true;
        }
        
        for (String city : TAIWAN_CITIES) {
            if (lower.contains(city)) return true;
        }
        
        for (String landmark : TAIWAN_LANDMARKS) {
            if (lower.contains(landmark.toLowerCase())) return true;
        }
        
        return false;
    }

    /**
     * 判斷是否為海外內容
     */
    public static boolean isLikelyForeign(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        
        int foreignCount = 0;
        for (String key : FOREIGN_KEYWORDS) {
            if (lower.contains(key.toLowerCase())) {
                foreignCount++;
                if (foreignCount >= 2) break;
            }
        }
        
        if (foreignCount == 0) return false;
        return !hasTaiwanLocation(text);
    }

    /**
     * 取得場館對應的城市
     */
    public static String getCityByVenue(String venue) {
        if (venue == null) return null;
        for (Map.Entry<String, String> entry : VENUE_CITY.entrySet()) {
            if (venue.contains(entry.getKey()) || entry.getKey().contains(venue)) {
                return entry.getValue();
            }
        }
        return null;
    }
}