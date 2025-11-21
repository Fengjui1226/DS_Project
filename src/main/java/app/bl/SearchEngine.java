package app.bl;

import app.da.GoogleConnector;
import app.da.LocationRecognizer;
import java.time.LocalDate;
import java.util.*;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
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
                query = userCity + " " + query;  // 如果查詢沒有包含城市，預設加入城市名稱
            }
        }
        
        String refinedQuery = refineQuery(query);

        // 搜尋 Google 並過濾非活動相關的結果
        List<GoogleConnector.Result> raw = GoogleConnector.search(refinedQuery, 10);
        List<GoogleConnector.Result> results = filterEventLike(raw);
        
        Set<String> seenLinks = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<PageNode> pages = new ArrayList<>();

        List<String> qTokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            if (!t.isBlank()) qTokens.add(t.trim());
        }

        // 偵測並處理城市
        String city = LocationRecognizer.extractCity(query);
        if (city == null || city.isEmpty()) {
            city = detectCityFromQuery(query);  // 若查詢中未提及城市，嘗試提取城市
        }
        // 使用時間 API 取得當前日期以避免本機時鐘誤差，失敗時 fallback 到本機時間
        LocalDate today = fetchCurrentDate();

        for (GoogleConnector.Result r : results) {
            if (r == null || r.title == null || r.title.isBlank() || r.link == null || r.link.isBlank()) continue;
            
            String linkKey = r.link.trim().toLowerCase();
            String titleKey = r.title.trim().toLowerCase();
            
            // 去重
            if (!seenLinks.add(linkKey)) continue;
            if (!seenTitles.add(titleKey)) continue;
            
            // 標題相似度檢查，避免重複的標題
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

            // 只過濾掉「有日期且已過期」的活動
            // 沒有日期的保留（可能是活動列表頁面或即將公告的活動）
            if (eventDate != null && eventDate.isBefore(today)) {
                continue;  // 已過期的活動過濾掉
            }

            Map<Keyword, Integer> tf = new HashMap<>();
            
            // 解析查詢中的關鍵字頻率
            for (String t : qTokens) {
                Keyword k = Keyword.of(t);
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
            
            // 處理活動關鍵字
            String titleLower = r.title.toLowerCase(Locale.ROOT);
            for (String term : EVENT_TERMS) {
                if (titleLower.contains(term.toLowerCase())) {
                    Keyword k = Keyword.of(term);
                    tf.put(k, tf.getOrDefault(k, 0) + 1);
                }
            }

            // 添加查詢關鍵字到 tokens 中
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
            
            // 更新使用者的習慣行為
            for (String token : tokens) {
                user.bumpHabit(token);
            }
            
            pages.add(p);
        }

        // 樹結構和分數排名
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
    /**
     * 試圖從公共時間 API 取得目前的日期（UTC 或區域時間），若失敗則回傳本機系統日期。
     */
    private static LocalDate fetchCurrentDate() {
        // worldtimeapi.org 提供簡單的 JSON 回應，其中包含 datetime 欄位
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
                // 找出 "datetime" 字段的值（例如 "2025-11-20T13:45:00.123+08:00"）
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
                        } catch (Exception ex) {
                            // ignore parse error and fallback
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore network/timeout errors and fallback to system date
        }
        return LocalDate.now();
    }
}