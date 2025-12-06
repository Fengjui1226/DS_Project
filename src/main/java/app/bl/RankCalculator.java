package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RankCalculator v2.0 - 打敗 Google 的排名演算法
 * 
 * 核心理念：
 * 1. 活動相關性 > 一般網頁相關性
 * 2. 時效性極度重要（即將發生的活動優先）
 * 3. 可信來源加成（售票平台 > 官方網站 > 一般網站）
 * 4. 子網頁分析加深理解
 * 5. 負面信號懲罰（申請辦法、已過期等）
 * 
 * 計算公式：
 * TotalScore = (BaseScore × SourceMultiplier × ContentMultiplier × FreshnessBoost × RegionBoost)
 *            + (SubPagesScore × SUBPAGE_WEIGHT)
 */
public class RankCalculator {

    // ============ 核心權重參數 ============
    private static final double ALPHA = 0.2;              // TF 調整係數
    private static final double SUBPAGE_WEIGHT = 0.35;    // 子網頁分數權重（提高）
    
    // ============ 來源可信度（這是打敗 Google 的關鍵）============
    
    // 售票平台 = 最高可信度（有票 = 真實活動）
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
    
    // 活動平台 = 高可信度
    private static final Map<String, Double> EVENT_PLATFORMS = Map.of(
        "klook.com", 1.8,
        "kkday.com", 1.8,
        "citytalk.tw", 1.7,
        "eventbrite.com", 1.7,
        "meetup.com", 1.6
    );
    
    // 官方場館 = 可信來源
    private static final Map<String, Double> OFFICIAL_VENUES = Map.of(
        "npac-ntch.org", 1.7,      // 國家兩廳院
        "tmc.gov.tw", 1.6,         // 台北流行音樂中心
        "tfam.museum", 1.6,        // 北美館
        "npm.gov.tw", 1.6,         // 故宮
        "huashan1914.com", 1.6,    // 華山
        "songyanculture.taipei", 1.6,  // 松菸
        "legacy.com.tw", 1.5       // Legacy
    );
    
    // 社群媒體 = 中等可信度（有時效性資訊）
    private static final Set<String> SOCIAL_MEDIA = Set.of(
        "instagram.com", "facebook.com", "fb.com", 
        "threads.net", "twitter.com", "x.com"
    );
    private static final double SOCIAL_MEDIA_BOOST = 1.4;
    
    // 文化部/觀光局 = 可信但可能是政策文件
    private static final double GOV_CULTURE_BOOST = 1.3;
    private static final double GOV_GENERAL_PENALTY = 0.6;  // 一般政府網站懲罰加重
    
    // 購物網站 = 大懲罰
    private static final Set<String> SHOPPING_SITES = Set.of(
        "shopee.tw", "momo.com", "pcstore.com.tw", 
        "ruten.com.tw", "books.com.tw", "amazon"
    );
    private static final double SHOPPING_PENALTY = 0.2;

        // ============ 時間新鮮度（核心差異化）============
    // 近期活動一樣給高加權，但不再把沒有日期 / 過期打到幾乎 0 分
    private static final double DATE_TODAY_BOOST = 2.5;           // 今天
    private static final double DATE_TOMORROW_BOOST = 2.3;        // 明天
    private static final double DATE_THIS_WEEK_BOOST = 2.0;       // 本週
    private static final double DATE_NEXT_WEEK_BOOST = 1.8;       // 下週
    private static final double DATE_THIS_MONTH_BOOST = 1.5;      // 本月
    private static final double DATE_WITHIN_3MONTHS_BOOST = 1.2;  // 三個月內

    // 沒寫日期 → 只小扣；已過期 → 還是有分，但排在未來活動後面
    private static final double NO_DATE_PENALTY = 0.9;            // 無日期：輕微懲罰
    private static final double EXPIRED_PENALTY = 0.7;            // 已過期：中度懲罰

