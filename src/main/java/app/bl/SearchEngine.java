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
 * SearchEngine - 高效能版本
 * 
 * 優化重點：
 * 1. 動態年份生成（不再硬編碼）
 * 2. 並行爬蟲（共享 Thread Pool）
 * 3. 更智慧的查詢優化
 * 4. 更精準的活動過濾
 */
public class SearchEngine {

    // ============ 設定 ============
    private static final int MAX_GOOGLE_RESULTS = 10;
    private static final boolean ENABLE_CRAWLING = true;
    private static final int CRAWL_PARALLELISM = 4;  // 並行爬蟲數
    private static final int CRAWL_TIMEOUT_SECONDS = 12;  // 總爬蟲超時
    
    // 共享的執行緒池（重複使用，效能提升）
    private static final ExecutorService CRAWLER_POOL = Executors.newFixedThreadPool(
        CRAWL_PARALLELISM,
        r -> {
            Thread t = new Thread(r, "Crawler-Pool");
            t.setDaemon(true);
            return t;
        }
    );
    
    // 活動相關關鍵字（加權分類）
    private static final Map<String, Double> EVENT_TERMS_WEIGHTED = Map.ofEntries(
        // 高相關性
        Map.entry("演唱會", 2.0), Map.entry("音樂節", 2.0), Map.entry("展覽", 1.8),
        Map.entry("concert", 2.0), Map.entry("festival", 2.0), Map.entry("exhibition", 1.8),
        // 中相關性
        Map.entry("活動", 1.5), Map.entry("市集", 1.5), Map.entry("節慶", 1.5),
        Map.entry("表演", 1.4), Map.entry("音樂會", 1.4), Map.entry("藝術", 1.3),
        Map.entry("event", 1.5), Map.entry("market", 1.5),
        // 一般相關性
        Map.entry("體驗", 1.2), Map.entry("親子", 1.2), Map.entry("戶外", 1.2),
        Map.entry("講座", 1.1), Map.entry("工作坊", 1.1)
    );
    
