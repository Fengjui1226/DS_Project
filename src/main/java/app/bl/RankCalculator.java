package app.bl;

import java.util.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * RankCalculator - 完整改進版排名演算法
 * 
 * 計算公式：
 * FinalScore = BaseScore × TypeMultiplier × SourceBoost × FreshnessBoost × RegionBoost × QualityScore
 * 
 * 設計原則：
 * 1. 真正的活動 > 活動列表 > 政府公告/申請頁面
 * 2. 售票網站 > 一般網站 > 購物網站
 * 3. 有明確日期 > 無日期
 * 4. 符合用戶城市 > 其他城市
 */
public class RankCalculator {

    // ============ 權重參數（可調整）============
    private static final double ALPHA = 0.2;
    
    // 來源類型加成
    private static final double TICKET_PLATFORM_BOOST = 1.8;    // 售票平台
    private static final double EVENT_PLATFORM_BOOST = 1.5;     // 活動平台
    private static final double OFFICIAL_VENUE_BOOST = 1.4;     // 官方場館
    private static final double NEWS_MEDIA_BOOST = 1.2;         // 新聞媒體
    private static final double GOV_CULTURE_BOOST = 1.3;        // 政府文化單位
    private static final double GOV_GENERAL_BOOST = 0.8;        // 一般政府網站（降權）
    private static final double SHOPPING_PENALTY = 0.3;         // 購物網站
    
    // 內容類型懲罰
    private static final double APPLICATION_PENALTY = 0.15;     // 申請/辦法頁面
    private static final double REGULATION_PENALTY = 0.2;       // 法規/須知頁面
    private static final double LIST_PAGE_PENALTY = 0.7;        // 列表頁面（稍微降權）
    
    // ============ 網站分類 ============
    
    // 售票平台（最高優先）
    private static final Set<String> TICKET_PLATFORMS = Set.of(
        "kktix.com", "kktix.cc",
        "accupass.com",
        "tixcraft.com",
        "ticket.com.tw",
        "ticketplus.com.tw",
        "ibon.com.tw",
        "tickets.udnfunlife.com",
        "www.opentix.life", "opentix.life",
        "ticketmaster.com",
        "livenation.com.tw"
    );
    
    // 活動平台
    private static final Set<String> EVENT_PLATFORMS = Set.of(
        "accupass.com",
        "citytalk.tw",
        "eventbrite.com",
        "meetup.com",
        "facebook.com/events",
        "klook.com",
        "kkday.com"
    );
    
    // 官方場館
    private static final Set<String> OFFICIAL_VENUES = Set.of(
        "npac-ntch.org",          // 國家兩廳院
        "artsticket.com.tw",      // 藝文售票
        "tmc.gov.tw",             // 台北流行音樂中心
        "ntso.gov.tw",            // 國立台灣交響樂團
        "jam.moc.gov.tw",         // 文化部活動
        "cksmh.gov.tw",           // 中正紀念堂
        "npm.gov.tw",             // 故宮
        "tfam.museum",            // 北美館
        "mocataipei.org.tw",      // 當代藝術館
        "ntm.gov.tw",             // 國立台灣博物館
        "ntsec.gov.tw",           // 科教館
        "huashan1914.com",        // 華山
        "songyanculture.taipei",  // 松菸
        "pier2.tw"                // 駁二
    );
    
    // 政府文化單位
    private static final Set<String> GOV_CULTURE = Set.of(
        "culture.gov.tw",
        "moc.gov.tw",
        "culture.taipei",
        "culture.taichung.gov.tw",
        "culture.tainan.gov.tw",
        "khcc.gov.tw"
    );
    
    // 新聞/活動媒體
    private static final Set<String> NEWS_MEDIA = Set.of(
        "udn.com",
        "ltn.com.tw",
        "chinatimes.com",
        "ettoday.net",
        "walkerland.com.tw",
        "elle.com",
        "gq.com.tw",
        "beauty321.com"
    );
    
    // 購物網站（大幅降權）
    private static final Set<String> SHOPPING_SITES = Set.of(
        "shopee", "momo", "pchome", "yahoo購物", "ruten",
        "rakuten", "books.com.tw", "eslite.com", "taobao",
        "amazon", "ebay"
    );
    
    // ============ 負面關鍵字 ============
    
