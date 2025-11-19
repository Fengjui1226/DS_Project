package app.bl;

import java.util.*;

/**
 * Tree - 網站樹狀結構，計算網站總分
 * 
 * Stage 2: Site Ranking - 包含子URL的樹狀結構
 */
public class Tree {
    
    private final SiteNode root;
    
    public Tree() {
        this.root = new SiteNode("root");
    }
    
    /**
     * 加入一個頁面到樹狀結構
     */
    public void addPage(PageNode page) {
        if (page == null || page.getDomain() == null) return;
        
        String domain = page.getDomain();
        SiteNode domainNode = root.getOrCreateChild(domain);
        domainNode.addPage(page);
    }
    
    /**
     * 加入多個頁面
     */
    public void addPages(List<PageNode> pages) {
        for (PageNode p : pages) {
            addPage(p);
        }
    }
    
    /**
     * 取得所有頁面（依分數排序）
     */
    public List<PageNode> getAllPagesSorted() {
        List<PageNode> all = new ArrayList<>();
        for (SiteNode child : root.getChildren()) {
            all.addAll(child.getPages());
        }
        all.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return all;
    }
    
    /**
     * 取得網站總分
     */
    public double getDomainScore(String domain) {
        SiteNode node = root.getChild(domain);
        return node != null ? node.getTotalScore() : 0.0;
    }
    
    /**
     * 取得樹狀結構的文字表示（用於顯示）
     */
    public String getTreeDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 網站樹狀結構 ===\n\n");
        
        List<SiteNode> sites = new ArrayList<>(root.getChildren());
        sites.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        int rank = 1;
        for (SiteNode site : sites) {
            sb.append(String.format("#%d %s (總分: %.2f)\n", 
                rank++, site.getName(), site.getTotalScore()));
            
            // 顯示關鍵字統計
            Map<String, Integer> keywordCounts = site.getKeywordCounts();
            if (!keywordCounts.isEmpty()) {
                sb.append("    關鍵字: ");
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(keywordCounts.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                
                int count = 0;
                for (Map.Entry<String, Integer> e : sorted) {
                    if (count++ > 0) sb.append(", ");
                    sb.append(e.getKey() + "(" + e.getValue() + ")");
                    if (count >= 5) break; // 只顯示前5個
                }
                sb.append("\n");
            }
            
            // 顯示子頁面
            for (PageNode page : site.getPages()) {
                sb.append(String.format("    └─ %s (%.2f)\n", 
                    truncate(page.getTitle(), 40), page.getScore()));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
    
    /**
     * 內部類別：網站節點
     */
    public static class SiteNode {
        private final String name;
        private final Map<String, SiteNode> children = new HashMap<>();
        private final List<PageNode> pages = new ArrayList<>();
        
        public SiteNode(String name) {
            this.name = name;
        }
        
        public String getName() { return name; }
        
        public SiteNode getChild(String childName) {
            return children.get(childName.toLowerCase(Locale.ROOT));
        }
        
        public SiteNode getOrCreateChild(String childName) {
            String key = childName.toLowerCase(Locale.ROOT);
            return children.computeIfAbsent(key, k -> new SiteNode(childName));
        }
        
        public Collection<SiteNode> getChildren() {
            return children.values();
        }
        
        public void addPage(PageNode page) {
            pages.add(page);
        }
        
        public List<PageNode> getPages() {
            return Collections.unmodifiableList(pages);
        }
        
        public double getTotalScore() {
            return pages.stream().mapToDouble(PageNode::getScore).sum();
        }
        
        /**
         * 統計此網站所有頁面的關鍵字出現次數
         */
        public Map<String, Integer> getKeywordCounts() {
            Map<String, Integer> counts = new HashMap<>();
            for (PageNode page : pages) {
                for (Map.Entry<Keyword, Integer> e : page.tf().entrySet()) {
                    String kw = e.getKey().name();
                    counts.put(kw, counts.getOrDefault(kw, 0) + e.getValue());
                }
            }
            return counts;
        }
    }
}