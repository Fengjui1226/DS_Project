package app.bl;
import java.time.LocalDate;
import java.util.*;

public class PageNode {
    private final String url;
    private final String title;
    // 關鍵字 -> 詞頻
    private final Map<Keyword,Integer> tf;
    private double score = 0.0;

    // ===== 新增的屬性 =====
    private LocalDate eventDate;     // 活動日期
    private String city;             // 活動城市
    private String domain;           // 網域（例如 gov.tw, org, com）
    private List<String> tokens = List.of(); // 標題或摘要斷詞結果

    // ===== 建構子 =====
    private PageNode(String url, String title, Map<Keyword,Integer> tf){
        this.url = url;
        this.title = title;
        this.tf = new HashMap<>(tf);
    }

    // 原本的工廠方法（保持不動）
    public static PageNode of(String url, String title, Map<Keyword,Integer> tf){
        return new PageNode(url, title, tf);
    }

    // ===== 新增的多參數工廠方法 =====
    public static PageNode of(String url, String title,
                              Map<Keyword,Integer> tf,
                              LocalDate eventDate,
                              String city,
                              String domain,
                              List<String> tokens){
        PageNode p = new PageNode(url, title, tf);
        p.eventDate = eventDate;
        p.city = city == null ? "" : city;
        p.domain = domain == null ? "" : domain.toLowerCase();
        p.tokens = tokens == null ? List.of() : List.copyOf(tokens);
        return p;
    }

    // ===== Getter 區 =====
    public Map<Keyword,Integer> tf(){ return tf; }
    public String getUrl(){ return url; }
    public String getTitle(){ return title; }
    public double getScore(){ return score; }
    public void setScore(double s){ this.score = s; }

    // ===== 新增的 Getter =====
    public LocalDate getEventDate(){ return eventDate; }
    public String getCity(){ return city; }
    public String getDomain(){ return domain; }
    public List<String> getTokens(){ return tokens; }
}