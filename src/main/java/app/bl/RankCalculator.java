package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.bl.TextUtils.isNotEmpty;
import static app.bl.TextUtils.toLowerCase;
import static app.bl.TextUtils.truncate;
import app.bl.config.CityConfig;
import app.bl.config.EventTypeConfig;
import app.bl.config.FilterConfig;

/**
 * RankCalculator v9.0 - 重構版
 * 
 * 重構內容：
 * 1. 使用 TextUtils.truncate() 取代本地方法
 * 2. 使用新的 Config 類別
 * 
 * 評分公式：
 * 最終分數 = (baseScore + textScore + dateScore + freshnessScore) × multiplier
 */
public class RankCalculator {

    // ================= 文字匹配權重 =================
    private static final double W_EXACT_TITLE = 40.0;
    private static final double W_HALF_MATCH_TITLE = 25.0;
    private static final double W_PARTIAL_TITLE = 12.0;
    private static final double W_EXACT_CONTENT = 15.0;
    private static final double W_PARTIAL_CONTENT = 5.0;
    private static final double W_PROPER_NOUN_TITLE = 12.0;
    private static final double W_PROPER_NOUN_CONTENT = 4.0;
    
    // ================= 日期權重 =================
    private static final double W_DATE_SOON = 25.0;
    private static final double W_DATE_NEAR = 15.0;
    private static final double W_DATE_FUTURE = 8.0;
    private static final double W_DATE_QUERY_MATCH = 30.0;
    
    // ================= 新鮮度權重 =================
    private static final double W_GOOGLE_RANK_BASE = 10.0;
    private static final double W_GOOGLE_RANK_DECAY = 0.5;
    
    // ================= 懲罰 =================
    private static final double P_NO_KEYWORD_MATCH = -20.0;
    private static final double P_FOREIGN_CONTENT = -40.0;
    private static final double P_NO_DATE = -5.0;

    // ================= 乘數 =================
    private static final double M_CITY_MATCH = 1.25;
    private static final double M_CITY_MISMATCH = 0.6;
    private static final double M_INFO_RICH = 1.15;
    private static final double M_HAS_DATE_ONLY = 1.08;
    private static final double M_APPLICATION_PENALTY = 0.15;
    private static final double M_NOISE_PENALTY = 0.0;
    private static final double M_EXPIRED_PENALTY = 0.0;

    /**
     * 主要排名入口
     */
    public static void rank(List<PageNode> pages, UserProfile user, String originalQuery) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數 (Formula V9.0)...");
        LocalDate today = LocalDate.now();

        List<String> queryTokens = new ArrayList<>();
        if (!pages.isEmpty()) queryTokens.addAll(pages.get(0).getTokens());

        List<String> properNounTokens = new ArrayList<>();
        List<String> normalTokens = new ArrayList<>();
        for (String token : queryTokens) {
            if (isProperNoun(token)) properNounTokens.add(token);
            else normalTokens.add(token);
        }

        // 記錄 Google 原始排序
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setGoogleRank(i + 1);
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
        double freshnessScore = calculateFreshnessScore(p);
        double multiplier = calculateMultiplier(p, user, today);

