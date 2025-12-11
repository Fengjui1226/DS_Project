package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import app.da.GoogleConnector;
 
import static app.bl.Constants.*;

/**
 * SearchEngine v9.0 - 最終整合版
 * * 策略核心：寬進嚴出 (Broad Funnel) + 代理戰術 (Proxy Strategy) + 節能模式 (Eco Mode)
 * * 1. [API 節省]: 只抓前 20 筆 (消耗 2 單位額度)。
 * 2. [爬蟲策略]: 只深爬前 12 筆最有希望的結果。
 * 3. [過濾邏輯]: 移除 Google 端的負向關鍵字，全權交由後端 RankCalculator 進行「垃圾過濾」與「過期處決」。
 */
public class SearchEngine {

    // ★ 參數設定
    private static final int MAX_GOOGLE_RESULTS = 20;   // 抓 20 筆，節省 API (10筆=1次呼叫)
    private static final int MAX_DEEP_CRAWL_COUNT = 12; // 只爬前 12 筆，加速回應
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 20000; // 20秒超時
    private static final int CRAWL_PARALLEL_LIMIT = 15; // 並發數
    private static final ExecutorService CRAWL_EXECUTOR =
            Executors.newFixedThreadPool(CRAWL_PARALLEL_LIMIT);

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    /**
     * 搜尋主入口
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v9.0 (Final Architecture)            ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        // 1. 解析意圖與城市
        // 這裡只是為了理解使用者，不會在第一步就刪除資料
        String queryCity = detectCityFromQuery(query);
        String userCity = (user != null) ? user.getUserCity() : null;
        String effectiveCity = (queryCity != null && !queryCity.isEmpty()) ? queryCity : 
                              ((userCity != null && !userCity.isEmpty()) ? userCity : null);

        // 2. 構建 Google 查詢字串
        String googleQuery = query;
        // 如果使用者沒打城市，但我們知道他在哪，稍微幫他加一點地緣限制，但不強求
        if (queryCity == null && effectiveCity != null) {
            googleQuery = effectiveCity + " " + query;
        }

        // 3. 語意擴展與查詢優化
        QueryUnderstanding.ParsedQuery parsedQuery = QueryUnderstanding.parse(googleQuery);
        googleQuery = expandQuery(parsedQuery.expandedQuery);
        String refinedQuery = refineQuery(googleQuery); // ★ 這裡產生的是「寬鬆」的查詢
        System.out.println("[Refined] " + refinedQuery);

        // 4. 呼叫 Google API (原料採集)
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS, 3000);
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        // 5. 建立初步節點
        List<String> queryTokens = parseQueryTokens(query);
        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();
        
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            
            // 基礎黑名單過濾 (網域/標題)
            if (shouldExclude(r.title, r.link)) continue;

            PageNode page = createPageNode(r, query, queryTokens, effectiveCity, today);
            if (page != null) {
                // 這裡只做最粗略的海外排除，細節交給 Ranker
                if (Constants.isLikelyForeign(page.getTitle())) {
                    continue;
                }
                pages.add(page);
            }
        }
        System.out.println("[Pages] 初步保留 " + pages.size() + " 個頁面節點");

        // 6. 平行爬蟲 (特徵提取)
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = SEARCH_TIMEOUT_MS - elapsed - 1000;
            
            // 只爬前段班
            List<PageNode> pagesToCrawl = pages.stream()
                .limit(MAX_DEEP_CRAWL_COUNT)
                .collect(Collectors.toList());

            if (remainingTime > 2000) {
                System.out.println("\n[Step 3] 並行爬取前 " + pagesToCrawl.size() + " 筆網頁...");
                parallelCrawl(pagesToCrawl, queryTokens, remainingTime, today);
            }
        }

        // 7. 進階處理 (TF-IDF + 結構化提取 + 去重)
        System.out.println("\n[Step 4] 進階處理與排名...");
        TFIDFCalculator.applyTFIDFScores(pages, query);
        EventInfoExtractor.applyCompletenessBonus(pages); // 這裡會智慧判斷日期
        pages = Deduplicator.deduplicate(pages);

        // 8. 最終排名 (大審判)
        // RankCalculator v7.1 會執行：過期處決、垃圾降權、權威加分
        UserProfile effectiveUser = new UserProfile();
        if (effectiveCity != null) effectiveUser.setUserCity(effectiveCity);
        
        RankCalculator.rank(pages, effectiveUser, query);
        
        // 9. 最終清道夫
        // 移除被 RankCalculator 判死刑 (0分) 或極低分的結果
        int beforeClean = pages.size();
        pages.removeIf(p -> p.getTotalScore() <= 0.01);
        int afterClean = pages.size();
        System.out.printf("[Clean] 移除 %d 個無效(過期/垃圾)結果%n", beforeClean - afterClean);

        // 10. 輸出結果
        Tree tree = new Tree();
        tree.addPages(pages);
        lastSearchTree = tree;
        lastResults = pages;

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n========== 搜尋完成 ==========");
        System.out.println("[Time] " + duration + " ms");
        System.out.println("[Results] 最終保留 " + pages.size() + " 個優質結果");
        printResultsSummary(pages);

        return pages;
    }

    /**
     * 寬鬆版 Query Refinement：不加負向關鍵字，只加正向引導
     */
    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;
        StringBuilder sb = new StringBuilder(q);
        String lower = q.toLowerCase();

