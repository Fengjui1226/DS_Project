package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.bl.Constants.*;

/**
 * RankCalculator v7.1 - 最終權威優化版
 * * 新增：
 * 1. [權威加權] 針對 Accupass, Opentix 等給予 1.3 倍加分 (不含 Klook/KKday)。
 * 2. [資訊加權] 成功解析日期的頁面給予 1.2 倍加分。
 * 3. [常數整合] 使用 Constants 中的 NOISE_KEYWORDS 進行過濾。
 */
public class RankCalculator {

    // ================= 權重設定 =================
    private static final double W_EXACT_TITLE = 50.0;
    private static final double W_EXACT_CONTENT = 25.0;
    private static final double W_PROPER_NOUN_TITLE = 15.0;
    private static final double W_PROPER_NOUN_CONTENT = 5.0;
    private static final double W_KEYWORD_TITLE = 20.0;
    private static final double W_KEYWORD_CONTENT = 5.0;
    
    private static final double P_NO_PROPER_NOUN = -10.0;
    private static final double P_MISSING_INTENT = -50.0;
    private static final double P_FOREIGN_CONTENT = -50.0;

    private static final double W_DATE_FRESH = 20.0;
    private static final double W_DATE_FUTURE = 10.0;
    private static final double W_DATE_QUERY_MATCH = 40.0;

    // ================= 乘數加成 (Multipliers) =================
    private static final double M_CITY_MATCH = 1.3;         // 城市匹配很重要
    private static final double M_CITY_MISMATCH = 0.5;      // 城市不對扣分重
    private static final double M_AUTHORITY_DOMAIN = 1.3;   // ★ 新增：權威網域加成 (1.3倍)
    private static final double M_INFO_RICH = 1.2;          // ★ 新增：有日期資訊加成 (1.2倍)
    private static final double M_SOURCE_GOV = 1.1;         // 政府網域微幅加成
    private static final double M_APPLICATION_PENALTY = 0.2;// 申請類打2折
    private static final double M_NOISE_PENALTY = 0.05;     // 垃圾內容打0.5折 (幾乎殺死)
    private static final double M_EXPIRED_PENALTY = 0.0;    // 過期直接處決

    /**
     * 主要排名入口
     */
    public static void rank(List<PageNode> pages, UserProfile user, String originalQuery) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數 (Formula V7.1 - Authority Boost)...");
        LocalDate today = LocalDate.now();

        List<String> queryTokens = new ArrayList<>();
        if (!pages.isEmpty()) queryTokens.addAll(pages.get(0).getTokens());

        List<String> properNounTokens = new ArrayList<>();
        List<String> normalTokens = new ArrayList<>();
        for (String token : queryTokens) {
            if (isProperNoun(token)) properNounTokens.add(token);
            else normalTokens.add(token);
        }

        for (PageNode p : pages) {
            double finalScore = calculatePageScore(p, user, originalQuery, properNounTokens, normalTokens, today);
            p.setTotalScore(finalScore);
        }

