package app.bl;

import java.util.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * RankCalculator - 完整排名演算法（支援子網頁）
 * 
 * 計算公式：
 * 大網頁總分 = 大網頁自身分數 + Σ(子網頁分數) × 0.3
 * 
 * 流程：
 * 1. 計算每個子網頁的分數
 * 2. 計算大網頁的自身分數
 * 3. 加總子網頁分數
 * 4. 計算總分並排序
 */
public class RankCalculator {

    // ============ 權重參數 ============
    private static final double ALPHA = 0.2;
    private static final double SUBPAGE_WEIGHT = 0.3;  // 子網頁分數權重
    
    // 來源加成
    private static final double TICKET_PLATFORM_BOOST = 1.8;
    private static final double EVENT_PLATFORM_BOOST = 1.5;
    private static final double SOCIAL_MEDIA_BOOST = 1.4;
    private static final double OFFICIAL_VENUE_BOOST = 1.4;
    private static final double GOV_CULTURE_BOOST = 1.3;
    private static final double GOV_GENERAL_PENALTY = 0.8;
    private static final double SHOPPING_PENALTY = 0.3;
    
    // 時間加成
    private static final double DATE_WITHIN_WEEK_BOOST = 2.0;
    private static final double DATE_WITHIN_MONTH_BOOST = 1.6;
    private static final double DATE_WITHIN_3MONTHS_BOOST = 1.3;
    private static final double NO_DATE_PENALTY = 0.7;
    private static final double EXPIRED_PENALTY = 0.05;
    
    // 內容類型懲罰
    private static final double APPLICATION_PENALTY = 0.15;
    private static final double HOMEPAGE_PENALTY = 0.4;

    // ============ 網站分類 ============
    
    private static final Set<String> TICKET_PLATFORMS = Set.of(
        "kktix.com", "accupass.com", "tixcraft.com", "ticket.com.tw",
        "ticketplus.com.tw", "ibon.com.tw", "opentix.life", "indievox.com"
    );
    
    private static final Set<String> SOCIAL_MEDIA = Set.of(
        "instagram.com", "facebook.com", "fb.com", "threads.net", "twitter.com"
    );
    
    private static final Set<String> EVENT_PLATFORMS = Set.of(
        "accupass.com", "citytalk.tw", "eventbrite.com", "meetup.com",
        "klook.com", "kkday.com"
    );
    