    // 申請/辦法類（大幅降權）
    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "計畫書", "徵選",
        "徵件", "甄選", "招標", "採購", "發包", "委託"
    );
    
    // 法規/公告類（降權）
    private static final Set<String> REGULATION_KEYWORDS = Set.of(
        "須知", "規定", "規則", "規範", "條例", "法規",
        "注意事項", "相關規定", "作業程序", "審查", "備查"
    );
    
    // 列表/目錄類（稍微降權）
    private static final Set<String> LIST_KEYWORDS = Set.of(
        "列表", "清單", "目錄", "總覽", "彙整", "懶人包",
        "整理", "大全", "合集"
    );
    
    // ============ 正面關鍵字（加分）============
    
    // 活動類型關鍵字
    private static final Set<String> EVENT_TYPE_KEYWORDS = Set.of(
        "演唱會", "音樂會", "音樂節", "演奏會", "live",
        "展覽", "特展", "聯展", "個展", "畫展", "攝影展",
        "市集", "文創市集", "手作市集", "農夫市集",
        "節慶", "嘉年華", "園遊會", "慶典",
        "講座", "工作坊", "體驗", "課程",
        "派對", "party", "音樂派對",
        "電影", "首映", "影展", "戶外電影",
        "路跑", "馬拉松", "運動", "賽事",
        "親子", "兒童", "家庭日"
    );
    
    // 時間相關關鍵字（表示有具體活動）
    private static final Set<String> TIME_KEYWORDS = Set.of(
        "開演", "開始", "入場", "開幕", "閉幕",
        "早鳥", "預售", "售票", "免費入場",
        "週末", "假日", "平日", "每週", "每月"
    );

    // ============ 主要排名方法 ============
    
    public static void rank(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty()) return;

        // 取得查詢 tokens
        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }

        // 計算每個頁面的分數
        for (PageNode p : pages) {
            double score = calculateScore(p, user, queryTokens);
            p.setScore(score);
        }
        
        // 標準化分數到 0-100
        normalizeScores(pages);

        // 依分數排序（高到低）
        pages.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    }

    private static double calculateScore(PageNode p, UserProfile user, List<String> queryTokens) {
        String title = p.getTitle() != null ? p.getTitle() : "";
        String domain = p.getDomain() != null ? p.getDomain() : "";
        String url = p.getUrl() != null ? p.getUrl() : "";
        
        // 1. 基礎分數（關鍵字匹配）
        double baseScore = calculateBaseScore(p, user);
        
        // 2. 來源可信度（網站類型）
        double sourceMultiplier = calculateSourceMultiplier(domain, url);
        
        // 3. 內容類型（是否為真正活動）
        double contentMultiplier = calculateContentMultiplier(title, url);
        
        // 4. 標題品質（含活動關鍵字加分）
        double qualityBonus = calculateQualityBonus(title);
        
        // 5. 時間新鮮度
        double freshnessBonus = calculateFreshnessBonus(p.getEventDate());
        
        // 6. 地區匹配
        double regionBoost = calculateRegionBoost(p.getCity(), user.getUserCity());
        
        // 7. 接近度獎勵
        double proximityBonus = p.calculateProximityBonus(queryTokens);
        
        // 8. 用戶習慣
        double habitBoost = 1.0 + user.getTotalHabitBoost(p.getTokens()) * 0.5;

        // 組合分數
        double finalScore = baseScore 
            * sourceMultiplier 
            * contentMultiplier 
            * qualityBonus
            * freshnessBonus 
            * regionBoost 
            * proximityBonus
            * habitBoost;
        
        return Math.max(0.01, finalScore);  // 確保最小分數
    }

    // ============ 分數計算子方法 ============
    
    /**
     * 基礎分數：關鍵字 TF 加權
     */
    private static double calculateBaseScore(PageNode p, UserProfile user) {
        double score = 0.0;
        for (Map.Entry<Keyword, Integer> e : p.tf().entrySet()) {
            Keyword k = e.getKey();
            int tf = e.getValue() == null ? 0 : e.getValue();
            double adjustedWeight = user.adjustedWeight(k, ALPHA);
            score += adjustedWeight * tf;
        }
        return Math.max(1.0, score);  // 最小為 1
    }
    
    /**
     * 來源可信度：根據網站類型給予不同權重
     */
    private static double calculateSourceMultiplier(String domain, String url) {
        String d = domain.toLowerCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        
        // 售票平台（最高優先）
        for (String ticket : TICKET_PLATFORMS) {
            if (d.contains(ticket) || d.equals(ticket.replace("www.", ""))) {
                return TICKET_PLATFORM_BOOST;
            }
        }
        
        // 官方場館
        for (String venue : OFFICIAL_VENUES) {
            if (d.contains(venue)) {
                return OFFICIAL_VENUE_BOOST;
            }
        }
        
        // 活動平台
        for (String platform : EVENT_PLATFORMS) {
            if (d.contains(platform)) {
                return EVENT_PLATFORM_BOOST;
            }
        }
        
        // 政府文化單位
        for (String culture : GOV_CULTURE) {
            if (d.contains(culture)) {
                return GOV_CULTURE_BOOST;
            }
        }
        
        // 新聞媒體
        for (String news : NEWS_MEDIA) {
            if (d.contains(news)) {
                return NEWS_MEDIA_BOOST;
            }
        }
        
        // 購物網站（大幅降權）
        for (String shop : SHOPPING_SITES) {
            if (d.contains(shop)) {
                return SHOPPING_PENALTY;
            }
        }
        
        // 一般政府網站（如 service.taipei）
        if (d.endsWith(".gov.tw") || d.contains("service.")) {
            // 檢查是否為文化相關
            if (u.contains("culture") || u.contains("art") || u.contains("music")) {
                return GOV_CULTURE_BOOST;
            }
            return GOV_GENERAL_BOOST;  // 一般政府網站降權
        }
        
        return 1.0;  // 預設
    }
    
    /**
     * 內容類型：判斷是否為真正的活動頁面
     */
    private static double calculateContentMultiplier(String title, String url) {
        String t = title.toLowerCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        String combined = t + " " + u;
        
        // 申請/補助類（大幅降權）
        for (String keyword : APPLICATION_KEYWORDS) {
            if (combined.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        
        // 法規/須知類（降權）
        for (String keyword : REGULATION_KEYWORDS) {
            if (combined.contains(keyword)) {
                return REGULATION_PENALTY;
            }
        }
        
        // 列表頁面（稍微降權，因為不是具體活動）
        for (String keyword : LIST_KEYWORDS) {
            if (t.contains(keyword)) {
                return LIST_PAGE_PENALTY;
            }
        }
        
        // 其他負面信號
        if (t.contains("下載") || t.contains("表格") || t.contains("範本")) {
            return 0.25;
        }
        
        if (t.contains("常見問題") || t.contains("faq") || t.contains("q&a")) {
            return 0.4;
        }
        
        if (t.contains("關於我們") || t.contains("聯絡") || t.contains("隱私權")) {
            return 0.2;
        }
        
        return 1.0;
    }
    
    /**
     * 標題品質：含活動關鍵字加分
     */
    private static double calculateQualityBonus(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        double bonus = 1.0;
        
        // 活動類型關鍵字（每個 +15%）
        int eventTypeCount = 0;
        for (String keyword : EVENT_TYPE_KEYWORDS) {
            if (t.contains(keyword.toLowerCase())) {
                eventTypeCount++;
            }
        }
        bonus += eventTypeCount * 0.15;
        
        // 時間相關關鍵字（表示有具體活動資訊）
        int timeCount = 0;
        for (String keyword : TIME_KEYWORDS) {
            if (t.contains(keyword.toLowerCase())) {
                timeCount++;
            }
        }
        bonus += timeCount * 0.1;
        
        // 標題長度適中（太短或太長都不好）
        int len = title.length();
        if (len >= 10 && len <= 50) {
            bonus += 0.1;  // 適中長度加分
        } else if (len > 80) {
            bonus -= 0.1;  // 太長減分
        }
        
        // 含有地點資訊
        if (t.contains("台北") || t.contains("台中") || t.contains("高雄") || 
            t.contains("台南") || t.contains("桃園")) {
            bonus += 0.1;
        }
        
        return Math.max(0.5, bonus);
    }
    
    /**
     * 時間新鮮度：有日期且即將到來的活動加分
     */
    private static double calculateFreshnessBonus(LocalDate eventDate) {
        if (eventDate == null) {
            return 0.9;  // 沒有日期稍微降權
        }
        
        LocalDate today = LocalDate.now();
        long daysUntil = ChronoUnit.DAYS.between(today, eventDate);
        
        if (daysUntil < 0) {
            return 0.1;  // 已過期（應該被過濾，但以防萬一）
        } else if (daysUntil <= 7) {
            return 1.5;  // 一週內，高度優先
        } else if (daysUntil <= 30) {
            return 1.3;  // 一個月內
        } else if (daysUntil <= 90) {
            return 1.1;  // 三個月內
        } else {
            return 1.0;  // 更遠的未來
        }
    }
    
    /**
     * 地區匹配：符合用戶城市加分
     */
    private static double calculateRegionBoost(String eventCity, String userCity) {
        if (eventCity == null || eventCity.isEmpty()) {
            return 1.0;
        }
        if (userCity == null || userCity.isEmpty()) {
            return 1.0;
        }
        
        // 完全匹配
        if (eventCity.equals(userCity)) {
            return 1.4;
        }
        
        // 相鄰城市（如台北-新北）
        if (areNeighboringCities(eventCity, userCity)) {
            return 1.2;
        }
        
        return 1.0;
    }
    
    /**
     * 判斷是否為相鄰城市
     */
    private static boolean areNeighboringCities(String city1, String city2) {
        Set<Set<String>> neighbors = Set.of(
            Set.of("台北", "新北", "基隆"),
            Set.of("桃園", "新竹"),
            Set.of("台中", "彰化", "南投"),
            Set.of("台南", "高雄"),
            Set.of("宜蘭", "花蓮")
        );
        
        for (Set<String> group : neighbors) {
            if (group.contains(city1) && group.contains(city2)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 標準化分數到 0-100
     */
    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        
        double maxScore = pages.stream()
            .mapToDouble(PageNode::getScore)
            .max()
            .orElse(1.0);
        
        double minScore = pages.stream()
            .mapToDouble(PageNode::getScore)
            .min()
            .orElse(0.0);
        
        double range = maxScore - minScore;
        if (range < 0.001) range = 1.0;
        
        for (PageNode p : pages) {
            // 線性映射到 0-100，但最低給 5 分
            double normalized = ((p.getScore() - minScore) / range) * 95 + 5;
            p.setScore(Math.round(normalized * 10) / 10.0);  // 保留一位小數
        }
    }
}