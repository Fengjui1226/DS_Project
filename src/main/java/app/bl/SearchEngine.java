package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import app.da.GoogleConnector;
import app.bl.config.CityConfig;
import app.bl.config.FilterConfig;
import app.bl.config.EventTypeConfig;

import static app.bl.TextUtils.*;

/**
 * SearchEngine v10.0 - 重構版
 * 
 * 重構內容：
 * 1. 日期解析邏輯移至 DateParser（減少 200+ 行）
 * 2. 使用 TextUtils 共用工具方法
 * 3. 使用新的 Config 類別
 */
public class SearchEngine {

    // ★ 參數設定
    private static final int MAX_GOOGLE_RESULTS = 20;
    private static final int MAX_DEEP_CRAWL_COUNT = 20;
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 20000;
    private static final int CRAWL_PARALLEL_LIMIT = 15;
    
    // ★ 迭代搜尋設定
    private static final boolean ENABLE_ITERATIVE_SEARCH = true;
    private static final int MAX_ITERATIONS = 2;
    private static final int MIN_RESULTS_FOR_ITERATION = 8;
    
    private static final ExecutorService CRAWL_EXECUTOR =
            Executors.newFixedThreadPool(CRAWL_PARALLEL_LIMIT);

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    /**
     * 搜尋主入口
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v10.0 (Refactored)                   ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        // 1. 解析意圖與城市
        String queryCity = detectCityFromQuery(query);
        String userCity = (user != null) ? user.getUserCity() : null;
        String effectiveCity = isNotEmpty(queryCity) ? queryCity : 
                              (isNotEmpty(userCity) ? userCity : null);

        // 2. 構建 Google 查詢字串
        String googleQuery = query;
        if (queryCity == null && effectiveCity != null) {
            googleQuery = effectiveCity + " " + query;
        }

        // 3. 語意擴展與查詢優化
        QueryUnderstanding.ParsedQuery parsedQuery = QueryUnderstanding.parse(googleQuery);
        googleQuery = parsedQuery.expandedQuery;
        String refinedQuery = refineQuery(googleQuery);
        System.out.println("[Refined] " + refinedQuery);

        // 4. 呼叫 Google API
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
            if (shouldExclude(r.title, r.link)) continue;

            PageNode page = createPageNode(r, query, queryTokens, effectiveCity, today);
            if (page != null) {
                if (isLikelyForeign(page.getTitle())) continue;
                pages.add(page);
            }
        }
        System.out.println("[Pages] 初步保留 " + pages.size() + " 個頁面節點");

        // 6. 平行爬蟲
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = SEARCH_TIMEOUT_MS - elapsed - 1000;
            
            List<PageNode> pagesToCrawl = pages.stream()
                .limit(MAX_DEEP_CRAWL_COUNT)
                .collect(Collectors.toList());

            if (remainingTime > 2000) {
                System.out.println("\n[Step 3] 並行爬取前 " + pagesToCrawl.size() + " 筆網頁...");
                parallelCrawl(pagesToCrawl, queryTokens, remainingTime, today);
            }
        }

        // 7. 進階處理
        System.out.println("\n[Step 4] 進階處理與排名...");
        TFIDFCalculator.applyTFIDFScores(pages, query);
        EventInfoExtractor.applyCompletenessBonus(pages);
        pages = Deduplicator.deduplicate(pages);
        pages = promoteRelevantSubPages(pages, queryTokens, today);

        // 8. 最終排名
        UserProfile effectiveUser = new UserProfile();
        if (effectiveCity != null) effectiveUser.setUserCity(effectiveCity);
        
        RankCalculator.rank(pages, effectiveUser, query);
        
        // 9. 清理無效結果
        int beforeClean = pages.size();
        pages.removeIf(p -> p.getTotalScore() <= 0.01);
        int afterClean = pages.size();
        System.out.printf("[Clean] 移除 %d 個無效結果%n", beforeClean - afterClean);
        
        // 10. 迭代搜尋
        if (ENABLE_ITERATIVE_SEARCH && pages.size() < MIN_RESULTS_FOR_ITERATION) {
            pages = performIterativeSearch(pages, query, queryTokens, effectiveUser, today, 1);
        }

        // 11. 輸出結果
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
     * 迭代搜尋
     */
    private static List<PageNode> performIterativeSearch(
            List<PageNode> currentPages, 
            String originalQuery,
            List<String> originalTokens,
            UserProfile user,
            LocalDate today,
            int iteration) {
        
        if (iteration > MAX_ITERATIONS) return currentPages;
        
        System.out.println("\n🔄 [Iteration " + iteration + "] 結果不足，啟動語意擴展搜尋...");
        
        List<String> derivedKeywords = deriveKeywordsFromResults(currentPages, originalTokens);
        if (derivedKeywords.isEmpty()) {
            System.out.println("[Iteration] 無法提取更多關鍵字，停止迭代");
            return currentPages;
        }
        
        System.out.println("[Iteration] 發現相關關鍵字: " + derivedKeywords);
        
        Set<String> seenUrls = currentPages.stream()
            .map(PageNode::getUrl)
            .collect(Collectors.toSet());
        
        List<PageNode> newPages = new ArrayList<>();
        
        for (String keyword : derivedKeywords) {
            String newQuery = originalQuery + " " + keyword;
            System.out.println("[Iteration] 擴展搜尋: " + newQuery);
            
            try {
                List<GoogleConnector.Result> results = 
                    GoogleConnector.search(refineQuery(newQuery), 10, 2000);
                List<String> newTokens = parseQueryTokens(newQuery);
                
                for (GoogleConnector.Result r : results) {
                    if (r == null || r.link == null) continue;
                    if (seenUrls.contains(r.link)) continue;
                    if (shouldExclude(r.title, r.link)) continue;
                    
                    PageNode page = createPageNode(r, newQuery, newTokens, user.getUserCity(), today);
                    if (page != null && !isLikelyForeign(page.getTitle())) {
                        newPages.add(page);
                        seenUrls.add(r.link);
                    }
                }
            } catch (Exception e) {
                System.out.println("[Iteration] 搜尋失敗: " + e.getMessage());
            }
            
            if (currentPages.size() + newPages.size() >= MIN_RESULTS_FOR_ITERATION * 2) break;
        }
        
        if (newPages.isEmpty()) {
            System.out.println("[Iteration] 未找到新結果");
            return currentPages;
        }
        
        System.out.println("[Iteration] 找到 " + newPages.size() + " 個新結果");
        
        if (ENABLE_CRAWLING && !newPages.isEmpty()) {
            List<String> allTokens = new ArrayList<>(originalTokens);
            derivedKeywords.forEach(k -> { if (!allTokens.contains(k)) allTokens.add(k); });
            parallelCrawl(newPages, allTokens, 5000, today);
        }
        
        List<PageNode> combined = new ArrayList<>(currentPages);
        combined.addAll(newPages);
        
        TFIDFCalculator.applyTFIDFScores(combined, originalQuery);
        combined = Deduplicator.deduplicate(combined);
        RankCalculator.rank(combined, user, originalQuery);
        combined.removeIf(p -> p.getTotalScore() <= 0.01);
        
        System.out.println("[Iteration] 合併後共 " + combined.size() + " 個結果");
        
        if (combined.size() < MIN_RESULTS_FOR_ITERATION && iteration < MAX_ITERATIONS) {
            return performIterativeSearch(combined, originalQuery, originalTokens, user, today, iteration + 1);
        }
        
        return combined;
    }
    