    // ============ 內容品質 ============
    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件", 
        "招標", "採購", "規定", "須知", "下載"
    );
    private static final double APPLICATION_PENALTY = 0.1;     // 申請類懲罰更重
    private static final double HOMEPAGE_PENALTY = 0.5;        // 首頁懲罰

    // 活動類型關鍵字（標題包含這些 = 好）
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

        // 1. 計算每個頁面的分數
        for (PageNode p : pages) {
            // 計算子網頁分數
            calculateSubPageScores(p, user, queryTokens, today);
            
            // 計算主網頁分數
            double selfScore = calculatePageScore(p, user, queryTokens, today);
            p.setScore(selfScore);
            
            // 計算子網頁總分
            p.calculateSubPagesScore();
            
            // 計算總分
            p.calculateTotalScore();
        }

        // 2. 標準化分數
        normalizeScores(pages);

        // 3. 依總分排序
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        System.out.println("✅ 排名計算完成");
    }

    /**
     * 計算子網頁分數
     */
    private static void calculateSubPageScores(PageNode page, UserProfile user, 
            List<String> queryTokens, LocalDate today) {
        for (SubPageNode sub : page.getSubPages()) {
            double score = calculateSubPageScore(sub, queryTokens, today);
            sub.setScore(score);
        }
    }

    /**
     * 計算單一子網頁分數
     */
    private static double calculateSubPageScore(SubPageNode sub, List<String> queryTokens, LocalDate today) {
        double score = sub.calculateBaseScore();
        String title = sub.getTitle().toLowerCase();
        String content = sub.getTextContent().toLowerCase();
        
        // 標題關鍵字匹配（重要！）
        for (String token : queryTokens) {
            if (title.contains(token.toLowerCase())) {
                score += 15.0;  // 標題匹配加很多分
            }
            if (content.contains(token.toLowerCase())) {
                score += 3.0;   // 內容匹配加一些分
            }
        }
        
        // 活動類型加分
        for (Map.Entry<String, Double> entry : EVENT_TYPE_BOOST.entrySet()) {
            if (title.contains(entry.getKey())) {
                score *= entry.getValue();
            }
        }
        
        // 檢查是否有日期資訊
        if (hasDatePattern(title) || hasDatePattern(content)) {
            score *= 1.3;  // 有日期的子網頁更可能是活動
        }
        
        return Math.max(0.1, score);
    }

    /**
     * 計算主網頁分數（核心算法）
     */
    private static double calculatePageScore(PageNode p, UserProfile user, 
            List<String> queryTokens, LocalDate today) {
        
        String title = p.getTitle() != null ? p.getTitle() : "";
        String domain = p.getDomain() != null ? p.getDomain() : "";
        String url = p.getUrl() != null ? p.getUrl() : "";
        String textContent = p.getTextContent() != null ? p.getTextContent() : "";
        
        // 1. 基礎分數（TF-IDF 概念）
        double baseScore = calculateBaseScore(p, user);
        
        // 2. 來源可信度乘數（關鍵！）
        double sourceMultiplier = calculateSourceMultiplier(domain, url);
        
        // 3. 內容類型乘數
        double contentMultiplier = calculateContentMultiplier(title, url);
        
        // 4. 標題品質加成
        double qualityBonus = calculateQualityBonus(title, queryTokens);
        
        // 5. 時間新鮮度（極度重要）
        double freshnessBoost = calculateFreshnessBoost(p.getEventDate(), today);
        
        // 6. 地區匹配
        double regionBoost = calculateRegionBoost(p.getCity(), user.getUserCity());
        
        // 7. 內容深度加成（有爬到內容 = 更可靠）
        double contentDepthBonus = 1.0;
        if (p.isCrawled() && textContent.length() > 500) {
            contentDepthBonus = 1.2;
            // 內容中包含查詢關鍵字
            for (String token : queryTokens) {
                if (textContent.toLowerCase().contains(token.toLowerCase())) {
                    contentDepthBonus += 0.1;
                }
            }
        }
        
        // 8. 子網頁數量獎勵
        double subpageBonus = 1.0 + Math.min(p.getSubPageCount() * 0.08, 0.4);

        // 組合分數
        double finalScore = baseScore 
            * sourceMultiplier 
            * contentMultiplier 
            * qualityBonus
            * freshnessBoost
            * regionBoost 
            * contentDepthBonus
            * subpageBonus;
        
        // Debug 輸出（可選）
        if (finalScore > 10) {
            System.out.printf("  [%s] base=%.1f src=%.1f content=%.1f fresh=%.1f → %.1f%n",
                truncate(title, 20), baseScore, sourceMultiplier, contentMultiplier, freshnessBoost, finalScore);
        }
        
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
        
        // 售票平台（最高優先）
        for (Map.Entry<String, Double> entry : TICKET_PLATFORMS.entrySet()) {
            if (d.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 活動平台
        for (Map.Entry<String, Double> entry : EVENT_PLATFORMS.entrySet()) {
            if (d.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 官方場館
        for (Map.Entry<String, Double> entry : OFFICIAL_VENUES.entrySet()) {
            if (d.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 社群媒體
        for (String social : SOCIAL_MEDIA) {
            if (d.contains(social)) {
                return SOCIAL_MEDIA_BOOST;
            }
        }
        
        // 購物網站懲罰
        for (String shop : SHOPPING_SITES) {
            if (d.contains(shop)) {
                return SHOPPING_PENALTY;
            }
        }
        
        // 政府網站
        if (d.endsWith(".gov.tw")) {
            if (d.contains("culture") || d.contains("moc") || d.contains("tourism")) {
                return GOV_CULTURE_BOOST;
            }
            return GOV_GENERAL_PENALTY;
        }
        
        return 1.0;
    }
    
    private static double calculateContentMultiplier(String title, String url) {
        String t = title.toLowerCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        
        // 申請類文件大懲罰
        for (String keyword : APPLICATION_KEYWORDS) {
            if (t.contains(keyword) || u.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        
        // 首頁懲罰
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
        return path.equals("/") || path.equals("/index.html") || 
               path.equals("/index.php") || path.length() <= 4;
    }
    
    private static double calculateQualityBonus(String title, List<String> queryTokens) {
        String t = title.toLowerCase(Locale.ROOT);
        double bonus = 1.0;
        
        // 標題包含查詢關鍵字（非常重要）
        for (String token : queryTokens) {
            if (t.contains(token.toLowerCase())) {
                bonus += 0.25;
            }
        }
        
        // 活動類型關鍵字
        for (Map.Entry<String, Double> entry : EVENT_TYPE_BOOST.entrySet()) {
            if (t.contains(entry.getKey().toLowerCase())) {
                bonus *= entry.getValue();
            }
        }
        
        // 標題長度適中
        int len = title.length();
        if (len >= 10 && len <= 60) bonus += 0.1;
        else if (len > 100) bonus -= 0.2;  // 太長的標題通常是垃圾
        
        return Math.max(0.3, Math.min(bonus, 3.0));  // 限制範圍
    }
    
    /**
 * 根據活動日期計算新鮮度加權
 * - 未來越近分數越高
 * - 沒有日期：給 NO_DATE_PENALTY（0.9）
 * - 已過期：給 EXPIRED_PENALTY（0.7），仍保留一定分數
 */
    private static double calculateFreshnessBoost(LocalDate eventDate, LocalDate today) {
        // 沒有日期：代表從標題抓不到時間，只稍微扣分即可
        if (eventDate == null) {
            return NO_DATE_PENALTY;
        }

        long daysUntil = ChronoUnit.DAYS.between(today, eventDate);

        // 已過期：仍給分，但會排在近期活動後面
        if (daysUntil < 0) {
            return EXPIRED_PENALTY;
        }

        // 近期加權
        if (daysUntil == 0) return DATE_TODAY_BOOST;             // 今天
        if (daysUntil == 1) return DATE_TOMORROW_BOOST;          // 明天
        if (daysUntil <= 7) return DATE_THIS_WEEK_BOOST;         // 本週
        if (daysUntil <= 14) return DATE_NEXT_WEEK_BOOST;        // 下週
        if (daysUntil <= 30) return DATE_THIS_MONTH_BOOST;       // 本月
        if (daysUntil <= 90) return DATE_WITHIN_3MONTHS_BOOST;   // 三個月內

        // 超過三個月以後：視為普通，不特別加權
        return 1.0;
    }
    
    private static double calculateRegionBoost(String eventCity, String userCity) {
        if (eventCity == null || userCity == null) return 1.0;
        if (eventCity.isEmpty() || userCity.isEmpty()) return 1.0;
        
        // 完全匹配
        if (eventCity.equals(userCity)) return 1.5;
        
        // 相鄰城市
        Map<String, Set<String>> neighbors = Map.of(
            "台北", Set.of("新北", "基隆"),
            "新北", Set.of("台北", "基隆", "桃園"),
            "桃園", Set.of("新北", "新竹"),
            "台中", Set.of("彰化", "南投", "苗栗"),
            "台南", Set.of("高雄", "嘉義"),
            "高雄", Set.of("台南", "屏東")
        );
        
        Set<String> nearby = neighbors.get(userCity);
        if (nearby != null && nearby.contains(eventCity)) {
            return 1.25;
        }
        
        return 1.0;
    }
    
    /**
     * 檢查是否有日期模式
     */
    private static boolean hasDatePattern(String text) {
        if (text == null) return false;
        // 簡單檢查常見日期模式
        return text.matches(".*\\d{4}[./\\-]\\d{1,2}[./\\-]\\d{1,2}.*") ||
               text.matches(".*\\d{1,2}月\\d{1,2}日.*") ||
               text.matches(".*\\d{1,2}/\\d{1,2}.*");
    }
    
    /**
     * 標準化分數到 0-100
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
            // 使用對數縮放讓分數分布更均勻
            double normalized = ((p.getTotalScore() - minScore) / range) * 90 + 10;
            p.setScore(Math.round(normalized * 10) / 10.0);
            
            // 子網頁分數也標準化
            double subNormalized = (p.getSubPagesScore() / (maxScore > 0 ? maxScore : 1)) * 30;
            p.setSubPagesScore(Math.round(subNormalized * 10) / 10.0);
        }
    }
    
    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }
}