    // 城市別名（含英文、簡體）
    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
        Map.entry("新北", "新北"), Map.entry("newtaipei", "新北"),
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"), Map.entry("tainan", "台南"),
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        Map.entry("桃園", "桃園"), Map.entry("taoyuan", "桃園"),
        Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹"), Map.entry("hsinchu", "新竹")
    );

    // 排除關鍵字（這些通常不是活動頁面）
    private static final Set<String> EXCLUDE_KEYWORDS = Set.of(
        "申請辦法", "補助要點", "徵選辦法", "作業規定", "表格下載",
        "法規查詢", "行政規則", "招標公告", "採購公告"
    );

    // 儲存最後結果
    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    /**
     * 主要搜尋方法
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║       🔍 EventFinder 搜尋引擎 v2.0       ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("[Query] " + query);
        
        long startTime = System.currentTimeMillis();
        
        // 1. 確保城市在查詢中
        String userCity = user.getUserCity();
        query = ensureCityInQuery(query, userCity);
        
        // 2. 優化查詢（動態年份）
        String refinedQuery = refineQuery(query);
        System.out.println("[Refined] " + refinedQuery);

        // 3. 呼叫 Google 搜尋
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults = GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS);
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        // 4. 解析查詢 tokens
        List<String> queryTokens = parseQueryTokens(query);
        System.out.println("[Tokens] " + queryTokens);

        // 5. 建立 PageNode（先建立，不爬取）
        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            
            PageNode page = createPageNode(r, query, queryTokens, userCity, today);
            if (page != null) {
                pages.add(page);
            }
        }
        
        System.out.println("[Created] " + pages.size() + " 個有效節點");

        // 6. 並行爬取（效能優化核心）
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            System.out.println("\n[Step 3] 並行爬取網頁內容（" + CRAWL_PARALLELISM + " 執行緒）...");
            parallelCrawl(pages, queryTokens);
        }

        // 7. 計算分數
        System.out.println("\n[Step 4] 計算排名分數...");
        RankCalculator.rank(pages, user);

        // 8. 建立樹結構
        Tree tree = new Tree();
        tree.addPages(pages);
        lastSearchTree = tree;
        lastResults = pages;

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.printf("║  ✅ 搜尋完成！耗時 %d ms，%d 個結果      ║%n", duration, pages.size());
        System.out.println("╚══════════════════════════════════════════╝");
        
        printResultsSummary(pages);

        return pages;
    }

    /**
     * 並行爬取所有頁面（效能大幅提升）
     */
    private static void parallelCrawl(List<PageNode> pages, List<String> queryTokens) {
        // 建立爬取任務
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (PageNode page : pages) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                crawlPageAndSubpages(page, queryTokens);
            }, CRAWLER_POOL);
            
            futures.add(future);
        }
        
        // 等待所有任務完成（有總超時限制）
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            allFutures.get(CRAWL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[Crawl] ✓ 所有爬蟲任務完成");
            
        } catch (TimeoutException e) {
            System.out.println("[Crawl] ⚠ 爬蟲超時，使用已完成的結果");
            // 取消未完成的任務
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            System.out.println("[Crawl] ⚠ 爬蟲異常: " + e.getMessage());
        }
        
        // 統計爬取結果
        long crawledCount = pages.stream().filter(PageNode::isCrawled).count();
        long totalSubpages = pages.stream().mapToInt(PageNode::getSubPageCount).sum();
        System.out.printf("[Crawl] 成功爬取 %d/%d 網站，共 %d 子網頁%n", 
            crawledCount, pages.size(), totalSubpages);
    }

    /**
     * 爬取單一頁面及其子網頁
     */
    private static void crawlPageAndSubpages(PageNode page, List<String> queryTokens) {
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
            System.out.println("[Crawl Error] " + page.getDomain() + " - " + e.getMessage());
        }
    }

    /**
     * 確保城市在查詢中
     */
    private static String ensureCityInQuery(String query, String userCity) {
        if (userCity == null || userCity.isEmpty()) return query;
        
        String queryLower = query.toLowerCase();
        boolean hasCity = CITY_ALIASES.keySet().stream()
            .anyMatch(alias -> queryLower.contains(alias.toLowerCase()));
        
        if (!hasCity) {
            return userCity + " " + query;
        }
        return query;
    }

    /**
     * 查詢優化（動態年份 + 智慧過濾）
     */
    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        StringBuilder refined = new StringBuilder(q);
        String lower = q.toLowerCase();
        
        // 檢查是否有城市
        boolean hasCity = CITY_ALIASES.keySet().stream()
            .anyMatch(alias -> lower.contains(alias.toLowerCase()));

        // 如果有城市，加入活動相關詞
        if (hasCity) {
            refined.append(" 活動 OR 展覽 OR 演唱會 OR 市集");
        }
        
        // 動態年份（當前年份和下一年）
        int currentYear = LocalDate.now().getYear();
        int nextYear = currentYear + 1;
        refined.append(" ").append(currentYear).append(" OR ").append(nextYear);
        
        // 排除非活動頁面
        refined.append(" -申請辦法 -徵選 -補助 -招標 -法規");
        
        // 優先活動平台
        refined.append(" site:accupass.com OR site:kktix.com OR site:klook.com");
        
        return refined.toString();
    }

    /**
     * 解析查詢 tokens（改進版）
     */
    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        for (String t : query.split("\\s+")) {
            t = t.trim().toLowerCase();
            
            // 跳過太短、重複、或特殊詞
            if (t.length() < 2) continue;
            if (seen.contains(t)) continue;
            if (t.startsWith("-")) continue;
            if (t.equalsIgnoreCase("OR") || t.equalsIgnoreCase("AND")) continue;
            if (t.startsWith("site:")) continue;
            
            // 跳過純數字（年份）
            if (t.matches("\\d+")) continue;
            
            seen.add(t);
            tokens.add(t);
        }
        
        return tokens;
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
            System.out.println("[Skip] 已過期: " + truncate(r.title, 40));
            return null;
        }
        
        // 計算詞頻（使用加權）
        Map<Keyword, Integer> tf = new HashMap<>();
        for (String token : queryTokens) {
            Keyword k = Keyword.of(token);
            tf.put(k, tf.getOrDefault(k, 0) + 1);
        }
        
        // 加入活動關鍵字（帶權重）
        String titleLower = r.title.toLowerCase();
        for (Map.Entry<String, Double> entry : EVENT_TERMS_WEIGHTED.entrySet()) {
            if (titleLower.contains(entry.getKey().toLowerCase())) {
                Keyword k = Keyword.of(entry.getKey());
                int weight = (int) Math.ceil(entry.getValue());
                tf.put(k, tf.getOrDefault(k, 0) + weight);
            }
        }
        
        // 偵測城市
        String city = LocationRecognizer.extractCity(r.title);
        if (city == null || city.isEmpty()) {
            city = detectCityFromQuery(query);
        }
        if (city == null) city = userCity != null ? userCity : "";
        
        // 建立節點
        return PageNode.of(
            r.link,
            r.title,
            tf,
            eventDate,
            city,
            extractDomain(r.link),
            new ArrayList<>(queryTokens)
        );
    }

    /**
     * 是否應該排除
     */
    private static boolean shouldExclude(String title, String url) {
        String t = title.toLowerCase();
        String u = url.toLowerCase();
        
        for (String kw : EXCLUDE_KEYWORDS) {
            if (t.contains(kw.toLowerCase()) || u.contains(kw.toLowerCase())) {
                return true;
            }
        }
        
        // 排除政府招標/法規網站
        if (u.contains("law.moj.gov.tw")) return true;
        if (u.contains("pcc.gov.tw")) return true;  // 採購網
        
        return false;
    }

    /**
     * 從標題提取日期（增強版）
     */
    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        if (title == null) return null;
        
        // 格式: 2024/10/26 或 2024-10-26 或 2024.10.26
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
        
        // 格式: 10月26日 或 10/26
        Pattern p2 = Pattern.compile("(\\d{1,2})月(\\d{1,2})日?");
        Matcher m2 = p2.matcher(title);
        if (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    LocalDate date = LocalDate.of(today.getYear(), month, day);
                    if (date.isBefore(today)) {
                        date = date.plusYears(1);
                    }
                    return date;
                }
            } catch (Exception e) {}
        }
        
        // 格式: 12/25 (斜線)
        Pattern p3 = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?!\\d)");
        Matcher m3 = p3.matcher(title);
        if (m3.find()) {
            try {
                int month = Integer.parseInt(m3.group(1));
                int day = Integer.parseInt(m3.group(2));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    LocalDate date = LocalDate.of(today.getYear(), month, day);
                    if (date.isBefore(today)) {
                        date = date.plusYears(1);
                    }
                    return date;
                }
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
    
    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    private static void printResultsSummary(List<PageNode> pages) {
        System.out.println("\n📊 Top 5 結果：");
        System.out.println("─".repeat(60));
        int rank = 1;
        for (PageNode p : pages) {
            String crawlStatus = p.isCrawled() ? "✓" : "✗";
            System.out.printf("#%d [%.1f] %s %s (%d 子頁)%n", 
                rank++, 
                p.getTotalScore(), 
                crawlStatus,
                truncate(p.getTitle(), 35), 
                p.getSubPageCount());
            if (rank > 5) break;
        }
        System.out.println("─".repeat(60));
    }

    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }
    
    public static List<PageNode> getLastResults() {
        return lastResults;
    }
    
    /**
     * 關閉爬蟲執行緒池（應用結束時呼叫）
     */
    public static void shutdown() {
        CRAWLER_POOL.shutdown();
        try {
            if (!CRAWLER_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                CRAWLER_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            CRAWLER_POOL.shutdownNow();
        }
    }
}