    /**
     * 從搜尋結果中提取相關關鍵字
     */
    private static List<String> deriveKeywordsFromResults(List<PageNode> pages, List<String> originalTokens) {
        Map<String, Integer> keywordFreq = new HashMap<>();
        Set<String> originalSet = new HashSet<>(originalTokens);
        
        Set<String> eventKeywords = Set.of(
            "市集", "展覽", "演唱會", "音樂節", "活動", "演出", "表演",
            "工作坊", "講座", "派對", "路跑", "野餐", "手作", "文創",
            "聖誕", "跨年", "春節", "萬聖節", "燈會", "花火"
        );
        
        Set<String> locationKeywords = Set.of(
            "華山", "松菸", "駁二", "信義", "大安", "中山", "西門",
            "台北", "新北", "台中", "高雄", "台南"
        );
        
        for (PageNode page : pages) {
            String title = page.getTitle() != null ? page.getTitle() : "";
            String content = page.getTextContent() != null ? page.getTextContent() : "";
            String combined = title + " " + content;
            
            for (String kw : eventKeywords) {
                if (combined.contains(kw) && !originalSet.contains(kw)) {
                    keywordFreq.merge(kw, 1, Integer::sum);
                }
            }
            
            for (String loc : locationKeywords) {
                if (combined.contains(loc) && !originalSet.contains(loc)) {
                    keywordFreq.merge(loc, 1, Integer::sum);
                }
            }
        }
        
        return keywordFreq.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Query Refinement
     */
    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;
        
        String lower = q.toLowerCase();
        StringBuilder sb = new StringBuilder(q);
        
        boolean hasEventType = EventTypeConfig.EVENT_TERMS.stream()
            .anyMatch(term -> lower.contains(term.toLowerCase()));
        
        Set<String> locationKeywords = Set.of(
            "信義", "大安", "中山", "松山", "內湖", "士林", "北投", "萬華", "中正", "大同",
            "板橋", "新店", "中和", "永和", "三重", "淡水", "西門", "東區", "天母",
            "台北", "新北", "台中", "高雄", "台南", "桃園",
            "華山", "松菸", "駁二", "草悟道", "逢甲", "一中"
        );
        
        boolean isLocationOnly = locationKeywords.stream()
            .anyMatch(loc -> lower.contains(loc)) && !hasEventType;
        
        if (isLocationOnly) {
            sb.append(" (市集 OR 展覽 OR 演唱會 OR 音樂節 OR 活動 OR 演出 OR 表演)");
            System.out.println("[Query] 偵測到純地點搜尋，自動擴展活動類型");
        }
        
        int currentYear = LocalDate.now().getYear();
        int nextYear = currentYear + 1;
        String curYearStr = String.valueOf(currentYear);
        String yearClause = "(" + currentYear + " OR " + nextYear + ")";
        
        if (sb.toString().contains(curYearStr)) {
            String updated = sb.toString().replace(curYearStr, yearClause);
            sb = new StringBuilder(updated);
            System.out.println("[Query] 擴展現有年份: " + yearClause);
        } else {
            boolean hasOtherYear = sb.toString().matches(".*202[0-4].*");
            if (!hasOtherYear) {
                sb.append(" ").append(yearClause);
                System.out.println("[Query] 自動補上年份限制: " + yearClause);
            }
        }
        
        return sb.toString();
    }

