package app.bl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deduplicator v1.0 - 搜尋結果去重
 * 
 * 功能：
 * 1. SimHash 指紋計算
 * 2. 標題相似度比對
 * 3. URL 正規化去重
 * 4. 內容相似度聚類
 * 5. 合併相似結果（保留最佳來源）
 */
public class Deduplicator {

    // 相似度閾值
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.7;   // 標題相似度閾值
    private static final int SIMHASH_HAMMING_THRESHOLD = 5;         // SimHash 漢明距離閾值
    private static final double CONTENT_SIMILARITY_THRESHOLD = 0.6; // 內容相似度閾值

    // ================= 主要 API =================

    /**
     * 對搜尋結果去重
     * @param pages 原始搜尋結果
     * @return 去重後的結果
     */
    public static List<PageNode> deduplicate(List<PageNode> pages) {
        if (pages == null || pages.size() <= 1) {
            return pages;
        }

        System.out.println("\n🔄 去重處理中...");
        System.out.println("原始結果數: " + pages.size());

        // 1. URL 正規化去重
        List<PageNode> afterUrlDedup = deduplicateByUrl(pages);
        System.out.println("URL 去重後: " + afterUrlDedup.size());

        // 2. 標題相似度去重
        List<PageNode> afterTitleDedup = deduplicateByTitle(afterUrlDedup);
        System.out.println("標題去重後: " + afterTitleDedup.size());

        // 3. 內容相似度去重（使用 SimHash）
        List<PageNode> afterContentDedup = deduplicateByContent(afterTitleDedup);
        System.out.println("內容去重後: " + afterContentDedup.size());

        int removed = pages.size() - afterContentDedup.size();
        System.out.println("✅ 去重完成，移除 " + removed + " 個重複結果");

        return afterContentDedup;
    }

    /**
     * 快速去重（只用標題，較快）
     */
    public static List<PageNode> quickDeduplicate(List<PageNode> pages) {
        if (pages == null || pages.size() <= 1) {
            return pages;
        }

        List<PageNode> afterUrlDedup = deduplicateByUrl(pages);
        return deduplicateByTitle(afterUrlDedup);
    }

    // ================= URL 去重 =================

    private static List<PageNode> deduplicateByUrl(List<PageNode> pages) {
        Map<String, PageNode> urlMap = new LinkedHashMap<>();

        for (PageNode page : pages) {
            String normalizedUrl = normalizeUrl(page.getUrl());
            
            if (!urlMap.containsKey(normalizedUrl)) {
                urlMap.put(normalizedUrl, page);
            } else {
                // 保留分數較高的
                PageNode existing = urlMap.get(normalizedUrl);
                if (page.getScore() > existing.getScore()) {
                    urlMap.put(normalizedUrl, page);
                }
            }
        }

        return new ArrayList<>(urlMap.values());
    }

    /**
     * URL 正規化
     */
    private static String normalizeUrl(String url) {
        if (url == null) return "";

        String normalized = url.toLowerCase()
            .replaceAll("^https?://", "")           // 移除協議
            .replaceAll("^www\\.", "")              // 移除 www
            .replaceAll("/$", "")                   // 移除結尾斜線
            .replaceAll("#.*$", "")                 // 移除 anchor
            .replaceAll("\\?utm_.*", "")            // 移除 UTM 參數
            .replaceAll("\\?fbclid=.*", "")         // 移除 FB 追蹤參數
            .replaceAll("\\?ref=.*", "");           // 移除 ref 參數

        // 移除常見的追蹤參數
        normalized = normalized.replaceAll("[?&](utm_|fbclid|gclid|ref=)[^&]*", "");

        return normalized;
    }

    // ================= 標題去重 =================

