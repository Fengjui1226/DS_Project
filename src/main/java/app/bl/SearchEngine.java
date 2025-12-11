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
 * SearchEngine v9.0 - 節能精準版
 * * 優化重點：
 * 1. [API 節省] MAX_GOOGLE_RESULTS 下調至 20 (每次搜尋僅消耗 2 單位額度)。
 * 2. [效能提升] MAX_DEEP_CRAWL_COUNT 下調至 12 (只爬取最有希望的前段班)。
 * 3. [策略] 依賴 RankCalculator v7.1 的高精準度，不需要抓太多垃圾來過濾。
 */
public class SearchEngine {

    // ★ 關鍵修改：節省 API 額度
    // Google API 預設 10 筆算 1 次 request。
    // 設為 20 代表每次搜尋只扣 2 次額度 (原本 50 會扣 5 次)。
    private static final int MAX_GOOGLE_RESULTS = 20;   
    
    // ★ 關鍵修改：加速爬蟲
    // 既然 Google 排名前面的通常相關性較高，我們只深爬前 12 個即可
    private static final int MAX_DEEP_CRAWL_COUNT = 12; 
    
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 18000; // 時間縮短，因為爬的頁面變少了
    private static final int CRAWL_PARALLEL_LIMIT = 15; // 稍微降低並發數，減輕系統負擔
    private static final ExecutorService CRAWL_EXECUTOR =
            Executors.newFixedThreadPool(CRAWL_PARALLEL_LIMIT);

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v9.0 (Eco Mode)                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        // 1. 解析城市與意圖
        String queryCity = detectCityFromQuery(query);
        String userCity = (user != null) ? user.getUserCity() : null;
        String effectiveCity = (queryCity != null && !queryCity.isEmpty()) ? queryCity : 
                              ((userCity != null && !userCity.isEmpty()) ? userCity : null);

        // 2. 準備 Google Query
        String googleQuery = query;
        if (queryCity == null && effectiveCity != null) {
            googleQuery = effectiveCity + " " + query;
        }

        QueryUnderstanding.ParsedQuery parsedQuery = QueryUnderstanding.parse(googleQuery);
        googleQuery = expandQuery(parsedQuery.expandedQuery);
        
        // 保持乾淨的 Query，依舊不加負向關鍵字，但因為抓得少，依賴 Google 自身的排序
        String refinedQuery = refineQuery(googleQuery); 
        System.out.println("[Refined] " + refinedQuery);

        System.out.println("\n[Step 1] 呼叫 Google API...");
        // 這裡會傳入 20，GoogleConnector 應該會發送 2 次請求 (1-10, 11-20)
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS, 3000);
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        List<String> queryTokens = parseQueryTokens(query);

        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            
            if (shouldExclude(r.title, r.link)) continue;

            PageNode page = createPageNode(r, query, queryTokens, effectiveCity, today);
            if (page != null) {
                if (Constants.isLikelyForeign(page.getTitle())) {
                    continue;
                }
                pages.add(page);
            }
        }
        System.out.println("[Pages] 初步保留 " + pages.size() + " 個頁面節點");

        // 3. 爬蟲階段
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = SEARCH_TIMEOUT_MS - elapsed - 1000;
            
            // 只爬前 MAX_DEEP_CRAWL_COUNT (12) 筆
            List<PageNode> pagesToCrawl = pages.stream()
                .limit(MAX_DEEP_CRAWL_COUNT)
                .collect(Collectors.toList());

            if (remainingTime > 2000) {
                System.out.println("\n[Step 3] 並行爬取前 " + pagesToCrawl.size() + " 筆網頁...");
                parallelCrawl(pagesToCrawl, queryTokens, remainingTime, today);
            }
        }

        // 4. 過濾階段
        List<PageNode> filteredPages = new ArrayList<>(pages);
        pages = filteredPages;

        System.out.println("\n[Step 4] 進階處理與排名...");
        TFIDFCalculator.applyTFIDFScores(pages, query);
        EventInfoExtractor.applyCompletenessBonus(pages);
        pages = Deduplicator.deduplicate(pages);

        // 5. 最終排名
        UserProfile effectiveUser = new UserProfile();
        if (effectiveCity != null) effectiveUser.setUserCity(effectiveCity);
        
        RankCalculator.rank(pages, effectiveUser, query);
        
        // 6. 最終清理：因為樣本數少，稍微放寬一點點移除標準，避免結果全滅
        // 但如果有過期處決 (0分)，還是要移除
        int beforeClean = pages.size();
        pages.removeIf(p -> p.getTotalScore() <= 0.01);
        int afterClean = pages.size();
        System.out.printf("[Clean] 移除 %d 個無效結果%n", beforeClean - afterClean);

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

    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;
        StringBuilder sb = new StringBuilder(q);
        String lower = q.toLowerCase();

        boolean hasEventWord = EVENT_TERMS.stream().anyMatch(t -> lower.contains(t.toLowerCase()));
        if (!hasEventWord) {
            sb.append(" (活動 OR 市集 OR 展覽)");
        }

        if (!lower.contains("台灣") && !lower.contains("taiwan")) {
            sb.append(" 台灣");
        }
        
        return sb.toString();
    }

    // ================= 以下為輔助方法 (保持不變) =================

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
            WebCrawler.CrawlResult result = WebCrawler.crawl(page.getUrl());
            if (!result.isSuccess()) return;

            page.setCrawled(true);
            String crawledTitle = result.getTitle();
            String content = result.getTextContent();

            if (crawledTitle != null && crawledTitle.length() > (page.getTitle() != null ? page.getTitle().length() : 0)) {
                page.setTitle(crawledTitle);
            }
            if (content != null) page.setTextContent(content);

            if (page.getEventDate() == null) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") + (content != null ? content : "");
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) page.setEventDate(d);
            }

            if (page.getCity() == null || "全台".equals(page.getCity())) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") + content;
                String city = extractCity(combined);
                if (city != null) page.setCity(city);
            }
            
            // 減少子連結爬取數量
            List<String> links = result.getLinks();
            if (links != null) {
                int subCount = Math.min(links.size(), 4); // 從 6 降到 4
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
        // 簡易解析邏輯，實際專案建議呼叫 EventInfoExtractor
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