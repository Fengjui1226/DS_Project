package app.bl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 代表一個活動頁面（文件）。
 */
public class PageNode {

    private final String url;
    private final String title;
    private final Map<Keyword, Integer> tf;
    private double score = 0.0;

    private LocalDate eventDate;
    private String city;
    private String domain;
    private List<String> tokens = List.of();

    private PageNode(String url, String title, Map<Keyword, Integer> tf) {
        this.url = Objects.requireNonNull(url, "url");
        this.title = Objects.requireNonNull(title, "title");
        this.tf = new HashMap<>(Objects.requireNonNull(tf, "tf"));
    }

    public static PageNode of(String url, String title, Map<Keyword, Integer> tf) {
        return new PageNode(url, title, tf);
    }

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

    // Getters / Setters
    public Map<Keyword, Integer> tf() { return Collections.unmodifiableMap(tf); }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public double getScore() { return score; }
    public void setScore(double s) { this.score = s; }
    public LocalDate getEventDate() { return eventDate; }
    public String getCity() { return city; }
    public String getDomain() { return domain; }
    public List<String> getTokens() { return tokens; }

    public void setEventDate(LocalDate d) { this.eventDate = d; }
    public void setCity(String c) { this.city = (c == null) ? "" : c; }
    public void setDomain(String d) { this.domain = (d == null) ? "" : d.toLowerCase(Locale.ROOT); }
    public void setTokens(List<String> tok) { this.tokens = (tok == null) ? List.of() : List.copyOf(tok); }

    public void bumpTf(Keyword k, int delta) {
        if (k == null || delta == 0) return;
        tf.put(k, tf.getOrDefault(k, 0) + delta);
    }

    /**
     * Freshness score: e^(-days/7)
     */
    public double calculateFreshness() {
        if (eventDate == null) return 0.5;
        
        LocalDate today = LocalDate.now();
        long daysUntilEvent = ChronoUnit.DAYS.between(today, eventDate);
        
        if (daysUntilEvent < 0) {
            return Math.exp(daysUntilEvent / 7.0) * 0.3;
        }
        return Math.exp(-daysUntilEvent / 7.0);
    }
    
    /**
     * Proximity bonus for keywords appearing close together
     */
    public double calculateProximityBonus(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.size() < 2) return 1.0;
        
        String titleLower = title.toLowerCase(Locale.ROOT);
        double bonus = 1.0;
        
        for (int i = 0; i < queryTokens.size() - 1; i++) {
            String word1 = queryTokens.get(i).toLowerCase();
            String word2 = queryTokens.get(i + 1).toLowerCase();
            
            int pos1 = titleLower.indexOf(word1);
            int pos2 = titleLower.indexOf(word2);
            
            if (pos1 >= 0 && pos2 >= 0) {
                int distance = Math.abs(pos2 - pos1 - word1.length());
                
                if (distance <= 0) bonus += 3.0;
                else if (distance <= 3) bonus += 2.5;
                else if (distance <= 5) bonus += 2.0;
                else if (distance <= 10) bonus += 1.5;
                else bonus += 1.0;
            }
        }
        return Math.min(bonus, 5.0);
    }
    
   /**
     * Region boost based on user location
     */
    public double calculateRegionBoost(String userCity) {
        if (userCity == null || userCity.isEmpty()) return 1.0;
        
        // 沒有城市資訊的活動降低分數
        if (city == null || city.isEmpty()) return 0.5;
        
        // 同城市 2 倍
        if (city.equals(userCity)) return 2.0;
        
        // 擴展鄰近城市關係
        Map<String, List<String>> nearby = Map.ofEntries(
            Map.entry("台北", List.of("新北", "基隆", "桃園", "宜蘭")),
            Map.entry("新北", List.of("台北", "基隆", "桃園", "宜蘭")),
            Map.entry("桃園", List.of("台北", "新北", "新竹", "苗栗")),
            Map.entry("新竹", List.of("桃園", "苗栗", "台北")),
            Map.entry("苗栗", List.of("新竹", "台中", "桃園")),
            Map.entry("台中", List.of("彰化", "南投", "苗栗", "雲林")),
            Map.entry("彰化", List.of("台中", "南投", "雲林")),
            Map.entry("南投", List.of("台中", "彰化", "雲林", "嘉義")),
            Map.entry("雲林", List.of("彰化", "嘉義", "台中")),
            Map.entry("嘉義", List.of("雲林", "台南", "南投")),
            Map.entry("台南", List.of("嘉義", "高雄")),
            Map.entry("高雄", List.of("台南", "屏東")),
            Map.entry("屏東", List.of("高雄", "台東")),
            Map.entry("宜蘭", List.of("台北", "新北", "花蓮")),
            Map.entry("花蓮", List.of("宜蘭", "台東")),
            Map.entry("台東", List.of("花蓮", "屏東")),
            Map.entry("基隆", List.of("台北", "新北"))
        );
        
        // 鄰近城市 1.5 倍
        if (nearby.getOrDefault(userCity, List.of()).contains(city)) return 1.5;
        
        // 非本地活動 0.8 倍
        return 0.8;
    }

    @Override 
    public String toString() {
        String displayUrl = "🌐 官方網站（點我）";
        return title + " | " + displayUrl + " | " + (city == null ? "" : city);
    }
}