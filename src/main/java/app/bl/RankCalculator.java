package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RankCalculator - 完整改進版排名演算法 v2
 * 
 * 🎯 核心宗旨：只顯示「還沒過期」的活動
 * 
 * 計算公式：
 * FinalScore = BaseScore × TypeMultiplier × SourceBoost × FreshnessBoost × RegionBoost × QualityScore
 * 
 * 設計原則：
 * 1. 真正的活動 > 活動列表 > 政府公告/申請頁面
 * 2. 售票網站 > 社群平台(IG/FB) > 一般網站 > 購物網站
 * 3. 有明確日期且即將到來 > 無日期（核心特色！）
 * 4. 符合用戶城市 > 其他城市
 */
public class RankCalculator {

    // ============ 權重參數 ============
    private static final double ALPHA = 0.2;
    
    // 來源類型加成
    private static final double TICKET_PLATFORM_BOOST = 1.8;    // 售票平台
    private static final double EVENT_PLATFORM_BOOST = 1.5;     // 活動平台
    private static final double SOCIAL_MEDIA_BOOST = 1.4;       // 社群媒體（IG、FB）
    private static final double OFFICIAL_VENUE_BOOST = 1.4;     // 官方場館
    private static final double NEWS_MEDIA_BOOST = 1.2;         // 新聞媒體
    private static final double GOV_CULTURE_BOOST = 1.3;        // 政府文化單位
    private static final double GOV_GENERAL_BOOST = 0.8;        // 一般政府網站（降權）
    private static final double SHOPPING_PENALTY = 0.3;         // 購物網站
    
    // 🕐 時間權重（核心功能！這是搜尋引擎的差異化特色）
    private static final double DATE_WITHIN_WEEK_BOOST = 2.0;   // 一週內活動（大幅加權）
    private static final double DATE_WITHIN_MONTH_BOOST = 1.6;  // 一個月內
    private static final double DATE_WITHIN_3MONTHS_BOOST = 1.3;// 三個月內
    private static final double DATE_FUTURE_BOOST = 1.1;        // 更遠的未來
    private static final double NO_DATE_PENALTY = 0.7;          // 沒有日期（降權）
    private static final double EXPIRED_PENALTY = 0.05;         // 已過期（幾乎不顯示）
    
