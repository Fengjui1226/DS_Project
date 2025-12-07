package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RankCalculator v3.3 - 內容導向、平台幾乎平等版 + 城市加權
 *
 * 主要設計：
 * 1. 以「關鍵字匹配 + 內文完整度 + 日期新鮮度」為主
 * 2. 不再對售票平台 / 活動平台給超大加權，大家幾乎平等
 * 3. 只保留「文化 / 觀光類政府網站」一點點加分（資訊較可靠）
 * 4. 同城活動明顯優先：同城市放大權重，不同城市降低權重
 * 5. 會吃到 PageNode 在爬蟲階段累積的 score（內文 match 等）
 */
public class RankCalculator {

    // ===== 專有名詞（高權重）=====
    private static final Set<String> PROPER_NOUNS = Set.copyOf(new HashSet<>(List.of(
        // 大學
        "政大", "台大", "師大", "清大", "交大", "成大", "中央", "中山", "中興", "北大",
        "輔大", "東吳", "淡江", "文化", "銘傳", "世新", "實踐", "逢甲", "元智", "長庚",
        // 場館
        "華山", "松菸", "駁二", "小巨蛋", "大巨蛋", "國家音樂廳", "兩廳院", "故宮",
        "北美館", "當代藝術館", "科博館", "科工館", "海生館",
        // 品牌活動
        "簡單生活節", "大港開唱", "覺醒音樂祭", "春浪", "貢寮海洋音樂祭",
        // 地標
        "信義區", "西門", "東區", "大安", "士林"
    )));