    private static List<PageNode> deduplicateByTitle(List<PageNode> pages) {
        List<PageNode> result = new ArrayList<>();
        Set<Integer> merged = new HashSet<>();

        for (int i = 0; i < pages.size(); i++) {
            if (merged.contains(i)) continue;

            PageNode current = pages.get(i);
            String currentTitle = normalizeTitle(current.getTitle());

            // 找所有相似的
            List<PageNode> similar = new ArrayList<>();
            similar.add(current);

            for (int j = i + 1; j < pages.size(); j++) {
                if (merged.contains(j)) continue;

                PageNode other = pages.get(j);
                String otherTitle = normalizeTitle(other.getTitle());

                double similarity = calculateTitleSimilarity(currentTitle, otherTitle);
                if (similarity >= TITLE_SIMILARITY_THRESHOLD) {
                    similar.add(other);
                    merged.add(j);
                }
            }

            // 從相似結果中選最佳
            PageNode best = selectBest(similar);
            result.add(best);
            merged.add(i);
        }

        return result;
    }

    /**
     * 標題正規化
     */
    private static String normalizeTitle(String title) {
        if (title == null) return "";

        return title.toLowerCase()
            .replaceAll("[\\s\\-_|｜–—]+", " ")     // 統一分隔符
            .replaceAll("[【】\\[\\]\\(\\)（）]", "") // 移除括號
            .replaceAll("\\s+", " ")                // 合併空白
            .trim();
    }

    /**
     * 計算標題相似度（Jaccard + 編輯距離混合）
     */
    private static double calculateTitleSimilarity(String t1, String t2) {
        if (t1.isEmpty() || t2.isEmpty()) return 0.0;

        // 完全相同
        if (t1.equals(t2)) return 1.0;

        // 包含關係
        if (t1.contains(t2) || t2.contains(t1)) {
            return 0.85;
        }

        // Jaccard 相似度（基於字元 bigram）
        Set<String> bigrams1 = getBigrams(t1);
        Set<String> bigrams2 = getBigrams(t2);

        Set<String> intersection = new HashSet<>(bigrams1);
        intersection.retainAll(bigrams2);

        Set<String> union = new HashSet<>(bigrams1);
        union.addAll(bigrams2);

        if (union.isEmpty()) return 0.0;

        return (double) intersection.size() / union.size();
    }