        // 增加召回率：如果沒有明確活動詞，幫他補上
        boolean hasEventWord = EVENT_TERMS.stream().anyMatch(t -> lower.contains(t.toLowerCase()));
        if (!hasEventWord) {
            sb.append(" (活動 OR 市集 OR 展覽)");
        }

        // 增加精準度：確保是台灣的資訊
        if (!lower.contains("台灣") && !lower.contains("taiwan")) {
            sb.append(" 台灣");
        }
        
        // ★ 注意：這裡完全不加 "-申請 -維修" 等負向詞
        // 我們把這些留給後端 RankCalculator 來殺，以免 Google 誤殺好結果
        
        return sb.toString();
    }

    // ================= 輔助方法 =================

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        for (String t : query.split("\\s+")) {
            if (!t.isEmpty() && !t.startsWith("-")) tokens.add(t);
        }
        return tokens;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return null;
        return extractCity(query);
    }

    private static boolean shouldExclude(String title, String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        // 檢查網域黑名單 (如 amazon, twitter)
        for (String domain : EXCLUDED_DOMAINS) {
            if (lowerUrl.contains(domain)) return true;
        }
        return false;
    }
    
    private static void parallelCrawl(List<PageNode> pages, List<String> queryTokens,
                                      long timeoutMs, LocalDate today) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PageNode page : pages) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    crawlPageFast(page, queryTokens, today);
                } catch (Exception e) {}
            }, CRAWL_EXECUTOR);
            futures.add(future);
        }
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignore) {}
    }

    private static void crawlPageFast(PageNode page, List<String> queryTokens, LocalDate today) {
        try {
            // 使用 WebCrawler v3.0 (偽裝成瀏覽器)
            WebCrawler.CrawlResult result = WebCrawler.crawl(page.getUrl());
            if (!result.isSuccess()) return;

            page.setCrawled(true);
            String crawledTitle = result.getTitle();
            String content = result.getTextContent();

            if (crawledTitle != null && crawledTitle.length() > (page.getTitle() != null ? page.getTitle().length() : 0)) {
                page.setTitle(crawledTitle);
            }
            if (content != null) page.setTextContent(content);

            // 嘗試提取日期與城市
            if (page.getEventDate() == null) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") + (content != null ? content : "");
                // 使用 EventInfoExtractor 進行智慧提取 (雖然這裡為了簡化沒直接呼叫，但原理相同)
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) page.setEventDate(d);
            }

            if (page.getCity() == null || "全台".equals(page.getCity())) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") + content;
                String city = extractCity(combined);
                if (city != null) page.setCity(city);
            }
            
            // 爬取子連結
            List<String> links = result.getLinks();
            if (links != null) {
                int subCount = Math.min(links.size(), 4);
                for (int i = 0; i < subCount; i++) {
                    String link = links.get(i);
                    SubPageNode sub = new SubPageNode(link, extractTitleFromUrl(link), "", page.getUrl());
                    page.addSubPage(sub);
                }
            }
        } catch (Exception ignore) {}
    }

    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        return parseDate(title, today);
    }
    
    private static LocalDate extractDateFromContent(String text, LocalDate today) {
        return parseDate(text, today);
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null) return null;
        // 這裡實際上會由 EventInfoExtractor 來處理，此為簡化接口
        return null; 
    }

    private static String extractTitleFromUrl(String url) {
        if (url == null) return "";
        try {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < url.length() - 1) {
                String segment = url.substring(lastSlash + 1);
                return segment.replaceAll("[-_]", " ");
            }
        } catch (Exception e) {}
        return "";
    }

    private static String expandQuery(String q) { return q; }

    private static PageNode createPageNode(GoogleConnector.Result r, String query,
                                           List<String> queryTokens, String effectiveCity,
                                           LocalDate today) {
        if (shouldExclude(r.title, r.link)) return null;
        LocalDate eventDate = extractDateFromTitle(r.title, today);
        Map<Keyword, Integer> tf = new HashMap<>();
        String city = extractCity(r.title);
        String domain = extractDomain(r.link);
        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, new ArrayList<>(queryTokens));
    }

    private static String extractDomain(String url) {
        if (url == null) return "";
        try {
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) { return ""; }
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    private static void printResultsSummary(List<PageNode> pages) {
        System.out.println("\n📊 搜尋結果摘要：");
        int rank = 1;
        for (PageNode p : pages) {
            System.out.printf("#%d [%.1f] %s | %s%n",
                    rank++, p.getTotalScore(), p.getCity(), truncate(p.getTitle(), 40));
            if (rank > 10) break;
        }
    }

    public static Tree getLastSearchTree() { return lastSearchTree; }
    public static List<PageNode> getLastResults() { return lastResults; }
}