    // 內容類型懲罰
    private static final double APPLICATION_PENALTY = 0.15;     // 申請/辦法頁面
    private static final double REGULATION_PENALTY = 0.2;       // 法規/須知頁面
    private static final double LIST_PAGE_PENALTY = 0.7;        // 列表頁面
    private static final double HOMEPAGE_PENALTY = 0.4;         // 首頁（降權）
    
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
        "livenation.com.tw",
        "indievox.com"
    );
    
    // 📱 社群媒體平台（IG、FB 等）
    private static final Set<String> SOCIAL_MEDIA = Set.of(
        "instagram.com",
        "www.instagram.com",
        "facebook.com",
        "www.facebook.com",
        "fb.com",
        "fb.me",
        "threads.net",
        "twitter.com",
        "x.com",
        "line.me"
    );
    
    // 活動平台
    private static final Set<String> EVENT_PLATFORMS = Set.of(
        "accupass.com",
        "citytalk.tw",
        "eventbrite.com",
        "meetup.com",
        "klook.com",
        "kkday.com",
        "gomaji.com",
        "17life.com",
        "funtime.tw"
    );
    
    // 官方場館 & Live House
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
        "pier2.tw",               // 駁二
        "legacy.com.tw",          // Legacy
        "thewall.tw",             // The Wall
        "revolver.tw",            // Revolver
        "pipemusic.com.tw",       // Pipe Live
        "korner.tw"               // Korner
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
        "beauty321.com",
        "womany.net",
        "niusnews.com",
        "cool-style.com.tw"
    );
    
    // 購物網站（大幅降權）
    private static final Set<String> SHOPPING_SITES = Set.of(
        "shopee", "momo", "pchome", "yahoo購物", "ruten",
        "rakuten", "books.com.tw", "eslite.com", "taobao",
        "amazon", "ebay"
    );
    
    // ============ 負面關鍵字 ============
    
    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "計畫書", "徵選",
        "徵件", "甄選", "招標", "採購", "發包", "委託"
    );
    
    private static final Set<String> REGULATION_KEYWORDS = Set.of(
        "須知", "規定", "規則", "規範", "條例", "法規",
        "注意事項", "相關規定", "作業程序", "審查", "備查"
    );
    
    private static final Set<String> LIST_KEYWORDS = Set.of(
        "列表", "清單", "目錄", "總覽", "彙整", "懶人包"
    );
    
    // ============ 正面關鍵字 ============
    
    private static final Set<String> EVENT_TYPE_KEYWORDS = Set.of(
        "演唱會", "音樂會", "音樂節", "演奏會", "live",
        "展覽", "特展", "聯展", "個展", "畫展", "攝影展",
        "市集", "文創市集", "手作市集", "農夫市集",
        "節慶", "嘉年華", "園遊會", "慶典",
        "講座", "工作坊", "體驗", "課程",
        "派對", "party", "音樂派對",
        "電影", "首映", "影展", "戶外電影",
        "路跑", "馬拉松", "運動", "賽事",
        "親子", "兒童", "家庭日",
        "快閃", "pop-up", "期間限定"
    );
    
    private static final Set<String> TIME_KEYWORDS = Set.of(
        "開演", "開始", "入場", "開幕", "閉幕",
        "早鳥", "預售", "售票", "免費入場",
        "週末", "假日", "平日", "即將", "本週", "本月"
    );

    // ============ 主要排名方法 ============
    
    public static void rank(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty()) return;

        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }

        for (PageNode p : pages) {
            double score = calculateScore(p, user, queryTokens);
            p.setScore(score);
        }
        
        normalizeScores(pages);
        pages.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    }

    private static double calculateScore(PageNode p, UserProfile user, List<String> queryTokens) {
        String title = p.getTitle() != null ? p.getTitle() : "";
        String domain = p.getDomain() != null ? p.getDomain() : "";
        String url = p.getUrl() != null ? p.getUrl() : "";
        
        double baseScore = calculateBaseScore(p, user);
        double sourceMultiplier = calculateSourceMultiplier(domain, url);
        double contentMultiplier = calculateContentMultiplier(title, url);
        double qualityBonus = calculateQualityBonus(title);
        double freshnessBonus = calculateFreshnessBonus(p.getEventDate());
        double regionBoost = calculateRegionBoost(p.getCity(), user.getUserCity());
        double proximityBonus = p.calculateProximityBonus(queryTokens);
        double habitBoost = 1.0 + user.getTotalHabitBoost(p.getTokens()) * 0.5;

        double finalScore = baseScore 
            * sourceMultiplier 
            * contentMultiplier 
            * qualityBonus
            * regionBoost 
            * proximityBonus
            * habitBoost
            * freshnessBonus;  // 時間權重最重要！
        
        return Math.max(0.01, finalScore);
    }

    private static double calculateBaseScore(PageNode p, UserProfile user) {
        double score = 0.0;
        for (Map.Entry<Keyword, Integer> e : p.tf().entrySet()) {
            Keyword k = e.getKey();
            int tf = e.getValue() == null ? 0 : e.getValue();
            double adjustedWeight = user.adjustedWeight(k, ALPHA);
            score += adjustedWeight * tf;
        }
        return Math.max(1.0, score);
    }
    
    private static double calculateSourceMultiplier(String domain, String url) {
        String d = domain.toLowerCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        
        // 售票平台
        for (String ticket : TICKET_PLATFORMS) {
            if (d.contains(ticket) || d.equals(ticket.replace("www.", ""))) {
                return TICKET_PLATFORM_BOOST;
            }
        }
        
        // 社群媒體（IG、FB）
        for (String social : SOCIAL_MEDIA) {
            if (d.contains(social) || d.equals(social.replace("www.", ""))) {
                return SOCIAL_MEDIA_BOOST;
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
        
        // 購物網站
        for (String shop : SHOPPING_SITES) {
            if (d.contains(shop)) {
                return SHOPPING_PENALTY;
            }
        }
        
        // 一般政府網站
        if (d.endsWith(".gov.tw") || d.contains("service.")) {
            if (u.contains("culture") || u.contains("art") || u.contains("music")) {
                return GOV_CULTURE_BOOST;
            }
            return GOV_GENERAL_BOOST;
        }
        
        return 1.0;
    }
    
    private static double calculateContentMultiplier(String title, String url) {
        String t = title.toLowerCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        String combined = t + " " + u;
        
        for (String keyword : APPLICATION_KEYWORDS) {
            if (combined.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        
        for (String keyword : REGULATION_KEYWORDS) {
            if (combined.contains(keyword)) {
                return REGULATION_PENALTY;
            }
        }
        
        for (String keyword : LIST_KEYWORDS) {
            if (t.contains(keyword)) {
                return LIST_PAGE_PENALTY;
            }
        }
        
        if (t.contains("下載") || t.contains("表格") || t.contains("範本")) {
            return 0.25;
        }
        
        if (t.contains("常見問題") || t.contains("faq")) {
            return 0.4;
        }
        
        if (t.contains("關於我們") || t.contains("聯絡") || t.contains("隱私權")) {
            return 0.2;
        }
        
        // 🏠 首頁檢測 - 降權
        if (isHomepageUrl(url)) {
            return HOMEPAGE_PENALTY;
        }
        
        return 1.0;
    }
    
    /**
     * 🏠 檢查是否為首頁 URL
     */
    private static boolean isHomepageUrl(String url) {
        if (url == null) return false;
        
        try {
            String u = url.toLowerCase(Locale.ROOT);
            
            // 移除 protocol
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            
            // 移除 domain
            int s = u.indexOf('/');
            if (s < 0) return true;  // 沒有路徑 = 首頁
            
            String path = u.substring(s);
            
            // 只有 "/" = 首頁
            if (path.equals("/")) return true;
            
            // 常見首頁模式
            Set<String> homepagePatterns = Set.of(
                "/index.html", "/index.php", "/index.aspx",
                "/home", "/main", "/default.aspx"
            );
            
            for (String pattern : homepagePatterns) {
                if (path.equals(pattern) || path.startsWith(pattern + "?")) {
                    return true;
                }
            }
            
            // 路徑太短（如 /tw, /zh）通常是首頁
            if (path.length() <= 4 && !path.contains(".")) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static double calculateQualityBonus(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        double bonus = 1.0;
        
        int eventTypeCount = 0;
        for (String keyword : EVENT_TYPE_KEYWORDS) {
            if (t.contains(keyword.toLowerCase())) {
                eventTypeCount++;
            }
        }
        bonus += eventTypeCount * 0.15;
        
        int timeCount = 0;
        for (String keyword : TIME_KEYWORDS) {
            if (t.contains(keyword.toLowerCase())) {
                timeCount++;
            }
        }
        bonus += timeCount * 0.1;
        
        int len = title.length();
        if (len >= 10 && len <= 50) {
            bonus += 0.1;
        } else if (len > 80) {
            bonus -= 0.1;
        }
        
        if (t.contains("台北") || t.contains("台中") || t.contains("高雄") || 
            t.contains("台南") || t.contains("桃園")) {
            bonus += 0.1;
        }
        
        return Math.max(0.5, bonus);
    }
    
    /**
     * 🕐【核心功能】時間新鮮度
     * 
     * EventFinder 的核心差異化特色：只顯示未過期的活動！
     */
    private static double calculateFreshnessBonus(LocalDate eventDate) {
        LocalDate today = LocalDate.now();
        
        // 沒有日期 - 降權
        if (eventDate == null) {
            return NO_DATE_PENALTY;
        }
        
        long daysUntil = ChronoUnit.DAYS.between(today, eventDate);
        
        // 已過期 - 幾乎不顯示
        if (daysUntil < 0) {
            return EXPIRED_PENALTY;
        }
        
        // 一週內 - 最高優先
        if (daysUntil <= 7) {
            return DATE_WITHIN_WEEK_BOOST;
        }
        
        // 一個月內
        if (daysUntil <= 30) {
            return DATE_WITHIN_MONTH_BOOST;
        }
        
        // 三個月內
        if (daysUntil <= 90) {
            return DATE_WITHIN_3MONTHS_BOOST;
        }
        
        // 更遠的未來
        return DATE_FUTURE_BOOST;
    }
    
    private static double calculateRegionBoost(String eventCity, String userCity) {
        if (eventCity == null || eventCity.isEmpty()) return 1.0;
        if (userCity == null || userCity.isEmpty()) return 1.0;
        
        if (eventCity.equals(userCity)) return 1.4;
        if (areNeighboringCities(eventCity, userCity)) return 1.2;
        
        return 1.0;
    }
    
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
    
    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        
        double maxScore = pages.stream().mapToDouble(PageNode::getScore).max().orElse(1.0);
        double minScore = pages.stream().mapToDouble(PageNode::getScore).min().orElse(0.0);
        
        double range = maxScore - minScore;
        if (range < 0.001) range = 1.0;
        for (PageNode p : pages) {
            double normalized = ((p.getScore() - minScore) / range) * 95 + 5;
            p.setScore(Math.round(normalized * 10) / 10.0);
        }
    }
}