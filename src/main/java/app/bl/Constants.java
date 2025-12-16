package app.bl;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Constants v4.1 - 關鍵字庫終極擴充版
 * * 擴充：台灣地標 (TAIWAN_LANDMARKS) - 覆蓋北中南各大熱門活動區
 * * 擴充：類別擴展 (CATEGORY_EXPANSIONS) - 增加更多活動細分類
 * * 保持：針對 IG、學術論文的優化策略
 */
public final class Constants {

    private Constants() {} 

    // ==================== 城市相關 (保持不變) ====================
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
    

    // 台北市常用行政區的大概中心點（lat, lng）
public static final Map<String, double[]> DISTRICT_COORDS = Map.ofEntries(
    Map.entry("信義區", new double[]{25.0328, 121.5680}),
    Map.entry("大安區", new double[]{25.0260, 121.5430}),
    Map.entry("中正區", new double[]{25.0326, 121.5198}),
    Map.entry("松山區", new double[]{25.0571, 121.5570}),
    Map.entry("萬華區", new double[]{25.0260, 121.4973}),
    Map.entry("內湖區", new double[]{25.0830, 121.5750}),
    Map.entry("士林區", new double[]{25.0928, 121.5246}),
    Map.entry("文山區", new double[]{24.9889, 121.5703})
    // 之後要加新北、其他縣市的也可以慢慢擴充
);

    // ==================== 場館相關 (保持不變) ====================
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
    
    // ★ 擴充：台灣熱門活動地標 (用於判斷是否為台灣活動)
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

    // ==================== 關鍵字擴充 ====================

    // ★ 擴充：查詢擴展 (這些詞會被用來擴大搜尋範圍)
    public static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.ofEntries(
        // 市集類
        Map.entry("市集", List.of(
            "文創市集", "手作市集", "假日市集", "聖誕市集", "二手市集", 
            "小農市集", "餐車市集", "風格市集", "主題市集", "復古市集", 
            "蚤之市", "花市", "創意市集", "夜市", "market"
        )),
        // 展覽類
        Map.entry("展覽", List.of(
            "特展", "回顧展", "快閃店", "體驗展", "藝術展", "設計展", 
            "攝影展", "插畫展", "動漫展", "博覽會", "畢業展", "個展", 
            "聯展", "沉浸式展覽", "互動展", "光影展", "exhibition"
        )),
        // 音樂類
        Map.entry("音樂", List.of(
            "音樂節", "音樂祭", "live house", "聽團", "樂團演出", 
            "獨立音樂", "爵士音樂節", "管弦樂", "售票演唱會", "免費演唱會", 
            "音樂會", "DJ派對", "電音", "嘻哈", "饒舌", "music festival"
        )),
        // 演唱會類
        Map.entry("演唱會", List.of(
            "巡迴演唱會", "專場", "見面會", "粉絲見面會", "售票演唱會", 
            "演唱會門票", "小型演出", "acoustic", "concert"
        )),
        // 親子類
        Map.entry("親子", List.of(
            "親子活動", "體驗營", "繪本", "說故事", "兒童劇", "DIY", 
            "觀光工廠", "科普活動", "共融公園", "親子館", "夏令營", "冬令營",
            "兒童工作坊", "親子手作", "親子餐廳"
        )),
        // 戶外類
        Map.entry("戶外", List.of(
            "露營", "野餐", "登山", "健行", "路跑", "馬拉松", "單車", 
            "自行車", "SUP", "衝浪", "生態導覽", "賞花", "賞螢", "賞楓",
            "獨木舟", "溯溪", "攀岩", "高空彈跳"
        )),
        // 藝文類
        Map.entry("藝文", List.of(
            "講座", "工作坊", "座談會", "分享會", "讀書會", "電影放映", 
            "影展", "舞台劇", "舞蹈", "戲劇", "表演藝術", "脫口秀", 
            "相聲", "音樂劇", "現代舞", "芭蕾"
        )),
        // 節慶類
        Map.entry("節慶", List.of(
            "跨年", "聖誕", "過年", "春節", "元宵", "燈會", 
            "萬聖節", "情人節", "嘉年華", "祭典", "煙火", "花火節",
            "啤酒節", "週年慶"
        )),
        // ★ 新增：美食類
        Map.entry("美食", List.of(
            "美食節", "餐酒會", "品酒會", "咖啡節", "甜點節", "火鍋節", 
            "啤酒節", "小吃節", "夜市美食", "美食展", "料理教室", 
            "烘焙課", "品茗", "下午茶", "brunch"
        )),
        // ★ 新增：運動賽事類
        Map.entry("運動", List.of(
            "籃球", "棒球", "足球", "電競", "賽事", "比賽", "球賽",
            "羽球", "網球", "桌球", "排球", "游泳", "拳擊", "格鬥",
            "健身", "瑜珈", "有氧", "重訓", "運動會"
        )),
        // ★ 新增：寵物類
        Map.entry("寵物", List.of(
            "毛小孩", "狗聚", "貓咪", "寵物友善", "寵物展", "認養",
            "寵物市集", "毛孩", "汪星人", "喵星人", "寵物野餐",
            "狗狗運動會", "寵物嘉年華"
        )),
        // ★ 新增：夜生活類
        Map.entry("夜生活", List.of(
            "夜店", "酒吧", "派對", "電音", "clubbing", "lounge",
            "DJ night", "ladies night", "主題派對", "泳池派對",
            "rooftop", "調酒"
        )),
        // ★ 新增：文青類
        Map.entry("文青", List.of(
            "獨立書店", "咖啡廳", "選物店", "vintage", "古著",
            "黑膠", "底片", "手沖咖啡", "文創園區", "藝術村",
            "老屋", "老宅", "文化祭"
        )),
        // ★ 新增：潮流類
        Map.entry("潮流", List.of(
            "潮牌", "球鞋", "sneaker", "街頭", "塗鴉", "滑板",
            "嘻哈", "饒舌", "街舞", "breaking", "潮流市集"
        ))
    );