    private static final Set<String> OFFICIAL_VENUES = Set.of(
        "npac-ntch.org", "tmc.gov.tw", "tfam.museum", "npm.gov.tw",
        "huashan1914.com", "songyanculture.taipei", "legacy.com.tw"
    );

    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件", "招標"
    );

    private static final Set<String> EVENT_TYPE_KEYWORDS = Set.of(
        "演唱會", "音樂會", "音樂節", "展覽", "特展", "市集",
        "講座", "工作坊", "派對", "路跑", "親子"
    );

    // ============ 主要排名方法 ============
    
    public static void rank(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n--- 開始計算分數 ---");
        
        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }

        // 1. 計算每個頁面的分數（包含子網頁）
        for (PageNode p : pages) {
            // 計算子網頁分數
            calculateSubPageScores(p, user, queryTokens);
            
            // 計算主網頁自身分數
            double selfScore = calculatePageScore(p, user, queryTokens);
            p.setScore(selfScore);
            
            // 計算子網頁分數總和
            p.calculateSubPagesScore();
            
            // 計算總分
            p.calculateTotalScore();
            
            System.out.printf("[Score] %s: self=%.1f, sub=%.1f, total=%.1f%n",
                truncate(p.getTitle(), 30), selfScore, p.getSubPagesScore(), p.getTotalScore());
        }

        // 2. 標準化分數
        normalizeScores(pages);

        // 3. 依總分排序
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        System.out.println("--- 分數計算完成 ---\n");
    }

    /**
     * 計算子網頁分數
     */
    private static void calculateSubPageScores(PageNode page, UserProfile user, List<String> queryTokens) {
        for (SubPageNode sub : page.getSubPages()) {
            double score = calculateSubPageScore(sub, user, queryTokens);
            sub.setScore(score);
        }
    }

    /**
     * 計算單一子網頁分數
     */
    private static double calculateSubPageScore(SubPageNode sub, UserProfile user, List<String> queryTokens) {
        // 1. 基礎分數（詞頻）
        double baseScore = sub.calculateBaseScore();
        
        // 2. 標題包含關鍵字加分
        String title = sub.getTitle().toLowerCase();
        for (String token : queryTokens) {
            if (title.contains(token.toLowerCase())) {
                baseScore += 10.0;
            }
        }
        
        // 3. 活動類型加分
        for (String eventType : EVENT_TYPE_KEYWORDS) {
            if (title.contains(eventType.toLowerCase())) {
                baseScore += 5.0;
            }
        }
        
        return Math.max(0.1, baseScore);
    }

    /**
     * 計算主網頁分數
     */
    private static double calculatePageScore(PageNode p, UserProfile user, List<String> queryTokens) {
        String title = p.getTitle() != null ? p.getTitle() : "";
        String domain = p.getDomain() != null ? p.getDomain() : "";
        String url = p.getUrl() != null ? p.getUrl() : "";
        
        // 1. 基礎分數（TF）
        double baseScore = calculateBaseScore(p, user);
        
        // 2. 來源可信度
        double sourceMultiplier = calculateSourceMultiplier(domain, url);
        
        // 3. 內容類型
        double contentMultiplier = calculateContentMultiplier(title, url);
        
        // 4. 標題品質
        double qualityBonus = calculateQualityBonus(title);
        
        // 5. 時間新鮮度
        double freshnessBonus = calculateFreshnessBonus(p.getEventDate());
        
        // 6. 地區匹配
        double regionBoost = calculateRegionBoost(p.getCity(), user.getUserCity());
        
        // 7. 接近度獎勵
        double proximityBonus = p.calculateProximityBonus(queryTokens);
        
        // 8. 子網頁數量獎勵（有子網頁表示是真正的活動網站）
        double subpageBonus = 1.0 + (p.getSubPageCount() * 0.05);

        // 組合分數
        double finalScore = baseScore 
            * sourceMultiplier 
            * contentMultiplier 
            * qualityBonus
            * regionBoost 
            * proximityBonus
            * subpageBonus
            * freshnessBonus;
        
        return Math.max(0.01, finalScore);
    }

    // ============ 分數計算子方法 ============
    
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
        
        for (String ticket : TICKET_PLATFORMS) {
            if (d.contains(ticket)) return TICKET_PLATFORM_BOOST;
        }
        
        for (String social : SOCIAL_MEDIA) {
            if (d.contains(social)) return SOCIAL_MEDIA_BOOST;
        }
        
        for (String venue : OFFICIAL_VENUES) {
            if (d.contains(venue)) return OFFICIAL_VENUE_BOOST;
        }
        
        for (String platform : EVENT_PLATFORMS) {
            if (d.contains(platform)) return EVENT_PLATFORM_BOOST;
        }
        
        if (d.endsWith(".gov.tw")) {
            if (d.contains("culture") || d.contains("moc")) {
                return GOV_CULTURE_BOOST;
            }
            return GOV_GENERAL_PENALTY;
        }
        
        return 1.0;
    }
    
    private static double calculateContentMultiplier(String title, String url) {
        String t = title.toLowerCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        
        for (String keyword : APPLICATION_KEYWORDS) {
            if (t.contains(keyword) || u.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        
        // 首頁檢測
        if (isHomepage(url)) {
            return HOMEPAGE_PENALTY;
        }
        
        return 1.0;
    }
    
    private static boolean isHomepage(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        int idx = u.indexOf("://");
        if (idx >= 0) u = u.substring(idx + 3);
        int slash = u.indexOf('/');
        if (slash < 0) return true;
        String path = u.substring(slash);
        return path.equals("/") || path.equals("/index.html") || path.length() <= 4;
    }
    
    private static double calculateQualityBonus(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        double bonus = 1.0;
        
        for (String keyword : EVENT_TYPE_KEYWORDS) {
            if (t.contains(keyword.toLowerCase())) {
                bonus += 0.15;
            }
        }
        
        int len = title.length();
        if (len >= 10 && len <= 50) bonus += 0.1;
        else if (len > 80) bonus -= 0.1;
        
        return Math.max(0.5, bonus);
    }
    
    private static double calculateFreshnessBonus(LocalDate eventDate) {
        LocalDate today = LocalDate.now();
        
        if (eventDate == null) return NO_DATE_PENALTY;
        
        long daysUntil = ChronoUnit.DAYS.between(today, eventDate);
        
        if (daysUntil < 0) return EXPIRED_PENALTY;
        if (daysUntil <= 7) return DATE_WITHIN_WEEK_BOOST;
        if (daysUntil <= 30) return DATE_WITHIN_MONTH_BOOST;
        if (daysUntil <= 90) return DATE_WITHIN_3MONTHS_BOOST;
        
        return 1.1;
    }
    
    private static double calculateRegionBoost(String eventCity, String userCity) {
        if (eventCity == null || userCity == null) return 1.0;
        if (eventCity.isEmpty() || userCity.isEmpty()) return 1.0;
        
        if (eventCity.equals(userCity)) return 1.4;
        
        // 相鄰城市
        Set<Set<String>> neighbors = Set.of(
            Set.of("台北", "新北", "基隆"),
            Set.of("桃園", "新竹"),
            Set.of("台中", "彰化", "南投"),
            Set.of("台南", "高雄")
        );
        
        for (Set<String> group : neighbors) {
            if (group.contains(eventCity) && group.contains(userCity)) {
                return 1.2;
            }
        }
        
        return 1.0;
    }
    
    /**
     * 標準化分數到 0-100
     */
    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        
        // 對總分標準化
        double maxScore = pages.stream()
            .mapToDouble(PageNode::getTotalScore)
            .max().orElse(1.0);
        
        double minScore = pages.stream()
            .mapToDouble(PageNode::getTotalScore)
            .min().orElse(0.0);
        
        double range = maxScore - minScore;
        if (range < 0.001) range = 1.0;
        
        for (PageNode p : pages) {
            double normalized = ((p.getTotalScore() - minScore) / range) * 95 + 5;
            
            // 同時更新 score 和 totalScore（顯示用）
            p.setScore(Math.round(normalized * 10) / 10.0);
            
            // 更新總分
            double subNormalized = (p.getSubPagesScore() / (maxScore > 0 ? maxScore : 1)) * 30;
            p.setSubPagesScore(Math.round(subNormalized * 10) / 10.0);
        }
    }
    
    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }
}