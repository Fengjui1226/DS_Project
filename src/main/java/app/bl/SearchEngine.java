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
 * SearchEngine - 搜尋演算法（改進版 v2）
 * 
 * 🎯 核心宗旨：只顯示「還沒過期」的活動
 * 
 * 改進內容：
 * 1. 強化時間過濾 - 嚴格過濾已過期活動
 * 2. 加入社群媒體來源（IG、FB）
 * 3. 排除申請/辦法/須知頁面
 * 4. 優先保留售票網站結果
 */
public class SearchEngine {

    // 活動相關關鍵字
    private static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "festival", "concert", "exhibition", "event",
        "表演", "藝術", "體驗", "親子", "戶外", "講座",
        "工作坊", "派對", "路跑", "馬拉松"
    );
    
    // 售票 & 活動平台（優先保留）
    private static final Set<String> PRIORITY_DOMAINS = Set.of(
        "kktix", "accupass", "tixcraft", "opentix", 
        "ticket", "ibon", "udnfunlife", "ticketplus",
        "instagram", "facebook", "fb.com"
    );
    
    // 首頁 URL 模式（應該降權或過濾）
    private static final Set<String> HOMEPAGE_PATTERNS = Set.of(
        "/index.html", "/index.php", "/index.aspx",
        "/home", "/main", "/default.aspx"
    );
    
    // 應該排除的頁面類型
    private static final Set<String> EXCLUDE_KEYWORDS = Set.of(
        "申請", "補助辦法", "徵選辦法", "作業要點", "實施計畫",
        "徵件須知", "注意事項", "相關規定", "法規", "條例",
        "下載專區", "表格下載", "申請表"
    );

    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
        Map.entry("新北", "新北"),
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"),
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        Map.entry("桃園", "桃園"), Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹"),
        Map.entry("嘉義", "嘉義"), Map.entry("宜蘭", "宜蘭"), Map.entry("花蓮", "花蓮"),
        Map.entry("台東", "台東"), Map.entry("苗栗", "苗栗"), Map.entry("彰化", "彰化"),
        Map.entry("南投", "南投"), Map.entry("雲林", "雲林"), Map.entry("屏東", "屏東")
    );

    private static Tree lastSearchTree;

    /**
     * 主要搜尋方法
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        // 確保城市被加入查詢
        String userCity = user.getUserCity();
        if (userCity != null && !userCity.isEmpty()) {
            String queryLower = query.toLowerCase();
            boolean hasCity = CITY_ALIASES.keySet().stream()
                .anyMatch(alias -> queryLower.contains(alias.toLowerCase()));
            if (!hasCity) {
                query = userCity + " " + query;
            }
        }
        
        String refinedQuery = refineQuery(query);

        // 搜尋 Google
        List<GoogleConnector.Result> raw = GoogleConnector.search(refinedQuery, 10);
        
        // 🕐 取得當前日期（用於過濾過期活動）
        LocalDate today = fetchCurrentDate();
        
        // 過濾結果
        List<GoogleConnector.Result> filtered = filterResults(raw, today);
        
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

        for (GoogleConnector.Result r : filtered) {
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

            // 🕐 解析日期
            LocalDate eventDate = extractDateFromTitle(r.title);

            // 🚫【核心過濾】已過期的活動 - 嚴格過濾！
            if (eventDate != null && eventDate.isBefore(today)) {
                System.out.println("[過濾] 已過期活動: " + r.title + " (日期: " + eventDate + ")");
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
        
        // 輸出搜尋結果統計
        System.out.println("[搜尋完成] 共 " + pages.size() + " 筆未過期活動");
        
        return pages;
    }
    
    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }

    /**
     * 查詢優化 - 加入活動相關詞彙，排除申請類頁面
     */
    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase(Locale.ROOT);
        boolean looksLikeCity = CITY_ALIASES.keySet().stream()
            .anyMatch(alias -> lower.contains(alias.toLowerCase(Locale.ROOT)));

        if (looksLikeCity) {
            q += " 活動 OR 展覽 OR 演唱會 OR 音樂會 OR 市集";
        }
        
        // 加入時間相關詞，優先找近期活動
        q += " 2024 OR 2025";
        
        // 排除申請/辦法頁面
        q += " -申請辦法 -徵選 -補助要點 -下載專區";
        
        return q;
    }

    /**
     * 🕐 改進版結果過濾 - 強化時間過濾 + 過濾首頁連結
     */
    private static List<GoogleConnector.Result> filterResults(List<GoogleConnector.Result> input, LocalDate today) {
        List<GoogleConnector.Result> specificPageResults = new ArrayList<>();  // 具體頁面（最優先）
        List<GoogleConnector.Result> priorityResults = new ArrayList<>();      // 售票/社群網站
        List<GoogleConnector.Result> normalResults = new ArrayList<>();        // 一般結果
        List<GoogleConnector.Result> homepageResults = new ArrayList<>();      // 首頁（最低優先）
        
        for (GoogleConnector.Result r : input) {
            if (r == null || r.title == null || r.link == null) continue;
            if (r.title.contains("Google Custom Search")) continue;
            
            String title = r.title.toLowerCase(Locale.ROOT);
            String link = r.link.toLowerCase(Locale.ROOT);
            
            // 🚫 排除申請/辦法頁面
            boolean shouldExclude = EXCLUDE_KEYWORDS.stream()
                .anyMatch(kw -> title.contains(kw.toLowerCase()));
            if (shouldExclude) {
                System.out.println("[過濾] 申請/辦法頁面: " + r.title);
                continue;
            }
            
            // 🕐 檢查標題中的日期是否已過期
            LocalDate titleDate = extractDateFromTitle(r.title);
            if (titleDate != null && titleDate.isBefore(today)) {
                System.out.println("[過濾] 標題含過期日期: " + r.title);
                continue;
            }
            
            // 🏠 檢查是否為首頁（應該降權）
            boolean isHomepage = isHomepageUrl(r.link);
            if (isHomepage) {
                System.out.println("[降權] 首頁連結: " + r.link);
                homepageResults.add(r);
                continue;  // 放到最後
            }
            
            // 檢查是否為優先網站（售票/社群）
            boolean isPrioritySite = PRIORITY_DOMAINS.stream()
                .anyMatch(domain -> link.contains(domain));
            
            // 檢查是否含活動關鍵字
            boolean hasEventTerm = EVENT_TERMS.stream()
                .anyMatch(term -> title.contains(term.toLowerCase()) || link.contains(term.toLowerCase()));
            
            // 🎯 檢查 URL 是否為具體頁面（有明確路徑）
            boolean isSpecificPage = isSpecificPageUrl(r.link);
            
            if (isSpecificPage && isPrioritySite) {
                specificPageResults.add(r);  // 具體頁面 + 優先網站 = 最高優先
            } else if (isPrioritySite) {
                priorityResults.add(r);
            } else if (hasEventTerm) {
                normalResults.add(r);
            }
        }
        
        // 合併結果：具體頁面 > 優先網站 > 一般 > 首頁
        List<GoogleConnector.Result> combined = new ArrayList<>();
        combined.addAll(specificPageResults);
        combined.addAll(priorityResults);
        combined.addAll(normalResults);
        combined.addAll(homepageResults);  // 首頁放最後
        
        // 如果過濾後沒有結果，回傳排除明顯非活動頁面的原始結果
        if (combined.isEmpty()) {
            for (GoogleConnector.Result r : input) {
                if (r == null || r.title == null || r.link == null) continue;
                String title = r.title.toLowerCase();
                
                boolean exclude = EXCLUDE_KEYWORDS.stream()
                    .anyMatch(kw -> title.contains(kw.toLowerCase()));
                if (!exclude) {
                    combined.add(r);
                }
            }
        }
        
        return combined;
    }
    
    /**
     * 🏠 檢查是否為首頁 URL
     */
    private static boolean isHomepageUrl(String url) {
        if (url == null) return false;
        
        try {
            String u = url.toLowerCase(Locale.ROOT);
            
            // 移除 protocol
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            
            // 移除 domain
            int s = u.indexOf('/');
            if (s < 0) return true;  // 沒有路徑 = 首頁
            
            String path = u.substring(s);
            
            // 只有 "/" = 首頁
            if (path.equals("/")) return true;
            
            // 常見首頁模式
            for (String pattern : HOMEPAGE_PATTERNS) {
                if (path.equals(pattern) || path.startsWith(pattern + "?")) {
                    return true;
                }
            }
            
            // 路徑太短（如 /tw, /zh）通常是首頁
            if (path.length() <= 4 && !path.contains(".")) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 🎯 檢查是否為具體頁面 URL（有明確的文章/活動路徑）
     */
    private static boolean isSpecificPageUrl(String url) {
        if (url == null) return false;
        
        String u = url.toLowerCase(Locale.ROOT);
        
        // 具體頁面通常有這些路徑特徵
        String[] specificPatterns = {
            "/event/", "/events/", "/activity/", "/activities/",
            "/article/", "/post/", "/news/", "/detail/",
            "/show/", "/concert/", "/exhibition/",
            "/p/", "/id/", "/item/", "/view/",
            ".html", ".htm", ".php", ".aspx"
        };
        
        for (String pattern : specificPatterns) {
            if (u.contains(pattern)) {
                return true;
            }
        }
        
        // URL 路徑有數字 ID 通常是具體頁面
        if (u.matches(".*/(\\d{4,}|[a-f0-9]{8,}).*")) {
            return true;
        }
        
        return false;
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
    
    /**
     * 🕐 從標題中提取日期
     */
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
        
        // Pattern 2: 10/26 或 10.26（假設今年或明年）
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
        
        // Pattern 4: 民國年 113/10/26
        Pattern p4 = Pattern.compile("(11[3-9])[./\\-](\\d{1,2})[./\\-](\\d{1,2})");
        Matcher m4 = p4.matcher(title);
        while (m4.find()) {
            try {
                int rocYear = Integer.parseInt(m4.group(1));
                int year = rocYear + 1911;  // 民國轉西元
                int month = Integer.parseInt(m4.group(2));
                int day = Integer.parseInt(m4.group(3));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    foundDates.add(LocalDate.of(year, month, day));
                }
            } catch (Exception e) {}
        }
        
        if (foundDates.isEmpty()) return null;
        
        // 回傳最晚的日期（活動結束日期）
        return foundDates.stream().max(LocalDate::compareTo).orElse(null);
    }
    
    /**
     * 從時間 API 取得當前日期
     */
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