        double finalScore = (baseScore + textScore + dateScore + freshnessScore) * multiplier;
        return Math.max(0.0, finalScore);
    }

    private static double calculateFreshnessScore(PageNode p) {
        int rank = p.getGoogleRank();
        if (rank <= 0) rank = 20;
        double score = W_GOOGLE_RANK_BASE - (rank - 1) * W_GOOGLE_RANK_DECAY;
        return Math.max(0, score);
    }

    private static double calculateMultiplier(PageNode p, UserProfile user, LocalDate today) {
        double multiplier = 1.0;
        String title = p.getTitle() != null ? p.getTitle() : "";
        String url = toLowerCase(p.getUrl());
        
        // 1. 過期處理
        LocalDate eventDate = p.getEventDate();
        if (eventDate != null) {
            if (eventDate.isBefore(today)) {
                System.out.println("  [EXPIRED] " + truncate(title, 30) + " | " + eventDate);
                return M_EXPIRED_PENALTY;
            }
            if (isNotEmpty(p.getCity())) {
                multiplier *= M_INFO_RICH;
            } else {
                multiplier *= M_HAS_DATE_ONLY;
            }
        }

        // 2. 標題年份檢查
        int currentYear = today.getYear();
        Pattern oldEventPattern = Pattern.compile("(201[0-9]|202[0-3])(?:年)?(?:全台|精選|最新|整理|市集|展覽|演唱會|音樂節)");
        Matcher m = oldEventPattern.matcher(title);
        if (m.find()) {
            int foundYear = Integer.parseInt(m.group(1));
            if (foundYear < currentYear - 1) {
                System.out.println("  [OLD-YEAR] " + truncate(title, 30) + " | " + foundYear + "年");
                return M_EXPIRED_PENALTY;
            }
        }

        // 3. 垃圾內容過濾（使用 FilterConfig）
        if (FilterConfig.isNoise(title)) {
            System.out.println("  [NOISE] " + truncate(title, 30));
            return M_NOISE_PENALTY;
        }

        // 4. 申請類懲罰（使用 FilterConfig）
        if (FilterConfig.isApplicationContent(title) || FilterConfig.isApplicationContent(url)) {
            multiplier *= M_APPLICATION_PENALTY;
        }

        // 5. 活動類型加成（使用 EventTypeConfig）
        for (var entry : EventTypeConfig.EVENT_TYPE_BOOST.entrySet()) {
            if (title.contains(entry.getKey())) {
                multiplier *= entry.getValue();
                break;
            }
        }

        // 6. 城市匹配
        String userCity = (user != null) ? user.getUserCity() : null;
        String pageCity = p.getCity();
        if (userCity != null && isNotEmpty(pageCity) && !"全台".equals(pageCity)) {
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
            if (daysDiff >= 0 && daysDiff <= 30) {
                score += W_DATE_SOON;
            } else if (daysDiff > 30 && daysDiff <= 90) {
                score += W_DATE_NEAR;
            } else if (daysDiff > 90) {
                score += W_DATE_FUTURE;
            }
            
            if (originalQuery != null) {
                QueryUnderstanding.DateRange queryDateRange = QueryUnderstanding.getDateRange(originalQuery);
                if (queryDateRange != null && queryDateRange.contains(eventDate)) {
                    score += W_DATE_QUERY_MATCH;
                }
            }
        } else {
            score += P_NO_DATE;
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

        // 1. 完全匹配檢查
        if (isNotEmpty(originalQuery)) {
            String q = originalQuery.trim().toLowerCase();
            if (titleLower.contains(q)) {
                score += W_EXACT_TITLE;
            } else if (contentLower.contains(q)) {
                score += W_EXACT_CONTENT;
            }
        }

        // 2. 部分匹配檢查
        List<String> allTokens = new ArrayList<>();
        allTokens.addAll(properNouns);
        allTokens.addAll(normalTokens);
        
        int titleMatches = 0;
        int contentMatches = 0;
        
        for (String token : allTokens) {
            String t = token.toLowerCase();
            if (titleLower.contains(t)) {
                titleMatches++;
            } else if (contentLower.contains(t)) {
                contentMatches++;
            }
        }
        
        if (!allTokens.isEmpty()) {
            double matchRatio = (double) titleMatches / allTokens.size();
            if (matchRatio >= 0.5) {
                score += W_HALF_MATCH_TITLE;
            } else if (titleMatches > 0) {
                score += W_PARTIAL_TITLE;
            }
            
            if (contentMatches > 0) {
                score += W_PARTIAL_CONTENT * Math.min(contentMatches, 3);
            }
            
            if (titleMatches == 0 && contentMatches == 0) {
                score += P_NO_KEYWORD_MATCH;
            }
        }

        // 3. 專有名詞額外加分（使用 FilterConfig）
        for (String noun : properNouns) {
            String n = noun.toLowerCase();
            if (titleLower.contains(n)) {
                score += W_PROPER_NOUN_TITLE;
            } else if (contentLower.contains(n)) {
                score += W_PROPER_NOUN_CONTENT;
            }
        }
        
        // 4. 海外內容懲罰（使用 FilterConfig + CityConfig）
        String combined = title + " " + content;
        if (FilterConfig.isForeign(combined) && !CityConfig.hasTaiwanLocation(combined)) {
            score += P_FOREIGN_CONTENT;
        }

        return score;
    }

    private static boolean isProperNoun(String token) {
        return FilterConfig.hasProperNoun(null, token);
    }

    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        
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