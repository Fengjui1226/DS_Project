package app.bl;

import java.time.LocalDate;
import java.util.*;

/**
 * PageNode - 網頁節點（支援子網頁）
 * 
 * 改進：
 * - 儲存子網頁列表
 * - 計算總分 = 自身分數 + Σ子網頁分數
 */
public class PageNode {
    
    private String url;
    private String title;
    private String domain;
    private String city;
    private LocalDate eventDate;
    private Map<Keyword, Integer> tf;
    private List<String> tokens;
    
    // 分數相關
    private double score = 0.0;           // 自身分數
    private double subPagesScore = 0.0;   // 子網頁分數總和
    private double totalScore = 0.0;      // 總分 = 自身 + 子網頁
    
    // 子網頁
    private List<SubPageNode> subPages = new ArrayList<>();
    private boolean crawled = false;      // 是否已爬取子網頁
    
    // 內容（從爬蟲取得）
    private String textContent = "";

    // ============ 建構子 ============
    
    private PageNode() {}
    
    public static PageNode of(String url, String title, Map<Keyword, Integer> tf,
                              LocalDate date, String city, String domain, List<String> tokens) {
        PageNode p = new PageNode();
        p.url = url;
        p.title = title;
        p.tf = tf != null ? tf : new HashMap<>();
        p.eventDate = date;
        p.city = city;
        p.domain = domain;
        p.tokens = tokens != null ? tokens : new ArrayList<>();
        return p;
    }
    
    // ============ 子網頁相關 ============
    
    /**
     * 新增子網頁
     */
    public void addSubPage(SubPageNode subPage) {
        this.subPages.add(subPage);
    }
    
    /**
     * 取得所有子網頁
     */
    public List<SubPageNode> getSubPages() {
        return subPages;
    }
    
    /**
     * 計算子網頁分數總和
     */
    public void calculateSubPagesScore() {
        this.subPagesScore = 0.0;
        for (SubPageNode sub : subPages) {
            this.subPagesScore += sub.getScore();
        }
    }
    
    /**
     * 計算總分 = 自身分數 + 子網頁分數（加權）
     * 
     * 子網頁分數權重較低（0.3），因為主要還是看大網頁
     */
    public void calculateTotalScore() {
        this.totalScore = this.score + (this.subPagesScore * 0.3);
    }
    
    /**
     * 取得總分
     */
    public double getTotalScore() {
        return totalScore;
    }
    
    // ============ 分數計算輔助 ============
    
    /**
     * 計算關鍵字接近度獎勵
     */
    public double calculateProximityBonus(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.size() < 2) return 1.0;
        
        String content = (title + " " + textContent).toLowerCase();
        double bonus = 1.0;
        
        // 檢查關鍵字是否相鄰出現
        for (int i = 0; i < queryTokens.size() - 1; i++) {
            String t1 = queryTokens.get(i).toLowerCase();
            String t2 = queryTokens.get(i + 1).toLowerCase();
            
            // 相鄰出現
            if (content.contains(t1 + t2) || content.contains(t1 + " " + t2)) {
                bonus += 0.2;
            }
        }
        
        return Math.min(bonus, 2.0);  // 最多 2 倍
    }
    
    // ============ Getters & Setters ============
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    
    public Map<Keyword, Integer> tf() { return tf; }
    public Map<Keyword, Integer> getTf() { return tf; }
    public void setTf(Map<Keyword, Integer> tf) { this.tf = tf; }
    
    public List<String> getTokens() { return tokens; }
    public void setTokens(List<String> tokens) { this.tokens = tokens; }
    
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    
    public double getSubPagesScore() { return subPagesScore; }
    public void setSubPagesScore(double score) { this.subPagesScore = score; }
    
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    
    public boolean isCrawled() { return crawled; }
    public void setCrawled(boolean crawled) { this.crawled = crawled; }
    
    /**
     * 取得子網頁數量
     */
    public int getSubPageCount() {
        return subPages.size();
    }
    
    @Override
    public String toString() {
        return String.format("PageNode[%.1f (self) + %.1f (sub) = %.1f]: %s (%d subpages)", 
            score, subPagesScore, totalScore, title, subPages.size());
    }
}