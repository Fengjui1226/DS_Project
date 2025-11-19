package app.bl;

import app.da.GoogleConnector;
import app.da.LocationRecognizer;
import java.time.LocalDate;
import java.util.*;

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

    // 儲存最後一次搜尋的 Tree 結構
    private static Tree lastSearchTree;

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        String refinedQuery = refineQuery(query);

        List<GoogleConnector.Result> raw = GoogleConnector.search(refinedQuery, 10);
        List<GoogleConnector.Result> results = filterEventLike(raw);

        Set<String> seenLinks = new HashSet<>();
        List<PageNode> pages = new ArrayList<>();

        List<String> qTokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            if (!t.isBlank()) qTokens.add(t.trim());
        }

        String city = LocationRecognizer.extractCity(query);
        if (city == null) {
            city = detectCityFromQuery(query);
        }

        for (GoogleConnector.Result r : results) {
            if (r == null || r.title == null || r.title.isBlank() || r.link == null || r.link.isBlank()) continue;
            String linkKey = r.link.trim();
            if (!seenLinks.add(linkKey)) continue;

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
            
            LocalDate eventDate = extractDateFromTitle(r.title);

            // 過濾過期活動 - 只保留今天及未來的活動
            if (eventDate != null && eventDate.isBefore(LocalDate.now())) {
                continue; // 跳過過期活動
            }

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

        // 使用 Tree 組織結果
        Tree tree = new Tree();
        tree.addPages(pages);
        
        RankCalculator.rank(pages, user);
        
        // 儲存 Tree 供後續顯示
        lastSearchTree = tree;
        
        return pages;
    }
    
    /**
     * 取得最後一次搜尋的 Tree 結構
     */
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
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})");
        java.util.regex.Matcher matcher = pattern.matcher(title);
        
        if (matcher.find()) {
            try {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                int year = LocalDate.now().getYear();
                
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    LocalDate date = LocalDate.of(year, month, day);
                    if (date.isBefore(LocalDate.now())) {
                        date = date.plusYears(1);
                    }
                    return date;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }
}