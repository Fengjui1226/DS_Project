package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PageNode v2.1 - 網頁節點（支援子網頁 + 城市正規化）
 * 
 * 更新：
 * - 新增 setTotalScore() setter
 * - 新增 setEventDate() setter
 * - 城市名稱將自動正規化為標準格式（例如「臺北」→「台北」）
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
    private double totalScore = 0.0;      // 總分
    private int googleRank = 0;           // Google 原始排名
    
    // 子網頁
    private List<SubPageNode> subPages = new ArrayList<>();
    private boolean crawled = false;
    
    // 內容
    private String textContent = "";

    // ============ 建構子 ============
    
    private PageNode() {}
    
    public static PageNode of(String url, String title, Map<Keyword, Integer> tf,
                              LocalDate date, String city, String domain, List<String> tokens) {
        return of(url, title, tf, date, city, domain, tokens, null);
    }
    
    /**
     * 新增：支援傳入 Google snippet 作為初始內容
     */
    public static PageNode of(String url, String title, Map<Keyword, Integer> tf,
                              LocalDate date, String city, String domain, List<String> tokens,
                              String snippet) {
        PageNode p = new PageNode();
        p.url = url;
        p.title = title;
        p.tf = tf != null ? tf : new HashMap<>();
        p.eventDate = date;
        p.city = normalizeCity(city);
        p.domain = domain;
        p.tokens = tokens != null ? tokens : new ArrayList<>();
        // ★ 關鍵：將 snippet 作為初始內容，即使爬蟲失敗也有資料
        p.textContent = snippet != null ? snippet : "";
        return p;
    }

    // ============ 城市正規化 ============
    private static String normalizeCity(String city) {
        if (city == null) return null;
        String c = city.trim().toLowerCase();
        if (c.matches("(台北|臺北|taipei)")) return "台北";
        if (c.matches("(新北|new taipei)")) return "新北";
        if (c.matches("(台中|臺中|taichung)")) return "台中";
        if (c.matches("(台南|臺南|tainan)")) return "台南";
        if (c.matches("(高雄|kaohsiung)")) return "高雄";
        if (c.matches("(桃園|taoyuan)")) return "桃園";
        if (c.matches("(基隆|keelung)")) return "基隆";
        if (c.matches("(新竹|hsinchu)")) return "新竹";
        if (c.matches("(嘉義|chiayi)")) return "嘉義";
        if (c.matches("(花蓮|hualien)")) return "花蓮";
        if (c.matches("(台東|臺東|taitung)")) return "台東";
        return city;
    }
    
    // ============ 子網頁相關 ============
    
    public void addSubPage(SubPageNode subPage) {
        this.subPages.add(subPage);
    }
    
    public List<SubPageNode> getSubPages() {
        return subPages;
    }
    
    public void calculateSubPagesScore() {
        this.subPagesScore = 0.0;
        for (SubPageNode sub : subPages) {
            this.subPagesScore += sub.getScore();
        }
    }
    
    public void calculateTotalScore() {
        this.totalScore = this.score + (this.subPagesScore * 0.3);
    }
    
    public double getTotalScore() {
        return totalScore;
    }
    
    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }
    
    public int getGoogleRank() {
        return googleRank;
    }
    
    public void setGoogleRank(int rank) {
        this.googleRank = rank;
    }
    
    public double calculateProximityBonus(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.size() < 2) return 1.0;
        
        String content = (title + " " + textContent).toLowerCase();
        double bonus = 1.0;
        
        for (int i = 0; i < queryTokens.size() - 1; i++) {
            String t1 = queryTokens.get(i).toLowerCase();
            String t2 = queryTokens.get(i + 1).toLowerCase();
            
            if (content.contains(t1 + t2) || content.contains(t1 + " " + t2)) {
                bonus += 0.2;
            }
        }
        
        return Math.min(bonus, 2.0);
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = normalizeCity(city); }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public Map<Keyword, Integer> tf() { return tf; }
    public Map<Keyword, Integer> getTf() { return tf; }
    public void setTf(Map<Keyword, Integer> tf) { this.tf = tf; }

    public List<String> getTokens() { return tokens; }
    public void setTokens(List<String> tokens) { this.tokens = tokens; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public void addScore(double bonus) { this.score += bonus; }

    public double getSubPagesScore() { return subPagesScore; }
    public void setSubPagesScore(double score) { this.subPagesScore = score; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public boolean isCrawled() { return crawled; }
    public void setCrawled(boolean crawled) { this.crawled = crawled; }

    public int getSubPageCount() {
        return subPages.size();
    }

    @Override
    public String toString() {
        return String.format("PageNode[%.1f]: %s (%d subpages)", 
            totalScore, title, subPages.size());
    }
}