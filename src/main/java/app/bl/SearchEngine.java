package app.bl;

import app.da.GoogleConnector;
import app.da.LocationRecognizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/**
 * SearchEngine - 搜尋演算法並計算結果
 */
public class SearchEngine {

    private static final List<String> EVENT_TERMS = List.of(
            "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
            "festival", "concert", "exhibition", "event",
            "表演", "藝術", "體驗", "親子", "戶外"
    );

    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
            Map.entry("台北", "台北"),
            Map.entry("臺北", "台北"),
            Map.entry("taipei", "台北"),
            Map.entry("新北", "新北"),
            Map.entry("台中", "台中"),
            Map.entry("臺中", "台中"),
            Map.entry("taichung", "台中"),
            Map.entry("台南", "台南"),
            Map.entry("臺南", "台南"),
            Map.entry("高雄", "高雄"),
            Map.entry("kaohsiung", "高雄"),
            Map.entry("桃園", "桃園"),
            Map.entry("基隆", "基隆"),
            Map.entry("新竹", "新竹"),
            Map.entry("嘉義", "嘉義"),
            Map.entry("宜蘭", "宜蘭"),
            Map.entry("花蓮", "花蓮"),
            Map.entry("台東", "台東")
    );

    private static Tree lastSearchTree;

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        // 確保城市被加入查詢
        String userCity = user.getUserCity();
        if (userCity != null && !userCity.isEmpty()) {
            String queryLower = query.toLowerCase();
            boolean hasCity = false;
            for (String cityAlias : CITY_ALIASES.keySet()) {
                if (queryLower.contains(cityAlias.toLowerCase())) {
                    hasCity = true;
                    break;
                }
            }
            if (!hasCity) {
                query = userCity + " " + query;
            }
        }
        
        String refinedQuery = refineQuery(query);

        List<GoogleConnector.Result> raw = GoogleConnector.search(refinedQuery, 10);
        List<GoogleConnector.Result> results = filterEventLike(raw);

        Set<String> seenLinks = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<PageNode> pages = new ArrayList<>();

        List<String> qTokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            if (!t.isBlank()) qTokens.add(t.trim());
        }

        String city = LocationRecognizer.extractCity(query);
        if (city == null) {
            city = detectCityFromQuery(query);
        }

        LocalDate today = LocalDate.now();

        for (GoogleConnector.Result r : results) {
            if (r == null || r.title == null || r.title.isBlank() || r.link == null || r.link.isBlank()) continue;
            
            String linkKey = r.link.trim().toLowerCase();
            String titleKey = r.title.trim().toLowerCase();
            
            // 去重
            if (!seenLinks.add(linkKey)) continue;
            if (!seenTitles.add(titleKey)) continue;
            
            // 標題相似度檢查
            String titlePrefix = titleKey.length() > 20 ? titleKey.substring(0, 20) : titleKey;
            boolean isDuplicate = false;
            for (String seen : seenTitles) {
                if (seen.startsWith(titlePrefix) && !seen.equals(titleKey)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (isDuplicate) continue;

            // 解析日期
            LocalDate eventDate = extractDateFromTitle(r.title);

            // 過濾過期活動 - 簡單直接的判斷
            if (eventDate != null && eventDate.isBefore(today)) {
                continue; // 日期在今天之前，跳過
            }

            Map<Keyword, Integer> tf = new HashMap<>();
            
            for (String t : qTokens) {
                Keyword k = Keyword.of(t);
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
            
            String titleLower = r.title.toLowerCase(Locale.ROOT);
            for (String term : EVENT_TERMS) {
                if (titleLower.contains(term.toLowerCase())) {
                    Keyword k = Keyword.of(term);
                    tf.put(k, tf.getOrDefault(k, 0) + 1);
                }
            }

            List<String> tokens = new ArrayList<>(qTokens);
            for (String term : EVENT_TERMS) {
                if (titleLower.contains(term.toLowerCase())) {
                    tokens.add(term);
                }
            }
            
            String eventCity = city;
            if (eventCity == null || eventCity.isEmpty()) {
                eventCity = LocationRecognizer.extractCity(r.title);
            }
            if (eventCity == null) eventCity = "";

            PageNode p = PageNode.of(
                    r.link,
                    r.title,
                    tf,
                    eventDate,
                    eventCity,
                    extractDomain(r.link),
                    tokens
            );
            
            for (String token : tokens) {
                user.bumpHabit(token);
            }
            
            pages.add(p);
        }

        Tree tree = new Tree();
        tree.addPages(pages);
        
        RankCalculator.rank(pages, user);
        
        lastSearchTree = tree;
        
        return pages;
    }
    
    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }

    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase(Locale.ROOT);
        boolean looksLikeCity = CITY_ALIASES.keySet().stream()
                .anyMatch(alias -> lower.contains(alias.toLowerCase(Locale.ROOT)));

        if (looksLikeCity) {
            q += " 活動 OR 展覽 OR 音樂 OR 節慶 OR 市集";
        }
        return q;
    }

    private static List<GoogleConnector.Result> filterEventLike(List<GoogleConnector.Result> input) {
        List<GoogleConnector.Result> out = new ArrayList<>();
        for (GoogleConnector.Result r : input) {
            if (r == null || r.title == null || r.link == null) continue;
            
            if (r.title.contains("Google Custom Search")) continue;
            
            String t = r.title.toLowerCase(Locale.ROOT);
            String link = r.link.toLowerCase(Locale.ROOT);

            boolean hit = false;
            for (String term : EVENT_TERMS) {
                String tt = term.toLowerCase(Locale.ROOT);
                if (t.contains(tt) || link.contains(tt)) {
                    hit = true;
                    break;
                }
            }
            if (hit) out.add(r);
        }
        
        if (out.isEmpty()) return input;
        return out;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return "";
        String lower = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return "";
    }

    private static String extractDomain(String url) {
        try {
            String u = url.toLowerCase(Locale.ROOT);
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "example.com";
        }
    }
    
    private static LocalDate extractDateFromTitle(String title) {
        if (title == null) return null;
        
        LocalDate today = LocalDate.now();
        List<LocalDate> foundDates = new ArrayList<>();
        
        // Pattern 1: 2024/10/26 或 2024.10.26 或 2024-10-26
        Pattern p1 = Pattern.compile("(202\\d)[./\\-](\\d{1,2})[./\\-](\\d{1,2})");
        Matcher m1 = p1.matcher(title);
        while (m1.find()) {
            try {
                int year = Integer.parseInt(m1.group(1));
                int month = Integer.parseInt(m1.group(2));
                int day = Integer.parseInt(m1.group(3));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    foundDates.add(LocalDate.of(year, month, day));
                }
            } catch (Exception e) {}
        }
        
        // Pattern 2: 10/26 或 10.26 或 10-26 (沒有年份，假設今年或明年)
        Pattern p2 = Pattern.compile("(?<!\\d)(\\d{1,2})[./\\-](\\d{1,2})(?!\\d)");
        Matcher m2 = p2.matcher(title);
        while (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    LocalDate date = LocalDate.of(today.getYear(), month, day);
                    // 如果日期已過，假設是明年
                    if (date.isBefore(today)) {
                        date = date.plusYears(1);
                    }
                    foundDates.add(date);
                }
            } catch (Exception e) {}
        }
        
        // Pattern 3: 10月26日
        Pattern p3 = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
        Matcher m3 = p3.matcher(title);
        while (m3.find()) {
            try {
                int month = Integer.parseInt(m3.group(1));
                int day = Integer.parseInt(m3.group(2));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    LocalDate date = LocalDate.of(today.getYear(), month, day);
                    if (date.isBefore(today)) {
                        date = date.plusYears(1);
                    }
                    foundDates.add(date);
                }
            } catch (Exception e) {}
        }
        
        if (foundDates.isEmpty()) return null;
        
        // 回傳最晚的日期（結束日期）
        return foundDates.stream().max(LocalDate::compareTo).orElse(null);
    }
}