    // ==================== 權威與黑名單 ====================

    // ★ 移除權威網域加成，讓搜尋結果自然排序
    // 好的結果會因為內容相關性和日期而排前面
    public static final Set<String> AUTHORITY_DOMAINS = Set.of(
        // 只保留官方網站
        "travel.taipei",           // 台北旅遊網 (政府官方)
        "gov.tw"                   // 政府網站
    );

    public static final Set<String> NOISE_KEYWORDS = Set.of(
        // 學術論文
        "碩士論文", "博士論文", "學位論文", "研究生", "指導教授", "口試", 
        "摘要", "Abstract", "參考文獻", "引用", "文獻探討", "研究方法", 
        "研究動機", "研究目的", "關鍵詞", "Keywords", "DOI", "PDF下載", 
        "期刊", "學報", "專題製作", "畢業製作", "成果報告书",
        // 公文與工具
        "決算書", "預算書", "財報", "報表", "公告", "標案", "決標", "開標", "會議記錄", "公報",
        "有線電視", "第四台", "寬頻", "光纖", "維修", "客服", "安裝", "費率", "繳費",
        "徵才", "職缺", "招募", "求職", "人力銀行", "工讀", "實習",
        "新聞網", "日報", "快訊", "社論", "專欄"
        // 注意：移除了「懶人包」，因為懶人包文章通常是很好的活動彙整
    );

    public static final Set<String> FOREIGN_KEYWORDS_CORE = Set.of(
        "東京", "大阪", "京都", "北海道", "首爾", "釜山", "曼谷", "機票", "入境", "簽證"
    );

    public static final Set<String> EVENT_TYPES = Set.of(
        // 原有
        "演唱會", "音樂會", "音樂節", "展覽", "特展", "市集", "夜市",
        "講座", "工作坊", "派對", "路跑", "馬拉松", "親子", "電影",
        "舞台劇", "音樂劇", "脫口秀", "相聲", "魔術", "馬戲",
        "快閃店", "見面會", "簽書會",
        // 新增
        "美食節", "啤酒節", "咖啡節", "運動會", "球賽", "電競",
        "寵物展", "狗聚", "DJ", "電音", "嘉年華", "燈會", "花火",
        "野餐", "露營", "健行", "瑜珈", "手作", "體驗"
    );