    // ================= 輔助方法 =================

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        
        for (String t : query.split("\\s+")) {
            if (!t.isEmpty() && !t.startsWith("-")) {
                tokens.add(t);
                
                String[] suffixes = {"市集", "展覽", "活動", "節", "祭", "日"};
                for (String suffix : suffixes) {
                    if (t.endsWith(suffix) && t.length() > suffix.length()) {
                        String prefix = t.substring(0, t.length() - suffix.length());
                        if (prefix.length() >= 2) {
                            if (!tokens.contains(prefix)) tokens.add(prefix);
                        }
                        if (!tokens.contains(suffix)) tokens.add(suffix);
                        break;
                    }
                }
            }
        }
        return tokens;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return null;
        return CityConfig.extract(query);
    }

    private static boolean shouldExclude(String title, String url) {
        if (url == null) return false;
        
        // 檢查網域黑名單
        if (FilterConfig.isExcludedDomain(url)) return true;

        // 過濾機票/旅遊比價
        if (title != null) {
            String t = title.toLowerCase();
            
            boolean hasFlightKeywords = t.contains("機票") || t.contains("航班") || 
                t.contains("飛往") || t.contains("cheap flights") || t.contains("airfare");
            boolean isAirline = t.contains("航空") || t.contains("airline");
            boolean isEvent = t.contains("展") || t.contains("節") || t.contains("祭") || t.contains("活動");

            if (hasFlightKeywords || (isAirline && !isEvent)) return true;
            if (t.contains("特價優惠") && t.contains("預訂")) return true;
        }

        return false;
    }
    
    private static boolean isLikelyForeign(String text) {
        if (text == null) return false;
        if (CityConfig.hasTaiwanLocation(text)) return false;
        return FilterConfig.isForeign(text);
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
            String domain = page.getDomain();
            
            // 社群平台策略：不深度爬取
            if (FilterConfig.isSocialDomain(domain)) {
                page.setCrawled(true);
                String content = page.getTextContent();
                if (isNotEmpty(content)) {
                    if (page.getEventDate() == null) {
                        LocalDate d = DateParser.parse(content, today);
                        if (d != null) page.setEventDate(d);
                    }
                    if (page.getCity() == null) {
                        String city = CityConfig.extract(content);
                        if (city != null) page.setCity(city);
                    }
                }
                return;
            }
            
            WebCrawler.CrawlResult result = WebCrawler.crawl(page.getUrl());
            if (!result.isSuccess()) return;

            page.setCrawled(true);
            String crawledTitle = result.getTitle();
            String content = result.getTextContent();

            if (crawledTitle != null && crawledTitle.length() > (page.getTitle() != null ? page.getTitle().length() : 0)) {
                page.setTitle(crawledTitle);
            }
            if (content != null) page.setTextContent(content);

            // ★ 使用 DateParser
            if (page.getEventDate() == null) {
                String combined = combine(crawledTitle, content);
                LocalDate d = DateParser.parse(combined, today);
                if (d != null) page.setEventDate(d);
            }

            if (page.getCity() == null || "全台".equals(page.getCity())) {
                String combined = combine(crawledTitle, content);
                String city = CityConfig.extract(combined);
                if (city != null) page.setCity(city);
            }
            
            // 處理活動列表頁
            List<WebCrawler.EventItem> eventItems = result.getEventItems();
            if (eventItems != null && !eventItems.isEmpty()) {
                for (WebCrawler.EventItem item : eventItems) {
                    if (isRelevantToQuery(item.title, queryTokens)) {
                        SubPageNode sub = new SubPageNode(
                            item.url, item.title, item.date + " " + item.snippet, page.getUrl()
                        );
                        sub.setScore(5.0);
                        page.addSubPage(sub);
                    }
                }
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
    
    private static boolean isRelevantToQuery(String title, List<String> queryTokens) {
        if (isEmpty(title) || queryTokens == null || queryTokens.isEmpty()) return false;
        String lower = title.toLowerCase();
        for (String token : queryTokens) {
            if (lower.contains(token.toLowerCase())) return true;
        }
        return false;
    }
    
    /**
     * 子網頁提升邏輯
     */
    private static List<PageNode> promoteRelevantSubPages(List<PageNode> pages, 
                                                          List<String> queryTokens,
                                                          LocalDate today) {
        List<PageNode> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        
        for (PageNode parent : pages) {
            List<SubPageNode> subPages = parent.getSubPages();
            List<PageNode> promotedSubs = new ArrayList<>();
            
            for (SubPageNode sub : subPages) {
                double relevance = calculateSubPageRelevance(sub, queryTokens);
                
                if (relevance >= 1.0 && !seenUrls.contains(sub.getUrl())) {
                    PageNode promoted = convertSubToPage(sub, parent, queryTokens, today);
                    if (promoted != null) {
                        promoted.setScore(parent.getScore() * 0.5 + relevance * 2);
                        promotedSubs.add(promoted);
                        seenUrls.add(promoted.getUrl());
                        System.out.println("[Promote] 提升子網頁: " + truncate(sub.getTitle(), 40));
                    }
                }
            }
            
            if (!promotedSubs.isEmpty()) {
                result.addAll(promotedSubs);
                if (isParentRelevant(parent, queryTokens)) {
                    if (!seenUrls.contains(parent.getUrl())) {
                        result.add(parent);
                        seenUrls.add(parent.getUrl());
                    }
                }
            } else {
                if (!seenUrls.contains(parent.getUrl())) {
                    result.add(parent);
                    seenUrls.add(parent.getUrl());
                }
            }
        }
        
        return result;
    }
    
    private static boolean isParentRelevant(PageNode parent, List<String> queryTokens) {
        String title = parent.getTitle() != null ? parent.getTitle().toLowerCase() : "";
        int matchCount = 0;
        for (String token : queryTokens) {
            if (title.contains(token.toLowerCase())) matchCount++;
        }
        return matchCount >= Math.max(1, queryTokens.size() / 2);
    }
    
    private static double calculateSubPageRelevance(SubPageNode sub, List<String> queryTokens) {
        if (sub == null || queryTokens == null) return 0;
        
        String title = toLowerCase(sub.getTitle());
        String content = toLowerCase(sub.getTextContent());
        String url = toLowerCase(sub.getUrl());
        
        double score = 0;
        int matchCount = 0;
        
        for (String token : queryTokens) {
            String t = token.toLowerCase();
            if (title.contains(t)) {
                score += 3.0;
                matchCount++;
            } else if (content.contains(t)) {
                score += 1.0;
                matchCount++;
            }
            if (url.contains(t)) score += 1.0;
        }
        
        if (matchCount == queryTokens.size() && queryTokens.size() > 1) score += 2.0;
        
        return score;
    }
    
    private static PageNode convertSubToPage(SubPageNode sub, PageNode parent, 
                                             List<String> queryTokens, LocalDate today) {
        if (sub == null) return null;
        
        String combined = combine(sub.getTitle(), sub.getTextContent());
        
        // ★ 使用 DateParser
        LocalDate eventDate = DateParser.parse(combined, today);
        
        String city = CityConfig.extract(combined);
        if (city == null) city = parent.getCity();
        
        PageNode promoted = PageNode.of(
            sub.getUrl(), sub.getTitle(), new HashMap<>(),
            eventDate, city, sub.getDomain(),
            new ArrayList<>(queryTokens), sub.getTextContent()
        );
        
        promoted.setCrawled(true);
        return promoted;
    }

    private static PageNode createPageNode(GoogleConnector.Result r, String query,
                                           List<String> queryTokens, String effectiveCity,
                                           LocalDate today) {
        if (shouldExclude(r.title, r.link)) return null;
        
        // ★ 使用 DateParser
        LocalDate eventDate = DateParser.parse(r.title, today);
        if (eventDate == null && r.snippet != null) {
            eventDate = DateParser.parse(r.snippet, today);
        }
        
        Map<Keyword, Integer> tf = new HashMap<>();
        String city = CityConfig.extract(r.title);
        if (city == null && r.snippet != null) {
            city = CityConfig.extract(r.snippet);
        }
        
        String domain = extractDomain(r.link);
        
        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, 
                          new ArrayList<>(queryTokens), r.snippet);
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