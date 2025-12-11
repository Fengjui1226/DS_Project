package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static app.bl.Constants.*;

/**
 * RankCalculator v5.0 - 優化版（公式化評分標準）
 * * 優化重點：
 * 1. [公式化] FinalScore = (Base + Text + Date) * Multiplier
 * 2. [可維護] 所有權重係數提取為常數
 * 3. [清晰] 邏輯拆分，不再是一團義大利麵程式碼
 */
public class RankCalculator {

    // ================= 1. 文字相關性權重 (Text Relevance) =================
    private static final double W_EXACT_TITLE = 50.0;       // 標題完全匹配
    private static final double W_EXACT_CONTENT = 25.0;     // 內文完全匹配
    private static final double W_PROPER_NOUN_TITLE = 15.0; // 專有名詞在標題
    private static final double W_PROPER_NOUN_CONTENT = 5.0;// 專有名詞在內文
    private static final double W_KEYWORD_TITLE = 3.0;      // 一般關鍵字在標題
    private static final double W_KEYWORD_CONTENT = 1.0;    // 一般關鍵字在內文
    
    private static final double P_NO_PROPER_NOUN = -20.0;   // 懲罰：完全沒提到專有名詞
    private static final double P_FOREIGN_CONTENT = -50.0;  // 懲罰：疑似海外內容

    // ================= 2. 時效性權重 (Freshness) =================
    private static final double W_DATE_FRESH = 12.0;        // 新鮮 (0-60天內)
    private static final double W_DATE_FUTURE = 4.0;        // 未來 (>60天)
    private static final double W_DATE_QUERY_MATCH = 25.0;  // 符合查詢指定日期
    private static final double P_DATE_EXPIRED = -18.0;     // 過期

    // ================= 3. 乘數加成 (Multipliers) =================
    private static final double M_CITY_MATCH = 1.2;         // 城市匹配加成
    private static final double M_CITY_MISMATCH = 0.8;      // 城市不匹配降權
    private static final double M_LENGTH_LONG = 1.05;       // 長文加成 (>2000字)
    private static final double M_LENGTH_SHORT = 0.8;       // 短文降權 (<300字)
    private static final double M_SOURCE_GOV = 1.1;         // 政府網站加成
    private static final double M_APPLICATION_PENALTY = 0.3;// 申請/辦法類大幅降權

    /**
     * 主要排名入口
     */
    public static void rank(List<PageNode> pages, UserProfile user, String originalQuery) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n🎯 開始計算排名分數 (Formula V1.0)...");
        System.out.println("📝 原始查詢: " + originalQuery);

        // 1. 準備 Token (從第一個結果取得查詢分析後的 Token)
        List<String> queryTokens = new ArrayList<>();
        if (!pages.isEmpty()) {
            queryTokens.addAll(pages.get(0).getTokens());
        }

        // 2. 分類 Token
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

        // 3. 套用公式計算
        for (PageNode p : pages) {
            double finalScore = calculatePageScore(p, user, originalQuery, properNounTokens, normalTokens, today);
            p.setTotalScore(finalScore);
        }