    public static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "快閃店", "見面會", "簽書會", "發表會", "體驗會",
        "生活節", "音樂祭", "派對", "路跑", "馬拉松",
        "festival", "concert", "exhibition", "event", "pop-up"
    );

    public static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        // 原有
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("快閃店", 1.4),
        Map.entry("市集", 1.3), Map.entry("生活節", 1.3),
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        Map.entry("見面會", 1.3), Map.entry("簽書會", 1.2),
        Map.entry("親子", 1.2), Map.entry("免費", 1.1),
        // 新增
        Map.entry("美食節", 1.3), Map.entry("啤酒節", 1.3), Map.entry("咖啡節", 1.2),
        Map.entry("電競", 1.3), Map.entry("球賽", 1.2), Map.entry("運動會", 1.2),
        Map.entry("寵物", 1.2), Map.entry("毛小孩", 1.2),
        Map.entry("派對", 1.2), Map.entry("電音", 1.2), Map.entry("DJ", 1.2),
        Map.entry("燈會", 1.3), Map.entry("花火", 1.3), Map.entry("煙火", 1.3),
        Map.entry("野餐", 1.2), Map.entry("露營", 1.2), Map.entry("嘉年華", 1.3)
    );

    public static final Map<String, String> EVENT_TYPE_KEYWORDS = Map.ofEntries(
        Map.entry("演唱會", "演唱會"), Map.entry("concert", "演唱會"),
        Map.entry("音樂會", "音樂會"), Map.entry("音樂節", "音樂節"),
        Map.entry("展覽", "展覽"), Map.entry("exhibition", "展覽"),
        Map.entry("市集", "市集"), Map.entry("market", "市集"),
        Map.entry("講座", "講座"), Map.entry("工作坊", "工作坊"),
        Map.entry("路跑", "路跑"), Map.entry("馬拉松", "路跑"),
        Map.entry("親子", "親子活動"), Map.entry("體驗", "親子活動")
    );

    public static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
        Map.entry("演唱會", List.of("音樂會", "live", "演出")),
        Map.entry("展覽", List.of("特展", "展出", "展示")),
        Map.entry("市集", List.of("文創市集", "假日市集", "market")),
        Map.entry("音樂節", List.of("音樂祭", "festival")),
        Map.entry("週末", List.of("假日", "星期六", "星期日")),
        Map.entry("跨年", List.of("新年", "元旦"))
    );

    // ★ 社群平台策略：不排除 IG/FB/Threads，改用 Google snippet 作為內容來源
    // 即使爬蟲失敗，snippet 已經包含足夠資訊
    public static final Set<String> EXCLUDED_DOMAINS = Set.of(
        "x.com", "twitter.com",  // Twitter/X 仍排除（內容太碎）
        "ptt.cc", "dcard.tw",    // 論壇討論，非活動資訊
        "amazon.co.jp", "rakuten.co.jp", "yahoo.co.jp",  // 日本購物
        "booking.com", "agoda.com", "trivago.com", "hotels.com",  // 訂房網站
        "ndltd.ncl.edu.tw", "airitilibrary.com", "scholar.google.com.tw"  // 學術
        // ★ 已移除：facebook.com, instagram.com（改由 snippet 提供內容）
    );
    
    // ★ 新增：社群平台域名（爬蟲會失敗，但 snippet 可用）讚讚
    public static final Set<String> SOCIAL_DOMAINS = Set.of(
        "instagram.com", "facebook.com", "threads.net", "fb.com"
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
    if (text.contains("台灣") || text.contains("臺灣")) return true;
    if (extractCity(text) != null) return true;
    for (String landmark : TAIWAN_LANDMARKS) {
        if (text.contains(landmark)) return true;
    }
    return false;
}
    

    public static String extractDistrict(String text) {
    if (text == null) return null;
    for (String district : DISTRICT_COORDS.keySet()) {
        if (text.contains(district)) {
            return district;
        }
    }
    return null;
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
    
    // ==================== 🆕 新增常數 ====================
    
    // ★ 1. 售票平台識別（用於優先處理、解析優化）
    public static final Map<String, String> TICKETING_PLATFORMS = Map.ofEntries(
        Map.entry("kktix.com", "KKTIX"),
        Map.entry("accupass.com", "Accupass"),
        Map.entry("tixcraft.com", "拓元"),
        Map.entry("ticket.com.tw", "年代售票"),
        Map.entry("opentix.life", "OPENTIX"),
        Map.entry("ibon.com.tw", "ibon售票"),
        Map.entry("gomaji.com", "GOMAJI"),
        Map.entry("indievox.com", "iNDIEVOX"),
        Map.entry("ticketmaster.com.tw", "Ticketmaster"),
        Map.entry("cityline.com", "城市售票網")
    );
    
    // ★ 2. 活動資訊網站（高品質來源）
    public static final Set<String> EVENT_INFO_SITES = Set.of(
        "taipei-walkin.tw",      // 台北散步
        "travelking.com.tw",     // 旅遊王
        "fun-life.com.tw",       // 滿分的旅遊
        "funtime.com.tw",        // FunTime
        "walkerland.com.tw",     // 窩客島
        "mook.com.tw",           // MOOK景點家
        "bring4u.tw",            // 帶你去旅行
        "shopee.tw/m/events",    // 蝦皮活動
        "klook.com/zh-TW"        // Klook
    );
    
    // ★ 3. 日期相關關鍵字（用於 parseDate 增強）
    public static final Map<String, Integer> RELATIVE_DATE_KEYWORDS = Map.ofEntries(
        Map.entry("今天", 0),
        Map.entry("明天", 1),
        Map.entry("後天", 2),
        Map.entry("大後天", 3),
        Map.entry("今日", 0),
        Map.entry("明日", 1),
        Map.entry("本週", 7),    // 範圍
        Map.entry("下週", 14),   // 範圍
        Map.entry("這週", 7),
        Map.entry("下周", 14),
        Map.entry("本月", 30),
        Map.entry("下個月", 60)
    );
    
    // ★ 4. 特殊節日對應日期（每年固定）
    public static final Map<String, int[]> HOLIDAY_DATES = Map.ofEntries(
        // 格式: {月, 日, 範圍天數}
        Map.entry("元旦", new int[]{1, 1, 3}),
        Map.entry("跨年", new int[]{12, 31, 3}),
        Map.entry("情人節", new int[]{2, 14, 3}),
        Map.entry("白色情人節", new int[]{3, 14, 3}),
        Map.entry("愚人節", new int[]{4, 1, 1}),
        Map.entry("勞動節", new int[]{5, 1, 3}),
        Map.entry("母親節", new int[]{5, 10, 7}),      // 5月第二個週日附近
        Map.entry("父親節", new int[]{8, 8, 3}),
        Map.entry("七夕", new int[]{8, 4, 7}),         // 農曆七月七，約在8月
        Map.entry("中秋", new int[]{9, 17, 7}),        // 農曆八月十五，約在9月
        Map.entry("雙十節", new int[]{10, 10, 3}),
        Map.entry("國慶", new int[]{10, 10, 3}),
        Map.entry("萬聖節", new int[]{10, 31, 7}),
        Map.entry("光棍節", new int[]{11, 11, 3}),
        Map.entry("感恩節", new int[]{11, 24, 3}),
        Map.entry("聖誕節", new int[]{12, 25, 7}),
        Map.entry("耶誕節", new int[]{12, 25, 7}),
        Map.entry("平安夜", new int[]{12, 24, 3})
    );
    
    // ★ 5. 價格相關關鍵字
    public static final Set<String> FREE_KEYWORDS = Set.of(
        "免費", "免票", "free", "FREE", "Free",
        "免費入場", "入場免費", "免費參觀", "自由入場",
        "免費參加", "無料", "0元", "免門票", "免費索票"
    );
    
    public static final Set<String> PAID_KEYWORDS = Set.of(
        "售票", "購票", "票價", "門票", "入場券",
        "早鳥票", "預售票", "現場票", "VIP票",
        "全票", "優待票", "學生票", "團體票"
    );
    
    // ★ 6. 時段關鍵字（用於判斷活動時間）
    public static final Map<String, int[]> TIME_PERIOD_KEYWORDS = Map.ofEntries(
        // 格式: {開始小時, 結束小時}
        Map.entry("早上", new int[]{6, 12}),
        Map.entry("上午", new int[]{6, 12}),
        Map.entry("中午", new int[]{11, 14}),
        Map.entry("下午", new int[]{12, 18}),
        Map.entry("傍晚", new int[]{16, 19}),
        Map.entry("晚上", new int[]{18, 23}),
        Map.entry("晚間", new int[]{18, 23}),
        Map.entry("深夜", new int[]{22, 4}),
        Map.entry("凌晨", new int[]{0, 6}),
        Map.entry("全天", new int[]{0, 24}),
        Map.entry("整天", new int[]{0, 24})
    );
    
    // ★ 7. 活動規模關鍵字（用於排名調整）
    public static final Map<String, Double> EVENT_SCALE_BOOST = Map.ofEntries(
        Map.entry("國際", 1.2),
        Map.entry("世界", 1.2),
        Map.entry("全球", 1.2),
        Map.entry("亞洲", 1.15),
        Map.entry("全國", 1.1),
        Map.entry("全台", 1.1),
        Map.entry("首次", 1.15),
        Map.entry("首度", 1.15),
        Map.entry("獨家", 1.1),
        Map.entry("限定", 1.1),
        Map.entry("限量", 1.1),
        Map.entry("最後", 1.1),     // 最後一場
        Map.entry("告別", 1.15),    // 告別演唱會
        Map.entry("加場", 1.1),
        Map.entry("安可", 1.1)
    );
    
    // ★ 8. 目標客群關鍵字
    public static final Set<String> AUDIENCE_KEYWORDS = Set.of(
        // 年齡層
        "親子", "兒童", "幼兒", "小朋友", "青少年", "大學生", "上班族", "銀髮族", "樂齡",
        // 興趣群體
        "文青", "攝影愛好者", "音樂愛好者", "運動愛好者", "美食愛好者",
        "毛小孩", "寵物友善", "狗友善", "貓奴",
        // 特殊需求
        "無障礙", "輪椅友善", "親子友善", "寵物可入"
    );
    
    // ★ 9. 排除的 URL 路徑（爬蟲跳過）
    public static final Set<String> SKIP_URL_PATHS = Set.of(
        "/login", "/signup", "/register", "/auth",
        "/cart", "/checkout", "/payment",
        "/account", "/profile", "/settings",
        "/admin", "/dashboard",
        "/api/", "/static/", "/assets/", "/images/",
        "/privacy", "/terms", "/policy", "/about", "/contact",
        "/faq", "/help", "/support",
        "/tag/", "/category/", "/archive/"
    );
    
    // ★ 10. 高品質內容指標（用於評分加成）
    public static final Set<String> QUALITY_INDICATORS = Set.of(
        // 有具體資訊
        "地點", "地址", "時間", "日期", "票價", "費用",
        "主辦", "協辦", "報名", "購票連結",
        // 有詳細說明
        "活動介紹", "活動內容", "節目表", "演出陣容",
        "交通資訊", "停車資訊", "注意事項"
    );
    
    // ★ 11. 新北市行政區座標（擴充 DISTRICT_COORDS）
    public static final Map<String, double[]> NEW_TAIPEI_DISTRICT_COORDS = Map.ofEntries(
        Map.entry("板橋區", new double[]{25.0146, 121.4593}),
        Map.entry("三重區", new double[]{25.0615, 121.4885}),
        Map.entry("中和區", new double[]{24.9991, 121.4989}),
        Map.entry("永和區", new double[]{25.0074, 121.5160}),
        Map.entry("新莊區", new double[]{25.0359, 121.4504}),
        Map.entry("新店區", new double[]{24.9676, 121.5419}),
        Map.entry("淡水區", new double[]{25.1696, 121.4407}),
        Map.entry("汐止區", new double[]{25.0676, 121.6575}),
        Map.entry("樹林區", new double[]{24.9904, 121.4205}),
        Map.entry("鶯歌區", new double[]{24.9554, 121.3545}),
        Map.entry("三峽區", new double[]{24.9340, 121.3687}),
        Map.entry("林口區", new double[]{25.0775, 121.3912}),
        Map.entry("蘆洲區", new double[]{25.0850, 121.4734}),
        Map.entry("五股區", new double[]{25.0829, 121.4380}),
        Map.entry("泰山區", new double[]{25.0593, 121.4308}),
        Map.entry("土城區", new double[]{24.9725, 121.4432})
    );
    
    // ★ 12. 常見活動主辦單位（用於辨識可信度）
    public static final Set<String> KNOWN_ORGANIZERS = Set.of(
        // 政府單位
        "文化部", "觀光局", "觀傳局", "文化局", "教育局", "體育局",
        "台北市政府", "新北市政府", "台中市政府", "高雄市政府",
        // 知名主辦
        "超級圓頂", "必應創造", "華研國際", "相信音樂", "環球音樂",
        "LiveNation", "KKTIX", "Accupass", "寬宏藝術",
        // 場館
        "華山1914", "松山文創", "誠品", "北美館", "故宮", "兩廳院"
    );
    
    // ★ 13. TF-IDF 停用詞擴充
    public static final Set<String> STOPWORDS_EXTENDED = Set.of(
        // 中文停用詞
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一個",
        "上", "也", "很", "到", "說", "要", "去", "你", "會", "著", "沒有", "看", "好",
        "自己", "這", "那", "他", "她", "它", "這個", "那個", "什麼", "怎麼", "為什麼",
        "可以", "可能", "應該", "需要", "想要", "喜歡", "覺得", "知道", "希望",
        // 網頁常見詞
        "首頁", "登入", "註冊", "更多", "詳情", "點擊", "查看", "分享", "按讚",
        "Facebook", "LINE", "Instagram", "Twitter", "複製連結",
        // 時間通用詞
        "年", "月", "日", "號", "時", "分", "秒", "點"
    );
    
    // ==================== 🆕 工具方法擴充 ====================
    
    /**
     * 判斷是否為售票平台
     */
    public static boolean isTicketingPlatform(String domain) {
        if (domain == null) return false;
        String lower = domain.toLowerCase();
        return TICKETING_PLATFORMS.keySet().stream()
            .anyMatch(lower::contains);
    }
    
    /**
     * 取得售票平台名稱
     */
    public static String getTicketingPlatformName(String domain) {
        if (domain == null) return null;
        String lower = domain.toLowerCase();
        for (Map.Entry<String, String> entry : TICKETING_PLATFORMS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * 判斷是否為免費活動
     */
    public static boolean isFreeEvent(String text) {
        if (text == null) return false;
        return FREE_KEYWORDS.stream().anyMatch(text::contains);
    }
    
    /**
     * 計算活動規模加成
     */
    public static double getEventScaleBoost(String text) {
        if (text == null) return 1.0;
        double boost = 1.0;
        for (Map.Entry<String, Double> entry : EVENT_SCALE_BOOST.entrySet()) {
            if (text.contains(entry.getKey())) {
                boost = Math.max(boost, entry.getValue());
            }
        }
        return boost;
    }
    
    /**
     * 計算內容品質分數
     */
    public static int getQualityScore(String text) {
        if (text == null) return 0;
        int score = 0;
        for (String indicator : QUALITY_INDICATORS) {
            if (text.contains(indicator)) {
                score += 5;
            }
        }
        return Math.min(score, 50); // 上限 50 分
    }
    
    /**
     * 判斷是否為已知主辦單位
     */
    public static boolean isKnownOrganizer(String text) {
        if (text == null) return false;
        return KNOWN_ORGANIZERS.stream().anyMatch(text::contains);
    }
    
    /**
     * 取得節日日期範圍
     */
    public static int[] getHolidayDateRange(String keyword) {
        if (keyword == null) return null;
        for (Map.Entry<String, int[]> entry : HOLIDAY_DATES.entrySet()) {
            if (keyword.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}