    private static Set<String> getBigrams(String text) {
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            bigrams.add(text.substring(i, i + 2));
        }
        return bigrams;
    }

    // ================= 內容去重（SimHash） =================

    private static List<PageNode> deduplicateByContent(List<PageNode> pages) {
        // 計算每個頁面的 SimHash
        List<Long> simhashes = new ArrayList<>();
        for (PageNode page : pages) {
            String content = getContentForHash(page);
            simhashes.add(calculateSimHash(content));
        }

        List<PageNode> result = new ArrayList<>();
        Set<Integer> merged = new HashSet<>();

        for (int i = 0; i < pages.size(); i++) {
            if (merged.contains(i)) continue;

            List<PageNode> similar = new ArrayList<>();
            similar.add(pages.get(i));

            for (int j = i + 1; j < pages.size(); j++) {
                if (merged.contains(j)) continue;

                int hammingDist = hammingDistance(simhashes.get(i), simhashes.get(j));
                if (hammingDist <= SIMHASH_HAMMING_THRESHOLD) {
                    similar.add(pages.get(j));
                    merged.add(j);
                }
            }

            PageNode best = selectBest(similar);
            result.add(best);
            merged.add(i);
        }

        return result;
    }

    private static String getContentForHash(PageNode page) {
        StringBuilder sb = new StringBuilder();
        if (page.getTitle() != null) sb.append(page.getTitle()).append(" ");
        if (page.getTextContent() != null) {
            // 只取前 2000 字元
            String content = page.getTextContent();
            sb.append(content.substring(0, Math.min(2000, content.length())));
        }
        return sb.toString();
    }

    /**
     * 計算 SimHash（64-bit）
     */
    private static long calculateSimHash(String text) {
        if (text == null || text.isEmpty()) return 0L;

        int[] weights = new int[64];

        // 分詞（簡單版：用 bigram）
        List<String> tokens = new ArrayList<>();
        String lower = text.toLowerCase();

        // 中文 bigram
        for (int i = 0; i < lower.length() - 1; i++) {
            tokens.add(lower.substring(i, i + 2));
        }

        // 計算每個 token 的 hash 並加權
        for (String token : tokens) {
            long hash = hashToken(token);

            for (int i = 0; i < 64; i++) {
                if (((hash >> i) & 1) == 1) {
                    weights[i]++;
                } else {
                    weights[i]--;
                }
            }
        }

        // 生成 SimHash
        long simhash = 0L;
        for (int i = 0; i < 64; i++) {
            if (weights[i] > 0) {
                simhash |= (1L << i);
            }
        }

        return simhash;
    }

    /**
     * 計算 token 的 hash（使用 FNV-1a）
     */
    private static long hashToken(String token) {
        long hash = 0xcbf29ce484222325L; // FNV offset basis
        long prime = 0x100000001b3L;      // FNV prime

        for (char c : token.toCharArray()) {
            hash ^= c;
            hash *= prime;
        }

        return hash;
    }

    /**
     * 計算漢明距離
     */
    private static int hammingDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
    }

    // ================= 選擇最佳結果 =================

    /**
     * 從相似結果中選擇最佳的
     * 優先順序：分數 > 內容長度 > 有日期 > 有城市
     */
    private static PageNode selectBest(List<PageNode> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return candidates.stream()
            .max(Comparator
                .comparingDouble(PageNode::getScore)
                .thenComparingInt(p -> p.getTextContent() != null ? p.getTextContent().length() : 0)
                .thenComparing(p -> p.getEventDate() != null ? 1 : 0)
                .thenComparing(p -> p.getCity() != null && !"全台".equals(p.getCity()) ? 1 : 0)
            )
            .orElse(candidates.get(0));
    }

    // ================= 進階功能 =================

    /**
     * 聚類相似結果（不合併，只標記）
     * 回傳 Map<代表結果, 相似結果列表>
     */
    public static Map<PageNode, List<PageNode>> cluster(List<PageNode> pages) {
        Map<PageNode, List<PageNode>> clusters = new LinkedHashMap<>();
        Set<PageNode> assigned = new HashSet<>();

        for (PageNode page : pages) {
            if (assigned.contains(page)) continue;

            List<PageNode> cluster = new ArrayList<>();
            cluster.add(page);
            assigned.add(page);

            String title1 = normalizeTitle(page.getTitle());

            for (PageNode other : pages) {
                if (assigned.contains(other)) continue;

                String title2 = normalizeTitle(other.getTitle());
                double similarity = calculateTitleSimilarity(title1, title2);

                if (similarity >= TITLE_SIMILARITY_THRESHOLD) {
                    cluster.add(other);
                    assigned.add(other);
                }
            }

            // 選出代表
            PageNode representative = selectBest(cluster);
            cluster.remove(representative);
            clusters.put(representative, cluster);
        }

        return clusters;
    }

    /**
     * 計算兩個 PageNode 的整體相似度
     */
    public static double calculateSimilarity(PageNode p1, PageNode p2) {
        double titleSim = calculateTitleSimilarity(
            normalizeTitle(p1.getTitle()),
            normalizeTitle(p2.getTitle())
        );

        // 如果標題很相似，直接回傳
        if (titleSim >= 0.8) return titleSim;

        // 否則也考慮內容
        String c1 = getContentForHash(p1);
        String c2 = getContentForHash(p2);

        long hash1 = calculateSimHash(c1);
        long hash2 = calculateSimHash(c2);

        int hamming = hammingDistance(hash1, hash2);
        double contentSim = 1.0 - (hamming / 64.0);

        // 加權平均
        return titleSim * 0.6 + contentSim * 0.4;
    }
}