        // 4. 正規化與排序
        normalizeScores(pages);
        pages.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        System.out.println("✅ 排名計算完成");
    }

    public static void rank(List<PageNode> pages, UserProfile user) {
        rank(pages, user, null);
    }

    /**
     * 核心評分公式
     * Formula: Score = (Base + TextRelevance + DateRelevance) * Multipliers
     */
    private static double calculatePageScore(
            PageNode p, UserProfile user, String originalQuery,
            List<String> properNouns, List<String> normalTokens, LocalDate today) {

        // A. 基礎分數 (來自 TF-IDF 與 InfoExtractor 的預處理)
        double baseScore = p.getScore();

        // B. 文字相關性 (Relevance)
        double textScore = calculateTextScore(p, originalQuery, properNouns, normalTokens);

        // C. 日期與時效 (Freshness)
        double dateScore = calculateDateScore(p, originalQuery, today);

        // D. 綜合乘數 (Multipliers - 內容品質、來源、個人化)
        double multiplier = calculateMultiplier(p, user);

        // E. 最終計算
        double finalScore = (baseScore + textScore + dateScore) * multiplier;

        // 確保分數不為負或過小
        return Math.max(0.1, finalScore);
    }

    // ================= 子計算邏輯 =================

    private static double calculateTextScore(PageNode p, String originalQuery, 
                                           List<String> properNouns, List<String> normalTokens) {
        double score = 0.0;
        String title = p.getTitle() != null ? p.getTitle() : "";
        String titleLower = title.toLowerCase();
        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String contentLower = content.toLowerCase();

        // 1. 完整查詢匹配
        if (originalQuery != null && !originalQuery.trim().isEmpty()) {
            String q = originalQuery.trim();
            if (title.contains(q)) score += W_EXACT_TITLE;
            else if (content.contains(q)) score += W_EXACT_CONTENT;
        }

        // 2. 專有名詞匹配
        int properMatches = 0;
        for (String noun : properNouns) {
            String n = noun.toLowerCase();
            if (titleLower.contains(n)) {
                score += W_PROPER_NOUN_TITLE;
                properMatches++;
            } else if (contentLower.contains(n)) {
                score += W_PROPER_NOUN_CONTENT;
                properMatches++;
            }
        }
        if (!properNouns.isEmpty() && properMatches == 0) {
            score += P_NO_PROPER_NOUN;
        }

        // 3. 一般關鍵字匹配
        int normalMatches = 0;
        for (String token : normalTokens) {
            String t = token.toLowerCase();
            if (titleLower.contains(t)) {
                score += W_KEYWORD_TITLE;
                normalMatches++;
            } else if (contentLower.contains(t)) {
                score += W_KEYWORD_CONTENT;
                normalMatches++;
            }
        }

        // 4. 匹配密度加成 (Matching Ratio Bonus)
        int totalTokens = properNouns.size() + normalTokens.size();
        if (totalTokens > 0) {
            double ratio = (double) (properMatches + normalMatches) / totalTokens;
            score *= (1.0 + ratio); // 最高 2倍
        }

        // 5. 海外內容懲罰 (檢查標題與內文)
        boolean isForeign = false;
        for (String fk : FOREIGN_KEYWORDS_CORE) {
            if (titleLower.contains(fk) || contentLower.contains(fk)) {
                isForeign = true;
                break;
            }
        }
        // 如果疑似海外且沒有台灣地名，給予懲罰
        if (isForeign && !hasTaiwanLocation(title + " " + content)) {
            score += P_FOREIGN_CONTENT;
        }

        return score;
    }

    private static double calculateDateScore(PageNode p, String originalQuery, LocalDate today) {
        double score = 0.0;
        LocalDate eventDate = p.getEventDate();
        
        if (eventDate != null) {
            long daysDiff = ChronoUnit.DAYS.between(today, eventDate);
            
            // 基礎時效分
            if (daysDiff >= 0 && daysDiff <= 60) {
                score += W_DATE_FRESH;
            } else if (daysDiff > 60) {
                score += W_DATE_FUTURE;
            } else {
                score += P_DATE_EXPIRED;
            }
            
            // 查詢意圖日期匹配
            if (originalQuery != null) {
                QueryUnderstanding.DateRange queryDateRange = QueryUnderstanding.getDateRange(originalQuery);
                if (queryDateRange != null && queryDateRange.contains(eventDate)) {
                    score += W_DATE_QUERY_MATCH;
                }
            }
        }
        return score;
    }

    private static double calculateMultiplier(PageNode p, UserProfile user) {
        double multiplier = 1.0;
        String title = p.getTitle() != null ? p.getTitle().toLowerCase() : "";
        String url = p.getUrl() != null ? p.getUrl().toLowerCase() : "";
        String content = p.getTextContent() != null ? p.getTextContent() : "";
        String domain = p.getDomain() != null ? p.getDomain().toLowerCase() : "";

        // 1. 內容長度
        int length = content.length();
        if (length < 300) multiplier *= M_LENGTH_SHORT;
        else if (length > 2000) multiplier *= M_LENGTH_LONG;

        // 2. 來源權威性
        if (domain.endsWith(".gov.tw") && 
           (domain.contains("culture") || domain.contains("moc") || domain.contains("tourism"))) {
            multiplier *= M_SOURCE_GOV;
        }

        // 3. 排除申請/辦法類 (垃圾資訊過濾)
        for (String keyword : APPLICATION_KEYWORDS) {
            if (title.contains(keyword) || url.contains(keyword)) {
                multiplier *= M_APPLICATION_PENALTY;
                break; // 只要中一個就降權
            }
        }

        // 4. 活動類型加成 (從 Constants 讀取)
        for (var entry : EVENT_TYPE_BOOST.entrySet()) {
            if (title.contains(entry.getKey().toLowerCase())) {
                multiplier *= entry.getValue();
                break;
            }
        }

        // 5. 使用者城市匹配 (Personalization)
        String userCity = (user != null) ? user.getUserCity() : null;
        String pageCity = p.getCity();
        
        if (userCity != null && pageCity != null && !pageCity.isEmpty() && !"全台".equals(pageCity)) {
            if (pageCity.equals(userCity)) {
                multiplier *= M_CITY_MATCH;
            } else {
                multiplier *= M_CITY_MISMATCH;
            }
        }

        return multiplier;
    }

    // ================= 工具方法 =================

    private static boolean isProperNoun(String token) {
        for (String noun : PROPER_NOUNS) {
            if (token.contains(noun) || noun.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static void normalizeScores(List<PageNode> pages) {
        if (pages.isEmpty()) return;
        double maxScore = pages.stream().mapToDouble(PageNode::getTotalScore).max().orElse(1.0);
        double minScore = pages.stream().mapToDouble(PageNode::getTotalScore).min().orElse(0.0);
        double range = maxScore - minScore;
        if (range < 0.001) range = 1.0;

        for (PageNode p : pages) {
            // 正規化到 10-100 分
            double normalized = ((p.getTotalScore() - minScore) / range) * 90 + 10;
            p.setTotalScore(Math.round(normalized * 10) / 10.0);
        }
    }
}