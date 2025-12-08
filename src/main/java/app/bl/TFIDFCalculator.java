package app.bl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TFIDFCalculator v1.0 - 專業 TF-IDF 權重計算
 * 
 * 功能：
 * 1. 計算 Term Frequency (TF)
 * 2. 計算 Inverse Document Frequency (IDF)
 * 3. 計算 TF-IDF 權重分數
 * 4. 支援中文斷詞
 */
public class TFIDFCalculator {

    // 停用詞（不計入權重）
    private static final Set<String> STOP_WORDS = Set.of(
        // 中文停用詞
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一個",
        "上", "也", "很", "到", "說", "要", "去", "你", "會", "著", "沒有", "看", "好",
        "自己", "這", "那", "他", "她", "它", "們", "這個", "那個", "什麼", "怎麼",
        "可以", "沒", "把", "讓", "被", "比", "等", "能", "對", "與", "及", "或",
        // 英文停用詞
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "must", "shall", "can", "need", "dare",
        "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
        "into", "through", "during", "before", "after", "above", "below",
        "and", "but", "or", "nor", "so", "yet", "both", "either", "neither",
        "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they"
    );

    // 重要活動關鍵詞（給予額外 IDF boost）
    private static final Set<String> IMPORTANT_TERMS = Set.of(
        "演唱會", "音樂會", "音樂節", "展覽", "特展", "市集", "夜市",
        "講座", "工作坊", "派對", "路跑", "馬拉松", "親子", "免費",
        "售票", "票價", "門票", "報名", "活動", "表演", "演出",
        "concert", "exhibition", "festival", "market", "workshop", "event"
    );

    // ================= 主要 API =================

    /**
     * 計算一組文件的 TF-IDF 分數
     * @param documents 文件列表（每個文件是一個字串）
     * @param query 查詢字串
     * @return 每個文件的 TF-IDF 總分
     */
    public static List<Double> calculateScores(List<String> documents, String query) {
        if (documents == null || documents.isEmpty() || query == null) {
            return Collections.emptyList();
        }

        // 1. 對查詢斷詞
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.nCopies(documents.size(), 0.0);
        }

        // 2. 對所有文件斷詞
        List<List<String>> tokenizedDocs = new ArrayList<>();
        for (String doc : documents) {
            tokenizedDocs.add(tokenize(doc));
        }

        // 3. 計算 IDF（基於所有文件）
        Map<String, Double> idfMap = calculateIDF(tokenizedDocs, queryTerms);

        // 4. 計算每個文件的 TF-IDF 分數
        List<Double> scores = new ArrayList<>();
        for (List<String> docTokens : tokenizedDocs) {
            double score = calculateDocumentScore(docTokens, queryTerms, idfMap);
            scores.add(score);
        }