    // ===== 申請/徵選類懲罰 =====
    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件",
        "招標", "採購", "規定", "須知", "下載"
    );
    private static final double APPLICATION_PENALTY = 0.3;   // 中度懲罰

    // ===== 活動類型加分 =====
    private static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("個展", 1.2),
        Map.entry("市集", 1.3), Map.entry("夜市", 1.2),
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        Map.entry("派對", 1.2), Map.entry("路跑", 1.3),
        Map.entry("親子", 1.2), Map.entry("免費", 1.1)
    );

    // ================= 主入口 =================

    public static void rank(List<PageNode> pages, UserProfile user, String originalQuery) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數...");
        System.out.println("📝 原始查詢: " + originalQuery);

        // 先從任一頁面拿 tokens（SearchEngine 已經統一填入）
        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }

        // 專有名詞 / 一般關鍵字拆開
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

        // 計算分數
        for (PageNode p : pages) {
            double score = calculatePageScore(
                p, user, originalQuery,
                properNounTokens, normalTokens, today
            );
            p.setTotalScore(score);
        }

        // 標準化到 10~100
        normalizeScores(pages);

        // 由高到低排序
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        System.out.println("✅ 排名計算完成");
    }

    // 舊介面相容
    public static void rank(List<PageNode> pages, UserProfile user) {
        rank(pages, user, null);
    }

    // ================= 內部細節 =================

    private static boolean isProperNoun(String token) {
        for (String noun : PROPER_NOUNS) {
            if (token.contains(noun) || noun.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 核心：計算單一頁面的原始分數（未標準化）
     */
    private static double calculatePageScore(
            PageNode p,
            UserProfile user,
            String originalQuery,
            List<String> properNouns,
            List<String> normalTokens,
            LocalDate today) {

        // ✅ 先從爬蟲階段累積的 score 起跑（內文 match 等）
        double score = p.getScore();

        String title = p.getTitle() != null ? p.getTitle() : "";
        String titleLower = title.toLowerCase();

        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String contentLower = content.toLowerCase();

        // 1️⃣ 完整查詢匹配：標題 > 內文
        if (originalQuery != null && !originalQuery.isEmpty()) {
            String q = originalQuery.trim();
            if (!q.isEmpty()) {
                if (title.contains(q)) {
                    score += 50;
                    System.out.println("  🎯 完整匹配標題: " + title.substring(0, Math.min(30, title.length())));
                } else if (content.contains(q)) {
                    score += 25;
                }
            }
        }

        // 2️⃣ 專有名詞匹配：活動場地 / 校名 / 地標
        int properNounMatches = 0;
        for (String noun : properNouns) {
            String n = noun.toLowerCase();
            if (titleLower.contains(n)) {
                score += 15;
                properNounMatches++;
            } else if (contentLower.contains(n)) {
                score += 5;
                properNounMatches++;
            }
        }
        // 有指定專有名詞但完全沒對到 → 懲罰
        if (!properNouns.isEmpty() && properNounMatches == 0) {
            score -= 20;
        }

        // 3️⃣ 一般關鍵字匹配：標題 > 內文
        int normalMatches = 0;
        for (String token : normalTokens) {
            String t = token.toLowerCase();
            if (titleLower.contains(t)) {
                score += 3;
                normalMatches++;
            } else if (contentLower.contains(t)) {
                score += 1;
                normalMatches++;
            }
        }

        // 4️⃣ 匹配比例加成（越多關鍵字命中，加成越高）
        int totalTokens = properNouns.size() + normalTokens.size();
        int totalMatches = properNounMatches + normalMatches;
        if (totalTokens > 0) {
            double matchRatio = (double) totalMatches / totalTokens; // 0 ~ 1
            score *= (1.0 + matchRatio); // 最多多 100%
        }

        // 5️⃣ 日期新鮮度：未來加分、過期扣分
        LocalDate d = p.getEventDate();
        if (d != null) {
            long days = ChronoUnit.DAYS.between(today, d);
            if (days >= 0 && days <= 60) {
                score += 12;            // 兩個月內活動強力加分
            } else if (days > 60) {
                score += 4;             // 未來但較遠一點
            } else { // 已經過期
                score -= 18;            // 過期活動明顯扣分
            }
        }

        // 6️⃣ 內容品質：字數太少的頁面扣一點分
        int length = content.length();
        if (length < 300) {
            score *= 0.8;               // 內容太短，可信度低
        } else if (length > 2000) {
            score *= 1.05;              // 內容完整稍微加分
        }

        // 7️⃣ 來源：大部分網站一律 1.0，只給文化觀光 gov.tw 一點點加成
        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";
        double sourceMultiplier = calculateSourceMultiplier(domain);
        score *= sourceMultiplier;

        // 8️⃣ 申請 / 辦法類頁面懲罰（用 title + URL 判斷）
        double contentMultiplier = calculateContentMultiplier(title, p.getUrl());
        score *= contentMultiplier;

        // 9️⃣ 活動類型加成（只取第一個命中的）
        for (Map.Entry<String, Double> entry : EVENT_TYPE_BOOST.entrySet()) {
            if (titleLower.contains(entry.getKey().toLowerCase())) {
                score *= entry.getValue();
                break;
            }
        }

        // 🔟 城市匹配：同城大加分，異城明顯降分；「全台」不調整
        String userCity = (user != null) ? user.getUserCity() : null;
        String pageCity = p.getCity();

        if (userCity != null && pageCity != null && !pageCity.isEmpty()
                && !"全台".equals(pageCity)) {

            if (pageCity.equals(userCity)) {
                // 同城市：加大權重，讓本地活動優先出現在前面
                score *= 1.4;
            } else {
                // 不同城市：明顯降低權重
                score *= 0.6;
            }
        }

        return Math.max(0.1, score);
    }

    /**
     * 平台幾乎平等：
     * - 預設 1.0
     * - 文化 / 觀光相關的政府網站：1.1（僅微小加分）
     */
    private static double calculateSourceMultiplier(String domain) {
        if (domain == null || domain.isEmpty()) return 1.0;
        String d = domain.toLowerCase();

        if (d.endsWith(".gov.tw")) {
            if (d.contains("culture") || d.contains("moc") || d.contains("tourism")) {
                return 1.1;   // 文化 / 觀光單位官網：資訊較準
            }
            return 1.0;       // 其他政府網站：不特別加減
        }

        // 其他一律平等
        return 1.0;
    }

    /**
     * 申請 / 辦法類頁面懲罰
     */
    private static double calculateContentMultiplier(String title, String url) {
        String t = title != null ? title.toLowerCase() : "";
        String u = url != null ? url.toLowerCase() : "";

        for (String keyword : APPLICATION_KEYWORDS) {
            if (t.contains(keyword) || u.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        return 1.0;
    }

    /**
     * 將分數標準化到 10~100 之間，方便前端顯示
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
