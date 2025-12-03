package app.bl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.da.GoogleConnector;
import app.da.LocationRecognizer;

/**
 * SearchEngine - 搜尋演算法（改進版）
 * 
 * 改進內容：
 * 1. 更好的活動過濾
 * 2. 排除申請/辦法/須知頁面
 * 3. 優先保留售票網站結果
 */
public class SearchEngine {

    // 活動相關關鍵字
    private static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "festival", "concert", "exhibition", "event",
        "表演", "藝術", "體驗", "親子", "戶外", "講座",
        "工作坊", "派對", "路跑", "馬拉松"
    );
    
    // 售票平台（優先保留）
    private static final Set<String> TICKET_DOMAINS = Set.of(
        "kktix", "accupass", "tixcraft", "opentix", 
        "ticket", "ibon", "udnfunlife", "ticketplus"
    );
    
    // 應該排除的頁面類型
    private static final Set<String> EXCLUDE_KEYWORDS = Set.of(
        "申請", "補助辦法", "徵選辦法", "作業要點", "實施計畫",
        "徵件須知", "注意事項", "相關規定", "法規", "條例"
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
        Map.entry("台東", "台東"),
        Map.entry("苗栗", "苗栗"),
        Map.entry("彰化", "彰化"),
        Map.entry("南投", "南投"),
        Map.entry("雲林", "雲林"),
        Map.entry("屏東", "屏東")
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

        // 搜尋 Google
        List<GoogleConnector.Result> raw = GoogleConnector.search(refinedQuery, 10);
        
        // 過濾結果
        List<GoogleConnector.Result> results = filterResults(raw);
        
        Set<String> seenLinks = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<PageNode> pages = new ArrayList<>();

        List<String> qTokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            if (!t.isBlank()) qTokens.add(t.trim());
        }

        // 偵測城市
        String city = LocationRecognizer.extractCity(query);
        if (city == null || city.isEmpty()) {
            city = detectCityFromQuery(query);
        }
        
        LocalDate today = fetchCurrentDate();

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

            // 過濾已過期活動
            if (eventDate != null && eventDate.isBefore(today)) {
                continue;
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

        // 建立樹結構和排名
        Tree tree = new Tree();
        tree.addPages(pages);
        
        RankCalculator.rank(pages, user);
        
        lastSearchTree = tree;
        
        return pages;
    }
    
    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }

    /**
     * 改進版查詢優化
     */
    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase(Locale.ROOT);
        boolean looksLikeCity = CITY_ALIASES.keySet().stream()
            .anyMatch(alias -> lower.contains(alias.toLowerCase(Locale.ROOT)));

        if (looksLikeCity) {
            // 如果只有城市名，加入活動相關詞
            q += " 活動 OR 展覽 OR 演唱會 OR 音樂會 OR 市集";
        }
        
        // 加入排除條件，過濾申請/辦法頁面
        q += " -申請辦法 -徵選 -補助要點";
        
        return q;
    }

    /**
     * 改進版結果過濾
     */
    private static List<GoogleConnector.Result> filterResults(List<GoogleConnector.Result> input) {
        List<GoogleConnector.Result> priorityResults = new ArrayList<>();  // 售票網站
        List<GoogleConnector.Result> normalResults = new ArrayList<>();    // 一般結果
        
        for (GoogleConnector.Result r : input) {
            if (r == null || r.title == null || r.link == null) continue;
            if (r.title.contains("Google Custom Search")) continue;
            
            String title = r.title.toLowerCase(Locale.ROOT);
            String link = r.link.toLowerCase(Locale.ROOT);
            
            // 排除申請/辦法頁面
            boolean shouldExclude = false;
            for (String exclude : EXCLUDE_KEYWORDS) {
                if (title.contains(exclude)) {
                    shouldExclude = true;
                    break;
                }
            }
            if (shouldExclude) continue;
            
            // 排除純下載/表格頁面
            if (title.contains("下載專區") || title.contains("表格下載") || 
                title.contains("申請表") || title.contains("書表")) {
                continue;
            }
            
            // 檢查是否為售票網站
            boolean isTicketSite = false;
            for (String ticket : TICKET_DOMAINS) {
                if (link.contains(ticket)) {
                    isTicketSite = true;
                    break;
                }
            }
            
            // 檢查是否含活動關鍵字
            boolean hasEventTerm = false;
            for (String term : EVENT_TERMS) {
                if (title.contains(term.toLowerCase()) || link.contains(term.toLowerCase())) {
                    hasEventTerm = true;
                    break;
                }
            }
            
            if (isTicketSite) {
                priorityResults.add(r);  // 售票網站優先
            } else if (hasEventTerm) {
                normalResults.add(r);
            }
        }
        
        // 合併結果：售票網站在前
        List<GoogleConnector.Result> combined = new ArrayList<>();
        combined.addAll(priorityResults);
        combined.addAll(normalResults);
        
        // 如果過濾後沒有結果，回傳原始結果（但排除明顯非活動頁面）
        if (combined.isEmpty()) {
            for (GoogleConnector.Result r : input) {
                if (r == null || r.title == null || r.link == null) continue;
                String title = r.title.toLowerCase();
                
                // 即使沒有活動關鍵字，也排除申請/辦法頁面
                boolean exclude = false;
                for (String kw : EXCLUDE_KEYWORDS) {
                    if (title.contains(kw)) {
                        exclude = true;
                        break;
                    }
                }
                if (!exclude) {
                    combined.add(r);
                }
            }
        }
        
        return combined;
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
        
        // Pattern 2: 10/26 或 10.26
        Pattern p2 = Pattern.compile("(?<!\\d)(\\d{1,2})[./\\-](\\d{1,2})(?!\\d)");
        Matcher m2 = p2.matcher(title);
        while (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    LocalDate date = LocalDate.of(today.getYear(), month, day);
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
        return foundDates.stream().max(LocalDate::compareTo).orElse(null);
    }
    
    private static LocalDate fetchCurrentDate() {
        String api = "http://worldtimeapi.org/api/ip";
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                int idx = body.indexOf("\"datetime\"");
                if (idx >= 0) {
                    int colon = body.indexOf(':', idx);
                    int q1 = body.indexOf('"', colon);
                    int q2 = body.indexOf('"', q1 + 1);
                    if (q1 >= 0 && q2 > q1) {
                        String dt = body.substring(q1 + 1, q2);
                        try {
                            OffsetDateTime odt = OffsetDateTime.parse(dt);
                            return odt.toLocalDate();
                        } catch (Exception ex) {}
                    }
                }
            }
        } catch (Exception e) {}
        return LocalDate.now();
    }
}