        return scores;
    }

    /**
     * 計算單一文件相對於查詢的 TF-IDF 分數
     * @param document 文件內容
     * @param query 查詢字串
     * @param corpusSize 語料庫大小（用於估算 IDF）
     * @param documentFrequencies 每個詞的文件頻率
     * @return TF-IDF 分數
     */
    public static double calculateScore(String document, String query,
                                        int corpusSize, Map<String, Integer> documentFrequencies) {
        if (document == null || query == null) return 0.0;

        List<String> queryTerms = tokenize(query);
        List<String> docTokens = tokenize(document);

        if (queryTerms.isEmpty() || docTokens.isEmpty()) return 0.0;

        // 計算 TF
        Map<String, Double> tfMap = calculateTF(docTokens);

        // 計算 TF-IDF
        double score = 0.0;
        for (String term : queryTerms) {
            double tf = tfMap.getOrDefault(term.toLowerCase(), 0.0);
            if (tf > 0) {
                int df = documentFrequencies.getOrDefault(term.toLowerCase(), 1);
                double idf = Math.log((double) corpusSize / df) + 1.0;

                // 重要詞彙加成
                if (IMPORTANT_TERMS.contains(term)) {
                    idf *= 1.5;
                }

                score += tf * idf;
            }
        }

        return score;
    }

    /**
     * 為 PageNode 列表計算 TF-IDF 分數並更新
     */
    public static void applyTFIDFScores(List<PageNode> pages, String query) {
        if (pages == null || pages.isEmpty() || query == null) return;

        System.out.println("\n📊 TF-IDF 計算中...");

        // 收集所有文件內容
        List<String> documents = new ArrayList<>();
        for (PageNode page : pages) {
            String content = buildDocumentContent(page);
            documents.add(content);
        }

        // 計算分數
        List<Double> scores = calculateScores(documents, query);

        // 套用分數（加到現有分數上）
        double maxScore = scores.stream().mapToDouble(d -> d).max().orElse(1.0);
        if (maxScore < 0.001) maxScore = 1.0;

        for (int i = 0; i < pages.size(); i++) {
            // 標準化到 0-30 分範圍，加到現有分數
            double normalizedScore = (scores.get(i) / maxScore) * 30;
            pages.get(i).addScore(normalizedScore);
        }

        System.out.println("✅ TF-IDF 分數已套用");
    }

    // ================= 內部方法 =================

    /**
     * 中英文混合斷詞
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        String lower = text.toLowerCase();

        // 1. 提取英文單詞
        Pattern englishPattern = Pattern.compile("[a-z]+");
        Matcher englishMatcher = englishPattern.matcher(lower);
        while (englishMatcher.find()) {
            String word = englishMatcher.group();
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }

        // 2. 提取中文（用 bigram + 重要詞彙匹配）
        StringBuilder chinese = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chinese.append(c);
            } else if (chinese.length() > 0) {
                tokens.addAll(tokenizeChinese(chinese.toString()));
                chinese = new StringBuilder();
            }
        }
        if (chinese.length() > 0) {
            tokens.addAll(tokenizeChinese(chinese.toString()));
        }

        return tokens;
    }

    /**
     * 中文斷詞（bigram + 重要詞彙優先）
     */
    private static List<String> tokenizeChinese(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.length() < 2) return tokens;

        // 先找重要詞彙（完整匹配）
        Set<String> foundImportant = new HashSet<>();
        for (String term : IMPORTANT_TERMS) {
            if (text.contains(term)) {
                tokens.add(term);
                foundImportant.add(term);
            }
        }

        // 再用 bigram 補充
        for (int i = 0; i < text.length() - 1; i++) {
            String bigram = text.substring(i, i + 2);
            if (!STOP_WORDS.contains(bigram)) {
                // 避免重複加入已經在重要詞彙中的部分
                boolean skipBigram = false;
                for (String important : foundImportant) {
                    if (important.contains(bigram)) {
                        skipBigram = true;
                        break;
                    }
                }
                if (!skipBigram) {
                    tokens.add(bigram);
                }
            }
        }

        // Trigram 補充（長度 >= 4 時）
        if (text.length() >= 3) {
            for (int i = 0; i < text.length() - 2; i++) {
                String trigram = text.substring(i, i + 3);
                if (!STOP_WORDS.contains(trigram)) {
                    tokens.add(trigram);
                }
            }
        }

        return tokens;
    }

    private static boolean isChinese(char c) {
        return (c >= '\u4e00' && c <= '\u9fff') ||  // CJK Unified
               (c >= '\u3400' && c <= '\u4dbf');    // CJK Extension A
    }

    /**
     * 計算 Term Frequency（使用對數平滑）
     */
    private static Map<String, Double> calculateTF(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }

        int maxCount = counts.values().stream().mapToInt(i -> i).max().orElse(1);

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            // 使用對數 TF：1 + log(count)
            double logTF = 1.0 + Math.log(entry.getValue());
            // 標準化
            tf.put(entry.getKey(), logTF);
        }

        return tf;
    }

    /**
     * 計算 Inverse Document Frequency
     */
    private static Map<String, Double> calculateIDF(List<List<String>> documents, List<String> queryTerms) {
        Map<String, Double> idf = new HashMap<>();
        int N = documents.size();

        for (String term : queryTerms) {
            String termLower = term.toLowerCase();
            int df = 0;

            for (List<String> doc : documents) {
                if (doc.contains(termLower)) {
                    df++;
                }
            }

            // IDF = log(N / df) + 1（平滑處理）
            double idfValue = Math.log((double) N / Math.max(df, 1)) + 1.0;

            // 重要詞彙 boost
            if (IMPORTANT_TERMS.contains(term)) {
                idfValue *= 1.3;
            }

            idf.put(termLower, idfValue);
        }

        return idf;
    }

    /**
     * 計算單一文件的 TF-IDF 總分
     */
    private static double calculateDocumentScore(List<String> docTokens, List<String> queryTerms,
                                                  Map<String, Double> idfMap) {
        Map<String, Double> tf = calculateTF(docTokens);
        double score = 0.0;

        for (String term : queryTerms) {
            String termLower = term.toLowerCase();
            double termTF = tf.getOrDefault(termLower, 0.0);
            double termIDF = idfMap.getOrDefault(termLower, 1.0);
            score += termTF * termIDF;
        }

        return score;
    }

    /**
     * 從 PageNode 建立文件內容（標題權重 x3）
     */
    private static String buildDocumentContent(PageNode page) {
        StringBuilder sb = new StringBuilder();

        // 標題重複 3 次（增加標題權重）
        String title = page.getTitle();
        if (title != null) {
            sb.append(title).append(" ");
            sb.append(title).append(" ");
            sb.append(title).append(" ");
        }

        // 內文
        String content = page.getTextContent();
        if (content != null) {
            sb.append(content);
        }

        return sb.toString();
    }

    // ================= 工具方法 =================

    /**
     * 取得詞彙的文件頻率（用於單文件計算）
     */
    public static Map<String, Integer> buildDocumentFrequencies(List<PageNode> pages) {
        Map<String, Integer> df = new HashMap<>();

        for (PageNode page : pages) {
            String content = buildDocumentContent(page);
            Set<String> uniqueTerms = new HashSet<>(tokenize(content));

            for (String term : uniqueTerms) {
                df.merge(term, 1, Integer::sum);
            }
        }

        return df;
    }
}