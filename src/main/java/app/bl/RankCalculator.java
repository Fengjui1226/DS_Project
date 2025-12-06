package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RankCalculator v3.0 - 最終優化版
 * 
 * 核心改進：
 * 1. 內文語義配對為主體，標題只是輔助
 * 2. 子頁面加分
 * 3. 日期分數（越近越高，過期扣分）
 * 4. 來源可信度
 */
public class RankCalculator {

    private static final double SUBPAGE_WEIGHT = 0.35;
    
    // ============ 來源可信度 ============
    private static final Map<String, Double> TICKET_PLATFORMS = Map.of(
        "kktix.com", 2.2,
        "accupass.com", 2.2,
        "tixcraft.com", 2.0,
        "ticket.com.tw", 2.0,
        "ticketplus.com.tw", 1.9,
        "ibon.com.tw", 1.8,
        "opentix.life", 2.0,
        "indievox.com", 1.9
    );
    
    private static final Map<String, Double> EVENT_PLATFORMS = Map.of(
        "klook.com", 1.8,
        "kkday.com", 1.8,
        "citytalk.tw", 1.7,
        "eventbrite.com", 1.7,
        "meetup.com", 1.6
    );
    
    private static final Map<String, Double> OFFICIAL_VENUES = Map.of(
        "npac-ntch.org", 1.7,
        "tmc.gov.tw", 1.6,
        "tfam.museum", 1.6,
        "npm.gov.tw", 1.6,
        "huashan1914.com", 1.6,
        "songyanculture.taipei", 1.6,
        "legacy.com.tw", 1.5
    );
    
    private static final Set<String> SOCIAL_MEDIA = Set.of(
        "instagram.com", "facebook.com", "fb.com", 
        "threads.net", "youtube.com"
    );
    private static final double SOCIAL_MEDIA_BOOST = 1.4;
    
    private static final Set<String> SHOPPING_SITES = Set.of(
        "shopee.tw", "momo.com", "pcstore.com.tw", 
        "ruten.com.tw", "books.com.tw", "amazon"
    );
    private static final double SHOPPING_PENALTY = 0.2;

    // ============ 內容懲罰 ============
    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件", 
        "招標", "採購", "規定", "須知", "下載"
    );
    private static final double APPLICATION_PENALTY = 0.1;

    // ============ 活動類型加分 ============
    private static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("個展", 1.2),
        Map.entry("市集", 1.3), Map.entry("夜市", 1.2),
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        Map.entry("派對", 1.2), Map.entry("路跑", 1.3),
        Map.entry("親子", 1.2), Map.entry("免費", 1.1)
    );

    // ============ 主要排名方法 ============
    
    public static void rank(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數...");
        
        // 取得查詢 tokens
        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }
        
        LocalDate today = LocalDate.now();

        // 計算每個頁面的分數
        for (PageNode p : pages) {
            double score = calculatePageScore(p, user, queryTokens, today);
            p.setTotalScore(score);
        }

        // 標準化分數
        normalizeScores(pages);

        // 依總分排序
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        System.out.println("✅ 排名計算完成");
    }

    /**
     * ★ 核心：計算頁面分數（重寫版）
     */
    private static double calculatePageScore(PageNode p, UserProfile user, 
            List<String> tokens, LocalDate today) {
        
        double score = 0;

        // ① 內文語義配對 (主體) - 權重 x3
        score += semanticScore(p.getTextContent(), tokens) * 3;

        // ② 標題加權
        String title = p.getTitle() != null ? p.getTitle().toLowerCase() : "";
        for (String t : tokens) {
            if (title.contains(t.toLowerCase())) {
                score += 1.5;
            }
        }

        // ③ 子頁面加分
        for (SubPageNode sp : p.getSubPages()) {
            score += semanticScore(sp.getTextContent(), tokens);
        }

        // ④ 日期分數 (越近越高)
        LocalDate d = p.getEventDate();
        if (d != null) {
            long days = ChronoUnit.DAYS.between(today, d);
            if (days >= 0 && days < 60) {
                score += 15;  // 兩個月內超加分
            } else if (days >= 0) {
                score += 5;   // 未來但較遠
            } else {
                score -= 10;  // 過期扣分
            }
        }

        // ⑤ 來源可信度
        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";
        double sourceMultiplier = calculateSourceMultiplier(domain);
        score *= sourceMultiplier;

        // ⑥ 內容懲罰
        double contentMultiplier = calculateContentMultiplier(title, p.getUrl());
        score *= contentMultiplier;

        // ⑦ 活動類型加分
        for (Map.Entry<String, Double> entry : EVENT_TYPE_BOOST.entrySet()) {
            if (title.contains(entry.getKey().toLowerCase())) {
                score *= entry.getValue();
            }
        }

        // ⑧ 地區匹配
        String userCity = user != null ? user.getUserCity() : null;
        if (userCity != null && p.getCity() != null && p.getCity().equals(userCity)) {
            score *= 1.3;
        }

        return Math.max(0.1, score);
    }

    /**
     * ★ 新增：語義分數計算
     * 內文關鍵字每個加 2 分
     */
    private static double semanticScore(String text, List<String> tokens) {
        if (text == null || text.isEmpty()) return 0;

        double score = 0;
        String lower = text.toLowerCase();

        for (String t : tokens) {
            if (lower.contains(t.toLowerCase())) {
                score += 2.0;
            }
        }

        return score;
    }

    private static double calculateSourceMultiplier(String domain) {
        // 售票平台
        for (Map.Entry<String, Double> entry : TICKET_PLATFORMS.entrySet()) {
            if (domain.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 活動平台
        for (Map.Entry<String, Double> entry : EVENT_PLATFORMS.entrySet()) {
            if (domain.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 官方場館
        for (Map.Entry<String, Double> entry : OFFICIAL_VENUES.entrySet()) {
            if (domain.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 社群媒體
        for (String social : SOCIAL_MEDIA) {
            if (domain.contains(social)) {
                return SOCIAL_MEDIA_BOOST;
            }
        }
        
        // 購物網站懲罰
        for (String shop : SHOPPING_SITES) {
            if (domain.contains(shop)) {
                return SHOPPING_PENALTY;
            }
        }
        
        // 政府網站
        if (domain.endsWith(".gov.tw")) {
            if (domain.contains("culture") || domain.contains("moc") || domain.contains("tourism")) {
                return 1.3;
            }
            return 0.6;
        }
        
        return 1.0;
    }
    
    private static double calculateContentMultiplier(String title, String url) {
        String t = title != null ? title.toLowerCase() : "";
        String u = url != null ? url.toLowerCase() : "";
        
        // 申請類文件大懲罰
        for (String keyword : APPLICATION_KEYWORDS) {
            if (t.contains(keyword) || u.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        
        return 1.0;
    }
    
    /**
     * 標準化分數到 10-100
     */
    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        
        double maxScore = pages.stream()
            .mapToDouble(PageNode::getTotalScore)
            .max().orElse(1.0);
        
        double minScore = pages.stream()
            .mapToDouble(PageNode::getTotalScore)
            .min().orElse(0.0);
        
        double range = maxScore - minScore;
        if (range < 0.001) range = 1.0;
        
        for (PageNode p : pages) {
            double normalized = ((p.getTotalScore() - minScore) / range) * 90 + 10;
            p.setTotalScore(Math.round(normalized * 10) / 10.0);
        }
    }
}