        normalizeScores(pages);
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        System.out.println("✅ 排名計算完成");
    }

    public static void rank(List<PageNode> pages, UserProfile user) {
        rank(pages, user, null);
    }

    private static double calculatePageScore(
            PageNode p, UserProfile user, String originalQuery,
            List<String> properNouns, List<String> normalTokens, LocalDate today) {

        double baseScore = p.getScore();
        double textScore = calculateTextScore(p, originalQuery, properNouns, normalTokens);
        double dateScore = calculateDateScore(p, originalQuery, today);
        
        double multiplier = calculateMultiplier(p, user, today);

        double finalScore = (baseScore + textScore + dateScore) * multiplier;
        return Math.max(0.0, finalScore);
    }

    private static double calculateMultiplier(PageNode p, UserProfile user, LocalDate today) {
        double multiplier = 1.0;
        String title = p.getTitle() != null ? p.getTitle() : "";
        String url = p.getUrl() != null ? p.getUrl().toLowerCase() : "";
        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";
        
        // 1. 過期處決
        LocalDate eventDate = p.getEventDate();
        if (eventDate != null) {
            if (eventDate.isBefore(today)) return M_EXPIRED_PENALTY;
            
            // ★ 如果成功解析出日期，代表這是高品質活動頁，給予加分
            multiplier *= M_INFO_RICH;
        }

        // 2. 標題年份檢查 (針對沒有 eventDate 的頁面)
        int currentYear = today.getYear(); 
        Pattern yearPattern = Pattern.compile("20[12][0-9]");
        Matcher m = yearPattern.matcher(title);
        while (m.find()) {
            int titleYear = Integer.parseInt(m.group());
            if (titleYear < currentYear) {
                boolean mentionsFuture = title.contains(String.valueOf(currentYear)) || 
                                         title.contains(String.valueOf(currentYear + 1));
                if (!mentionsFuture) return M_EXPIRED_PENALTY;
            }
        }

        // 3. 垃圾內容過濾 (使用 Constants 中的黑名單)
        for (String noise : NOISE_KEYWORDS) {
            if (title.contains(noise)) return M_NOISE_PENALTY;
        }

        // 4. ★ 權威網域加成 (使用 Constants 中的白名單)
        // Klook/KKday 不在名單內，所以不會加分
        for (String authDomain : AUTHORITY_DOMAINS) {
            if (domain.contains(authDomain)) {
                multiplier *= M_AUTHORITY_DOMAIN;
                break;
            }
        }
        
        // 5. 政府網域加成
        if (domain.endsWith(".gov.tw") && 
           (domain.contains("culture") || domain.contains("moc") || domain.contains("tourism"))) {
            multiplier *= M_SOURCE_GOV;
        }

        // 6. 申請/辦法類懲罰
        for (String keyword : APPLICATION_KEYWORDS) {
            if (title.contains(keyword) || url.contains(keyword)) {
                multiplier *= M_APPLICATION_PENALTY;
                break;
            }
        }

        // 7. 活動類型加成
        for (var entry : EVENT_TYPE_BOOST.entrySet()) {
            if (title.contains(entry.getKey().toLowerCase())) {
                multiplier *= entry.getValue();
                break;
            }
        }

        // 8. 城市匹配
        String userCity = (user != null) ? user.getUserCity() : null;
        String pageCity = p.getCity();
        if (userCity != null && pageCity != null && !pageCity.isEmpty() && !"全台".equals(pageCity)) {
            if (pageCity.equals(userCity)) multiplier *= M_CITY_MATCH;
            else multiplier *= M_CITY_MISMATCH;
        }

        return multiplier;
    }

    private static double calculateDateScore(PageNode p, String originalQuery, LocalDate today) {
        double score = 0.0;
        LocalDate eventDate = p.getEventDate();
        if (eventDate != null) {
            long daysDiff = ChronoUnit.DAYS.between(today, eventDate);
            if (daysDiff >= 0 && daysDiff <= 60) score += W_DATE_FRESH;
            else if (daysDiff > 60) score += W_DATE_FUTURE;
            
            if (originalQuery != null) {
                QueryUnderstanding.DateRange queryDateRange = QueryUnderstanding.getDateRange(originalQuery);
                if (queryDateRange != null && queryDateRange.contains(eventDate)) {
                    score += W_DATE_QUERY_MATCH;
                }
            }
        }
        return score;
    }

    private static double calculateTextScore(PageNode p, String originalQuery, 
                                           List<String> properNouns, List<String> normalTokens) {
        double score = 0.0;
        String title = p.getTitle() != null ? p.getTitle() : "";
        String titleLower = title.toLowerCase();
        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String contentLower = content.toLowerCase();

        if (originalQuery != null && !originalQuery.trim().isEmpty()) {
            String q = originalQuery.trim();
            if (title.contains(q)) score += W_EXACT_TITLE;
            else if (content.contains(q)) score += W_EXACT_CONTENT;
        }

        int properMatches = 0;
        for (String noun : properNouns) {
            String n = noun.toLowerCase();
            if (titleLower.contains(n)) { score += W_PROPER_NOUN_TITLE; properMatches++; }
            else if (contentLower.contains(n)) { score += W_PROPER_NOUN_CONTENT; properMatches++; }
        }
        if (!properNouns.isEmpty() && properMatches == 0) score += P_NO_PROPER_NOUN;

        int normalMatches = 0;
        for (String token : normalTokens) {
            String t = token.toLowerCase();
            if (titleLower.contains(t)) { score += W_KEYWORD_TITLE; normalMatches++; }
            else if (contentLower.contains(t)) { score += W_KEYWORD_CONTENT; normalMatches++; }
        }

        if (!normalTokens.isEmpty() && normalMatches == 0) score += P_MISSING_INTENT;

        int totalTokens = properNouns.size() + normalTokens.size();
        if (totalTokens > 0) {
            double ratio = (double) (properMatches + normalMatches) / totalTokens;
            score *= (1.0 + ratio);
        }
        
        boolean isForeign = false;
        for (String fk : FOREIGN_KEYWORDS_CORE) {
            if (titleLower.contains(fk) || contentLower.contains(fk)) { isForeign = true; break; }
        }
        if (isForeign && !hasTaiwanLocation(title + " " + content)) score += P_FOREIGN_CONTENT;

        return score;
    }

    private static boolean isProperNoun(String token) {
        for (String noun : PROPER_NOUNS) {
            if (token.contains(noun) || noun.contains(token)) return true;
        }
        return false;
    }

    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        
        // 移除 0 分 (被處決) 的結果
        pages.removeIf(p -> p.getTotalScore() <= 0.01);
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