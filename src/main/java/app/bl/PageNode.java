package app.bl;

import java.time.LocalDate;
import java.util.*;

/**
 * 代表一個活動頁面（文件）。
 * 內含：URL、標題、關鍵字詞頻(tf)、活動日期、城市、網域、斷詞、以及排名分數。
 */
public class PageNode {

    // 基本欄位
    private final String url;
    private final String title;
    /** 關鍵字 -> 詞頻 */
    private final Map<Keyword, Integer> tf;

    // 排名分數（由 RankCalculator 設定）
    private double score = 0.0;

    // 擴充屬性
    private LocalDate eventDate;          // 活動日期
    private String city;                  // 城市
    private String domain;                // 網域（ex: gov.tw, example.com）
    private List<String> tokens = List.of(); // 斷詞結果（標題/摘要/內容）

    // 建構子只讓 factory 使用，避免外部少填欄位
    private PageNode(String url, String title, Map<Keyword, Integer> tf) {
        this.url = Objects.requireNonNull(url, "url");
        this.title = Objects.requireNonNull(title, "title");
        this.tf = new HashMap<>(Objects.requireNonNull(tf, "tf"));
    }

    /** 原本的工廠：僅基本欄位 */
    public static PageNode of(String url, String title, Map<Keyword, Integer> tf) {
        return new PageNode(url, title, tf);
    }

    /** 擴充工廠：一次給齊所有欄位（可傳 null） */
    public static PageNode of(String url, String title,
                              Map<Keyword, Integer> tf,
                              LocalDate eventDate,
                              String city,
                              String domain,
                              List<String> tokens) {
        PageNode p = new PageNode(url, title, tf);
        p.eventDate = eventDate;
        p.city = (city == null) ? "" : city;
        p.domain = (domain == null) ? "" : domain.toLowerCase(Locale.ROOT);
        p.tokens = (tokens == null) ? List.of() : List.copyOf(tokens);
        return p;
    }

    // ========= Getters / Setters =========
    public Map<Keyword, Integer> tf() { return Collections.unmodifiableMap(tf); }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public double getScore() { return score; }
    public void setScore(double s) { this.score = s; }

    public LocalDate getEventDate() { return eventDate; }
    public String getCity() { return city; }
    public String getDomain() { return domain; }
    public List<String> getTokens() { return tokens; }

    // （可選）輔助：在建立後補資料用 — 若你不需要可移除
    public void setEventDate(LocalDate d) { this.eventDate = d; }
    public void setCity(String c) { this.city = (c == null) ? "" : c; }
    public void setDomain(String d) { this.domain = (d == null) ? "" : d.toLowerCase(Locale.ROOT); }
    public void setTokens(List<String> tok) { this.tokens = (tok == null) ? List.of() : List.copyOf(tok); }

    // ========= 小工具：方便 Rank/解析階段使用 =========
    /** 將某關鍵字詞頻累加（用於解析時計數） */
    public void bumpTf(Keyword k, int delta) {
        if (k == null || delta == 0) return;
        tf.put(k, tf.getOrDefault(k, 0) + delta);
    }

    /** 除錯顯示 */
    @Override public String toString() {
        return "PageNode{" + title + " | " + url + " | " + city + " | " + eventDate + "}";
    }
}