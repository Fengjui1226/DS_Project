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

    // ==================== 權威與黑名單 (保持先前優化的版本) ====================

    public static final Set<String> AUTHORITY_DOMAINS = Set.of(
        "accupass.com", "opentix.life", "tixcraft.com", "kktix.com",
        "indievox.com", "ticket.com.tw", "udnfunlife.com", "kham.com.tw",
        "ticket.ibon.com.tw", "famiport.com.tw",
        // IG 懶人包媒體 (原有)
        "popdaily.com.tw", "elle.com", "vogue.com.tw", "marieclaire.com.tw", 
        "womenshealthmag.com", "gq.com.tw", "travel.yahoo.com.tw", 
        "girlstalk.cc", "niusnews.com", "shoppingdesign.com.tw", "500times.udn.com",
        // ★ 新增：時尚生活媒體
        "harpersbazaar.com.tw",    // Harper's BAZAAR (搜尋結果很多)
        "beautimode.com",          // BeautiMode
        "styletc.com",             // 時尚圈
        "wowlavie.com",            // LaVie 設計生活
        "gvm.com.tw",              // 遠見雜誌
        "businessweekly.com.tw",   // 商周 (搜尋結果有活動整理)
        // ★ 新增：旅遊生活網站
        "welcometw.com",           // 好好玩 (搜尋結果有)
        "liviatravel.com",         // Livia's Wonderland
        "momoblog.tw",             // 桃桃旅人手札 (搜尋結果有)
        "walkerland.com.tw",       // 窩客島
        "taipei-walker.com",       // 台北Walker
        "travel.taipei",           // 台北旅遊網 (官方)
        "eztravel.com.tw",         // 易遊網
        // ★ 新增：美食生活
        "supertaste.tvbs.com.tw",  // 食尚玩家 (搜尋結果有)
        "ifoodie.tw",              // 愛食記
        "zineblog.com.tw",         // Zine 生活誌
        // ★ 新增：市集主辦單位
        "gds.apothecary1969.com",  // 好日市集 (搜尋結果有)
        "popupasia.com",           // Pop Up Asia
        "simplemarket.tw",         // Simple Market
        // ★ 新增：票務與活動平台
        "tw.trip.com",             // Trip.com 台灣 (搜尋結果有)
        "klook.com",               // Klook (搜尋結果有)
        "kkday.com",               // KKday
        // ★ 新增：創作平台
        "vocus.cc"                 // 方格子 (搜尋結果有市集整理)
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
    
    // ★ 新增：社群平台域名（爬蟲會失敗，但 snippet 可用）
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