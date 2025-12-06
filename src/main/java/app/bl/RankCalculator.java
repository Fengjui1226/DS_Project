package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RankCalculator v3.1 - 精準匹配優化版
 * 
 * 核心改進：
 * 1. 完整查詢匹配超高加分
 * 2. 專有名詞（機構/地點）匹配優先
 * 3. 匹配比例計算（匹配越多關鍵字分數越高）
 * 4. 標題匹配權重大幅提升
 */
public class RankCalculator {

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

    // ============ 專有名詞（高權重）============
    private static final Set<String> PROPER_NOUNS = Set.of(
        // 大學
        "政大", "台大", "師大", "清大", "交大", "成大", "中央", "中山", "中興", "北大",
        "輔大", "東吳", "淡江", "文化", "銘傳", "世新", "實踐", "逢甲", "元智", "長庚",
        // 場館
        "華山", "松菸", "駁二", "小巨蛋", "大巨蛋", "國家音樂廳", "兩廳院", "故宮",
        "北美館", "當代藝術館", "科博館", "科工館", "海生館",
        // 品牌活動
        "簡單生活節", "大港開唱", "覺醒音樂祭", "春浪", "貢寮海洋音樂祭",
        // 地標
        "信義區", "西門", "東區", "中山", "大安", "士林"
    );

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
    
    public static void rank(List<PageNode> pages, UserProfile user, String originalQuery) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數...");
        System.out.println("📝 原始查詢: " + originalQuery);
        
        // 取得查詢 tokens
        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }
        
        // 識別專有名詞 tokens（高權重）
        List<String> properNounTokens = new ArrayList<>();
        List<String> normalTokens = new ArrayList<>();
        
        for (String token : queryTokens) {
            if (isProperNoun(token)) {
                properNounTokens.add(token);
            } else {
                normalTokens.add(token);
            }
        }
        
        System.out.println("🔑 專有名詞: " + properNounTokens);
        System.out.println("🔤 一般關鍵字: " + normalTokens);
        
        LocalDate today = LocalDate.now();

        // 計算每個頁面的分數
        for (PageNode p : pages) {
            double score = calculatePageScore(p, user, originalQuery, 
                    properNounTokens, normalTokens, today);
            p.setTotalScore(score);
        }

        // 標準化分數
        normalizeScores(pages);

        // 依總分排序
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        System.out.println("✅ 排名計算完成");
    }
    
    // 向後兼容的舊方法
    public static void rank(List<PageNode> pages, UserProfile user) {
        rank(pages, user, null);
    }

    /**
     * 檢查是否為專有名詞
     */
    private static boolean isProperNoun(String token) {
        for (String noun : PROPER_NOUNS) {
            if (token.contains(noun) || noun.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ 核心：計算頁面分數（v3.1 精準匹配版）
     */
    private static double calculatePageScore(PageNode p, UserProfile user,
            String originalQuery, List<String> properNouns, 
            List<String> normalTokens, LocalDate today) {
        
        double score = 0;
        String title = p.getTitle() != null ? p.getTitle() : "";
        String titleLower = title.toLowerCase();
        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String contentLower = content.toLowerCase();
        
        // ★★★ 第一優先：完整查詢匹配 ★★★
        if (originalQuery != null && !originalQuery.isEmpty()) {
            if (title.contains(originalQuery)) {
                score += 50;  // 標題完全包含查詢 = 超高分
                System.out.println("  🎯 完整匹配標題: " + title.substring(0, Math.min(30, title.length())));
            } else if (content.contains(originalQuery)) {
                score += 25;  // 內文包含完整查詢
            }
        }

        // ★★ 第二優先：專有名詞匹配（權重 x5）★★
        int properNounMatches = 0;
        for (String noun : properNouns) {
            if (titleLower.contains(noun.toLowerCase())) {
                score += 15;  // 標題中有專有名詞
                properNounMatches++;
            } else if (contentLower.contains(noun.toLowerCase())) {
                score += 5;   // 內文中有專有名詞
                properNounMatches++;
            }
        }
        
        // 如果有專有名詞但一個都沒匹配 = 大扣分
        if (!properNouns.isEmpty() && properNounMatches == 0) {
            score -= 20;  // 懲罰：搜「政大」但結果完全沒有政大
        }

        // ★ 第三優先：一般關鍵字匹配 ★
        int normalMatches = 0;
        for (String token : normalTokens) {
            if (titleLower.contains(token.toLowerCase())) {
                score += 3;
                normalMatches++;
            } else if (contentLower.contains(token.toLowerCase())) {
                score += 1;
                normalMatches++;
            }
        }

        // ④ 匹配比例加成
        int totalTokens = properNouns.size() + normalTokens.size();
        int totalMatches = properNounMatches + normalMatches;
        if (totalTokens > 0) {
            double matchRatio = (double) totalMatches / totalTokens;
            score *= (1 + matchRatio);  // 匹配越多，分數越高
        }

        // ⑤ 日期分數 (越近越高)
        LocalDate d = p.getEventDate();
        if (d != null) {
            long days = ChronoUnit.DAYS.between(today, d);
            if (days >= 0 && days < 60) {
                score += 10;  // 兩個月內加分
            } else if (days >= 0) {
                score += 3;   // 未來但較遠
            } else {
                score -= 15;  // 過期扣分（加重）
            }
        }

        // ⑥ 來源可信度
        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";
        double sourceMultiplier = calculateSourceMultiplier(domain);
        score *= sourceMultiplier;

        // ⑦ 內容懲罰
        double contentMultiplier = calculateContentMultiplier(title, p.getUrl());
        score *= contentMultiplier;

        // ⑧ 活動類型加分
        for (Map.Entry<String, Double> entry : EVENT_TYPE_BOOST.entrySet()) {
            if (titleLower.contains(entry.getKey().toLowerCase())) {
                score *= entry.getValue();
                break;  // 只取一次
            }
        }

        // ⑨ 地區匹配
        String userCity = user != null ? user.getUserCity() : null;
        if (userCity != null && p.getCity() != null && p.getCity().equals(userCity)) {
            score *= 1.2;
        }

        return Math.max(0.1, score);
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