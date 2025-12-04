package app.bl;

import app.da.GoogleConnector;
import app.da.LocationRecognizer;
import app.bl.WebCrawler.*;
import java.time.LocalDate;
import java.util.*;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.regex.*;
import java.util.concurrent.*;

/**
 * SearchEngine - 整合爬蟲版本
 * 
 * 流程：
 * 1. Google 搜尋 → 取得大網頁 URL
 * 2. 爬蟲爬取每個大網頁 → 取得內容 + 子連結
 * 3. 爬取子網頁 → 取得子網頁內容
 * 4. 計算分數 → 大網頁分數 + Σ子網頁分數
 * 5. 排序顯示
 */
public class SearchEngine {

    // 設定
    private static final int MAX_GOOGLE_RESULTS = 10;    // Google 回傳數量
    private static final boolean ENABLE_CRAWLING = true; // 是否啟用爬蟲
    
    // 活動相關關鍵字
    private static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "festival", "concert", "exhibition", "event",
        "表演", "藝術", "體驗", "親子", "戶外", "講座"
    );
    
    // 城市別名
    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
        Map.entry("新北", "新北"),
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"),
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        Map.entry("桃園", "桃園"), Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹")
    );

    // 儲存最後結果
    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    /**
     * 主要搜尋方法
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n========== 開始搜尋 ==========");
        System.out.println("[Query] " + query);
        
        long startTime = System.currentTimeMillis();
        
        // 1. 確保城市在查詢中
        String userCity = user.getUserCity();
        if (userCity != null && !userCity.isEmpty()) {
            String queryLower = query.toLowerCase();
            boolean hasCity = CITY_ALIASES.keySet().stream()
                .anyMatch(alias -> queryLower.contains(alias.toLowerCase()));
            if (!hasCity) {
                query = userCity + " " + query;
            }
        }
        
        // 2. 優化查詢
        String refinedQuery = refineQuery(query);
        System.out.println("[Refined] " + refinedQuery);

        // 3. 呼叫 Google 搜尋
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults = GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS);
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        // 4. 解析查詢 tokens
        List<String> queryTokens = parseQueryTokens(query);
        System.out.println("[Tokens] " + queryTokens);

        // 5. 建立 PageNode 並爬取
        System.out.println("\n[Step 2] 爬取網頁內容...");
        List<PageNode> pages = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            
            // 建立 PageNode
            PageNode page = createPageNode(r, query, queryTokens, userCity, today);
            if (page == null) continue;
            
            // 爬取網頁內容和子網頁
            if (ENABLE_CRAWLING) {
                crawlPageAndSubpages(page, queryTokens);
            }
            
            pages.add(page);
        }
        
        System.out.println("[Crawl] 完成爬取 " + pages.size() + " 個網站");

        // 6. 計算分數
        System.out.println("\n[Step 3] 計算分數...");
        RankCalculator.rank(pages, user);

        // 7. 建立樹結構
        Tree tree = new Tree();
        tree.addPages(pages);
        lastSearchTree = tree;
        lastResults = pages;

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n========== 搜尋完成 ==========");
        System.out.println("[Time] " + duration + " ms");
        System.out.println("[Results] " + pages.size() + " 個網站");
        
        // 顯示結果摘要
        printResultsSummary(pages);

        return pages;
    }

    /**
     * 爬取頁面內容和子網頁（有超時控制）
     */
    private static void crawlPageAndSubpages(PageNode page, List<String> queryTokens) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    // 爬取網站（包含子網頁）
                    SiteResult site = WebCrawler.crawlSite(page.getUrl());
                    
                    if (site.getMainPage() != null && site.getMainPage().isSuccess()) {
                        // 設定主網頁內容
                        page.setTextContent(site.getMainPage().getTextContent());
                        page.setCrawled(true);
                        
                        // 處理子網頁
                        for (CrawlResult subResult : site.getSubPages()) {
                            SubPageNode subPage = new SubPageNode(
                                subResult.getUrl(),
                                subResult.getTitle(),
                                subResult.getTextContent(),
                                page.getUrl()
                            );
                            
                            // 計算子網頁的 TF
                            subPage.calculateTF(queryTokens);
                            
                            page.addSubPage(subPage);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Crawl Error] " + page.getUrl() + " - " + e.getMessage());
                }
            });
            
            // 等待最多 10 秒
            future.get(10, TimeUnit.SECONDS);
            
        } catch (TimeoutException e) {
            System.out.println("[Crawl Timeout] " + page.getUrl());
        } catch (Exception e) {
            System.out.println("[Crawl Error] " + page.getUrl() + " - " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 建立 PageNode
     */
    private static PageNode createPageNode(GoogleConnector.Result r, String query, 
            List<String> queryTokens, String userCity, LocalDate today) {
        
        // 過濾
        if (r.title.contains("Google Custom Search")) return null;
        if (shouldExclude(r.title, r.link)) return null;
        
        // 提取日期
        LocalDate eventDate = extractDateFromTitle(r.title, today);
        
        // 已過期則跳過
        if (eventDate != null && eventDate.isBefore(today)) {
            System.out.println("[Skip] 已過期: " + r.title);
            return null;
        }
        
        // 計算詞頻
        Map<Keyword, Integer> tf = new HashMap<>();
        for (String token : queryTokens) {
            Keyword k = Keyword.of(token);
            tf.put(k, tf.getOrDefault(k, 0) + 1);
        }
        
        // 加入活動關鍵字
        String titleLower = r.title.toLowerCase();
        for (String term : EVENT_TERMS) {
            if (titleLower.contains(term.toLowerCase())) {
                Keyword k = Keyword.of(term);
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
        }
        
        // 偵測城市
        String city = LocationRecognizer.extractCity(r.title);
        if (city == null || city.isEmpty()) {
            city = detectCityFromQuery(query);
        }
        if (city == null) city = userCity != null ? userCity : "";
        
        // 建立節點
        List<String> tokens = new ArrayList<>(queryTokens);
        return PageNode.of(
            r.link,
            r.title,
            tf,
            eventDate,
            city,
            extractDomain(r.link),
            tokens
        );
    }

    /**
     * 查詢優化
     */
    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase();
        boolean hasCity = CITY_ALIASES.keySet().stream()
            .anyMatch(alias -> lower.contains(alias.toLowerCase()));

        if (hasCity) {
            q += " 活動 OR 展覽 OR 演唱會";
        }
        
        // 加入時間
        q += " 2024 OR 2025";
        
        // 排除申請/辦法
        q += " -申請辦法 -徵選 -補助";
        
        return q;
    }

    /**
     * 解析查詢 tokens
     */
    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            t = t.trim();
            if (!t.isEmpty() && t.length() >= 2 && !t.startsWith("-")) {
                // 移除 OR, AND 等
                if (!t.equalsIgnoreCase("OR") && !t.equalsIgnoreCase("AND")) {
                    tokens.add(t);
                }
            }
        }
        return tokens;
    }

    /**
     * 是否應該排除
     */
    private static boolean shouldExclude(String title, String url) {
        String t = title.toLowerCase();
        String u = url.toLowerCase();
        
        Set<String> excludeKeywords = Set.of(
            "申請", "補助辦法", "徵選辦法", "作業要點",
            "徵件須知", "注意事項", "法規", "條例",
            "下載專區", "表格下載"
        );
        
        for (String kw : excludeKeywords) {
            if (t.contains(kw) || u.contains(kw)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 從標題提取日期
     */
    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        if (title == null) return null;
        
        // 2024/10/26
        Pattern p1 = Pattern.compile("(202\\d)[./\\-](\\d{1,2})[./\\-](\\d{1,2})");
        Matcher m1 = p1.matcher(title);
        if (m1.find()) {
            try {
                return LocalDate.of(
                    Integer.parseInt(m1.group(1)),
                    Integer.parseInt(m1.group(2)),
                    Integer.parseInt(m1.group(3))
                );
            } catch (Exception e) {}
        }
        
        // 10月26日
        Pattern p2 = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
        Matcher m2 = p2.matcher(title);
        if (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                LocalDate date = LocalDate.of(today.getYear(), month, day);
                if (date.isBefore(today)) {
                    date = date.plusYears(1);
                }
                return date;
            } catch (Exception e) {}
        }
        
        return null;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return "";
        String lower = query.toLowerCase();
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) {
                return e.getValue();
            }
        }
        return "";
    }

    private static String extractDomain(String url) {
        try {
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "";
        }
    }

    private static void printResultsSummary(List<PageNode> pages) {
        System.out.println("\n--- 結果摘要 ---");
        int rank = 1;
        for (PageNode p : pages) {
            System.out.printf("#%d [%.1f] %s (%d 子網頁)%n", 
                rank++, p.getTotalScore(), p.getTitle(), p.getSubPageCount());
            if (rank > 5) break;
        }
    }

    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }
    
    public static List<PageNode> getLastResults() {
        return lastResults;
    }
}