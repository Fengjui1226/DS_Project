package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static app.bl.Constants.*;

/**
 * RankCalculator v4.0 - 重構版（使用統一常數）
 */
public class RankCalculator {

    private static final double APPLICATION_PENALTY = 0.3;

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
            double score = calculatePageScore(p, user, originalQuery, properNounTokens, normalTokens, today);
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

    private static double calculatePageScore(
            PageNode p, UserProfile user, String originalQuery,
            List<String> properNouns, List<String> normalTokens, LocalDate today) {

        double score = p.getScore();
        String title = p.getTitle() != null ? p.getTitle() : "";
        String titleLower = title.toLowerCase();
        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String contentLower = content.toLowerCase();

        // 1. 完整查詢匹配
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

        // 2. 專有名詞匹配
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
        if (!properNouns.isEmpty() && properNounMatches == 0) {
            score -= 20;
        }

        // 2.5 海外內容懲罰
        boolean isForeign = false;
        for (String fk : FOREIGN_KEYWORDS_CORE) {
            if (titleLower.contains(fk) || contentLower.contains(fk)) {
                isForeign = true;
                break;
            }
        }
        if (isForeign && !hasTaiwanLocation(title + " " + content)) {
            score -= 50;
            System.out.println("  ⚠️ 發現疑似海外內容扣分: " + title);
        }

        // 3. 一般關鍵字匹配
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

        // 4. 匹配比例加成
        int totalTokens = properNouns.size() + normalTokens.size();
        int totalMatches = properNounMatches + normalMatches;
        if (totalTokens > 0) {
            double matchRatio = (double) totalMatches / totalTokens;
            score *= (1.0 + matchRatio);
        }

        // 5. 日期新鮮度
        LocalDate d = p.getEventDate();
        if (d != null) {
            long days = ChronoUnit.DAYS.between(today, d);
            if (days >= 0 && days <= 60) {
                score += 12;
            } else if (days > 60) {
                score += 4;
            } else {
                score -= 18;
            }
            
            if (originalQuery != null) {
                QueryUnderstanding.DateRange queryDateRange = QueryUnderstanding.getDateRange(originalQuery);
                if (queryDateRange != null && queryDateRange.contains(d)) {
                    score += 25;
                }
            }
        }

        // 6. 內容品質
        int length = content.length();
        if (length < 300) {
            score *= 0.8;
        } else if (length > 2000) {
            score *= 1.05;
        }

        // 7. 來源權重
        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";
        score *= calculateSourceMultiplier(domain);

        // 8. 申請/辦法類懲罰
        score *= calculateContentMultiplier(title, p.getUrl());

        // 9. 活動類型加成
        for (var entry : EVENT_TYPE_BOOST.entrySet()) {
            if (titleLower.contains(entry.getKey().toLowerCase())) {
                score *= entry.getValue();
                break;
            }
        }

        // 10. 城市匹配
        String userCity = (user != null) ? user.getUserCity() : null;
        String pageCity = p.getCity();
        if (userCity != null && pageCity != null && !pageCity.isEmpty() && !"全台".equals(pageCity)) {
            if (pageCity.equals(userCity)) {
                score *= 1.2;
            } else {
                score *= 0.8;
            }
        }

        return Math.max(0.1, score);
    }

    private static double calculateSourceMultiplier(String domain) {
        if (domain == null || domain.isEmpty()) return 1.0;
        if (domain.endsWith(".gov.tw")) {
            if (domain.contains("culture") || domain.contains("moc") || domain.contains("tourism")) {
                return 1.1;
            }
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
        double maxScore = pages.stream().mapToDouble(PageNode::getTotalScore).max().orElse(1.0);
        double minScore = pages.stream().mapToDouble(PageNode::getTotalScore).min().orElse(0.0);
        double range = maxScore - minScore;
        if (range < 0.001) range = 1.0;

        for (PageNode p : pages) {
            double normalized = ((p.getTotalScore() - minScore) / range) * 90 + 10;
            p.setTotalScore(Math.round(normalized * 10) / 10.0);
        }
    }
}