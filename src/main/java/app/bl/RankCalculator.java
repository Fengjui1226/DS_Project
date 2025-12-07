package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RankCalculator v3.2 - 所有來源平等處理
 */
public class RankCalculator {

    private static final Map<String, Double> OFFICIAL_VENUES = Map.of(
        "npac-ntch.org", 1.7,
        "tmc.gov.tw", 1.6,
        "tfam.museum", 1.6,
        "npm.gov.tw", 1.6,
        "huashan1914.com", 1.6,
        "songyanculture.taipei", 1.6,
        "legacy.com.tw", 1.5
    );

    private static final Set<String> PROPER_NOUNS = Set.copyOf(new HashSet<>(List.of(
        "政大", "台大", "師大", "清大", "交大", "成大", "中央", "中山", "中興", "北大",
        "輔大", "東吳", "淡江", "文化", "銘傳", "世新", "實踐", "逢甲", "元智", "長庚",
        "華山", "松菸", "駁二", "小巨蛋", "大巨蛋", "國家音樂廳", "兩廳院", "故宮",
        "北美館", "當代藝術館", "科博館", "科工館", "海生館",
        "簡單生活節", "大港開唱", "覺醒音樂祭", "春浪", "貢寮海洋音樂祭",
        "信義區", "西門", "東區", "大安", "士林"
    )));

    private static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件", 
        "招標", "採購", "規定", "須知", "下載"
    );
    private static final double APPLICATION_PENALTY = 0.1;

    private static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("個展", 1.2),
        Map.entry("市集", 1.3), Map.entry("夜市", 1.2),
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        Map.entry("派對", 1.2), Map.entry("路跑", 1.3),
        Map.entry("親子", 1.2), Map.entry("免費", 1.1)
    );

    public static void rank(List<PageNode> pages, UserProfile user, String originalQuery) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數...");
        System.out.println("📝 原始查詢: " + originalQuery);

        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }

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

        for (PageNode p : pages) {
            double score = calculatePageScore(p, user, originalQuery, 
                    properNounTokens, normalTokens, today);
            p.setTotalScore(score);
        }

        normalizeScores(pages);
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));

        System.out.println("✅ 排名計算完成");
    }

    public static void rank(List<PageNode> pages, UserProfile user) {
        rank(pages, user, null);
    }

    private static boolean isProperNoun(String token) {
        for (String noun : PROPER_NOUNS) {
            if (token.contains(noun) || noun.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static double calculatePageScore(PageNode p, UserProfile user,
            String originalQuery, List<String> properNouns, 
            List<String> normalTokens, LocalDate today) {

        double score = 0;
        String title = p.getTitle() != null ? p.getTitle() : "";
        String titleLower = title.toLowerCase();
        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String contentLower = content.toLowerCase();

        if (originalQuery != null && !originalQuery.isEmpty()) {
            if (title.contains(originalQuery)) {
                score += 50;
                System.out.println("  🎯 完整匹配標題: " + title.substring(0, Math.min(30, title.length())));
            } else if (content.contains(originalQuery)) {
                score += 25;
            }
        }

        int properNounMatches = 0;
        for (String noun : properNouns) {
            if (titleLower.contains(noun.toLowerCase())) {
                score += 15;
                properNounMatches++;
            } else if (contentLower.contains(noun.toLowerCase())) {
                score += 5;
                properNounMatches++;
            }
        }

        if (!properNouns.isEmpty() && properNounMatches == 0) {
            score -= 20;
        }

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

        int totalTokens = properNouns.size() + normalTokens.size();
        int totalMatches = properNounMatches + normalMatches;
        if (totalTokens > 0) {
            double matchRatio = (double) totalMatches / totalTokens;
            score *= (1 + matchRatio);
        }

        LocalDate d = p.getEventDate();
        if (d != null) {
            long days = ChronoUnit.DAYS.between(today, d);
            if (days >= 0 && days < 60) {
                score += 10;
            } else if (days >= 0) {
                score += 3;
            } else {
                score -= 15;
            }
        }

        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";
        double sourceMultiplier = calculateSourceMultiplier(domain);
        score *= sourceMultiplier;

        double contentMultiplier = calculateContentMultiplier(title, p.getUrl());
        score *= contentMultiplier;

        for (Map.Entry<String, Double> entry : EVENT_TYPE_BOOST.entrySet()) {
            if (titleLower.contains(entry.getKey().toLowerCase())) {
                score *= entry.getValue();
                break;
            }
        }

        String userCity = user != null ? user.getUserCity() : null;
        if (userCity != null && p.getCity() != null && p.getCity().equals(userCity)) {
            score *= 1.2;
        }

        return Math.max(0.1, score);
    }

    private static double calculateSourceMultiplier(String domain) {
        for (Map.Entry<String, Double> entry : OFFICIAL_VENUES.entrySet()) {
            if (domain.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
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
        for (String keyword : APPLICATION_KEYWORDS) {
            if (t.contains(keyword) || u.contains(keyword)) {
                return APPLICATION_PENALTY;
            }
        }
        return 1.0;
    }

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