package app.bl;

import java.util.*;

/**
 * SemanticAnalyzer - 語意分析
 * 
 * Stage 4: 從搜尋結果中提取相關關鍵字，進行迭代搜尋
 */
public class SemanticAnalyzer {
    
    // 相關關鍵字對應表
    private static final Map<String, List<String>> RELATED_KEYWORDS = Map.ofEntries(
        Map.entry("音樂", List.of("演唱會", "live", "樂團", "音樂節", "concert")),
        Map.entry("展覽", List.of("美術", "藝術", "博物館", "gallery", "展出")),
        Map.entry("市集", List.of("手作", "文創", "創意", "攤位", "市場")),
        Map.entry("戶外", List.of("露營", "健行", "登山", "野餐", "運動")),
        Map.entry("親子", List.of("兒童", "家庭", "kids", "遊樂", "體驗")),
        Map.entry("節慶", List.of("慶典", "嘉年華", "festival", "祭典", "過年")),
        Map.entry("表演", List.of("舞台", "戲劇", "舞蹈", "劇場", "演出")),
        Map.entry("藝術", List.of("畫展", "創作", "設計", "藝文", "文化"))
    );
    
    /**
     * 從搜尋結果中提取相關關鍵字
     */
    public static List<String> extractRelatedKeywords(List<PageNode> pages) {
        // 統計所有出現的關鍵字
        Map<String, Integer> keywordCounts = new HashMap<>();
        
        for (PageNode page : pages) {
            // 從標題提取
            String title = page.getTitle().toLowerCase(Locale.ROOT);
            
            for (Map.Entry<String, List<String>> entry : RELATED_KEYWORDS.entrySet()) {
                String mainKeyword = entry.getKey();
                List<String> related = entry.getValue();
                
                // 檢查主關鍵字
                if (title.contains(mainKeyword)) {
                    keywordCounts.put(mainKeyword, keywordCounts.getOrDefault(mainKeyword, 0) + 1);
                }
                
                // 檢查相關關鍵字
                for (String kw : related) {
                    if (title.contains(kw.toLowerCase())) {
                        keywordCounts.put(kw, keywordCounts.getOrDefault(kw, 0) + 1);
                    }
                }
            }
            
            // 從 tokens 提取
            for (String token : page.getTokens()) {
                keywordCounts.put(token, keywordCounts.getOrDefault(token, 0) + 1);
            }
        }
        
        // 按出現次數排序，取前 5 個
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(keywordCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            result.add(sorted.get(i).getKey());
        }
        
        return result;
    }
    
    /**
     * 根據已有關鍵字，推薦相關的新關鍵字
     */
    public static List<String> suggestNewKeywords(List<String> currentKeywords) {
        Set<String> suggestions = new HashSet<>();
        
        for (String kw : currentKeywords) {
            String lower = kw.toLowerCase();
            
            // 找到相關詞
            for (Map.Entry<String, List<String>> entry : RELATED_KEYWORDS.entrySet()) {
                if (entry.getKey().equals(lower) || entry.getValue().contains(lower)) {
                    // 加入主關鍵字
                    suggestions.add(entry.getKey());
                    // 加入相關詞
                    suggestions.addAll(entry.getValue());
                }
            }
        }
        
        // 移除已有的關鍵字
        for (String kw : currentKeywords) {
            suggestions.remove(kw.toLowerCase());
        }
        
        return new ArrayList<>(suggestions);
    }
    
    /**
     * 產生擴展查詢（用於迭代搜尋）
     */
    public static String generateExpandedQuery(String originalQuery, List<PageNode> pages) {
        List<String> extracted = extractRelatedKeywords(pages);
        List<String> suggested = suggestNewKeywords(extracted);
        
        // 取前 3 個建議詞加入查詢
        StringBuilder expanded = new StringBuilder(originalQuery);
        int added = 0;
        for (String kw : suggested) {
            if (added >= 3) break;
            if (!originalQuery.toLowerCase().contains(kw.toLowerCase())) {
                expanded.append(" ").append(kw);
                added++;
            }
        }
        
        return expanded.toString();
    }
    
    /**
     * 取得語意分析報告
     */
    public static String getAnalysisReport(List<PageNode> pages, String originalQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 語意分析報告 (Stage 4) ===\n\n");
        
        sb.append("原始查詢: " + originalQuery + "\n\n");
        
        // 提取的關鍵字
        List<String> extracted = extractRelatedKeywords(pages);
        sb.append("📊 提取的關鍵字:\n");
        for (String kw : extracted) {
            sb.append("  • " + kw + "\n");
        }
        sb.append("\n");
        
        // 建議的新關鍵字
        List<String> suggested = suggestNewKeywords(extracted);
        sb.append("💡 建議的相關關鍵字:\n");
        for (String kw : suggested) {
            sb.append("  • " + kw + "\n");
        }
        sb.append("\n");
        
        // 擴展查詢
        String expanded = generateExpandedQuery(originalQuery, pages);
        sb.append("🔍 擴展查詢: " + expanded + "\n");
        
        return sb.toString();
    }
}