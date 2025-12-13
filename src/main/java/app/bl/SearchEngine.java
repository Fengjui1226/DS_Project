package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final int MAX_DEEP_CRAWL_COUNT = 20; // 只爬前 12 筆，加速回應
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
        
        // 7.5 ★ 子網頁提升：如果子網頁更相關，直接把它提升為主結果
        pages = promoteRelevantSubPages(pages, queryTokens, today);

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
     * Query Refinement v8.0：智慧擴展版
     * - 如果只有地點沒有活動類型，自動加上活動類型
     * - ★ 新增：年份自動擴展 (2025 -> 2025 OR 2026)
     */
    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;
        
        String lower = q.toLowerCase();
        StringBuilder sb = new StringBuilder(q);
        
        // ★ 檢查是否有活動類型關鍵字
        boolean hasEventType = EVENT_TERMS.stream()
            .anyMatch(term -> lower.contains(term.toLowerCase()));
        
        // ★ 檢查是否只是地點查詢（常見地點名稱）
        Set<String> locationKeywords = Set.of(
            "信義", "大安", "中山", "松山", "內湖", "士林", "北投", "萬華", "中正", "大同",
            "板橋", "新店", "中和", "永和", "三重", "淡水", "西門", "東區", "天母",
            "台北", "新北", "台中", "高雄", "台南", "桃園",
            "華山", "松菸", "駁二", "草悟道", "逢甲", "一中"
        );
        
        boolean isLocationOnly = locationKeywords.stream()
            .anyMatch(loc -> lower.contains(loc)) && !hasEventType;
        
        // ★ 如果只有地點，加上活動類型擴展
        if (isLocationOnly) {
            sb.append(" (市集 OR 展覽 OR 演唱會 OR 音樂節 OR 活動 OR 演出 OR 表演)");
            System.out.println("[Query] 偵測到純地點搜尋，自動擴展活動類型");
        }
        
        // ★ 年份擴展邏輯：確保涵蓋跨年/明年活動
        // 例如：現在是 2025，自動擴展為 (2025 OR 2026)
        int currentYear = LocalDate.now().getYear();
        int nextYear = currentYear + 1;
        String curYearStr = String.valueOf(currentYear);
        String yearClause = "(" + currentYear + " OR " + nextYear + ")";
        
        // 情況 1: 查詢中已經包含今年 (例如 "2025")，將其擴展
        if (sb.toString().contains(curYearStr)) {
            // 用 replace 將 "2025" 換成 "(2025 OR 2026)"
            // 注意：這裡簡單替換，避免正則表達式太複雜
            String updated = sb.toString().replace(curYearStr, yearClause);
            sb = new StringBuilder(updated);
            System.out.println("[Query] 擴展現有年份: " + yearClause);
        } 
        // 情況 2: 查詢中完全沒提到年份 (且不是找舊資料)，則自動補上
        else {
            // 檢查是否包含其他 202x 年份 (避免干擾使用者查 2024 的舊資料)
            boolean hasOtherYear = sb.toString().matches(".*202[0-4].*");
            if (!hasOtherYear) {
                sb.append(" ").append(yearClause);
                System.out.println("[Query] 自動補上年份限制: " + yearClause);
            }
        }
        
        // ★ 移除：不再強制加上「台灣」，避免與外國地點查詢衝突
        
        return sb.toString();
    }

    // ================= 輔助方法 =================

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        
        // 先按空格分割
        for (String t : query.split("\\s+")) {
            if (!t.isEmpty() && !t.startsWith("-")) {
                tokens.add(t);
                
                // ★ 智慧拆解複合詞
                // 假日市集 → 假日, 市集
                // 聖誕市集 → 聖誕, 市集
                // 創意市集 → 創意, 市集
                // 演唱會 → 演唱, 會 (不拆)
                String[] suffixes = {"市集", "展覽", "活動", "節", "祭", "日"};
                for (String suffix : suffixes) {
                    if (t.endsWith(suffix) && t.length() > suffix.length()) {
                        String prefix = t.substring(0, t.length() - suffix.length());
                        if (prefix.length() >= 2) {  // 前綴至少2個字
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
        return extractCity(query);
    }

    private static boolean shouldExclude(String title, String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        
        // 1. 檢查網域黑名單 (如 amazon, twitter)
        for (String domain : EXCLUDED_DOMAINS) {
            if (lowerUrl.contains(domain)) return true;
        }

        // 2. ★ 新增：過濾機票/旅遊比價/住宿廣告 (針對 EventFinder 的優化)
        if (title != null) {
            String t = title.toLowerCase();
            
            // 機票/航班過濾
            // 邏輯：如果標題包含「機票」、「航班」、「飛往」、「cheap flights」則排除
            // 例外：如果標題包含「航空展」、「熱氣球節」等活動字眼，則放行
            boolean hasFlightKeywords = t.contains("機票") || t.contains("航班") || t.contains("飛往") || 
                                        t.contains("cheap flights") || t.contains("airfare");
            
            boolean isAirline = t.contains("航空") || t.contains("airline");
            
            boolean isEvent = t.contains("展") || t.contains("節") || t.contains("祭") || t.contains("活動");

            if (hasFlightKeywords || (isAirline && !isEvent)) {
                return true;
            }
            
            // 訂房/比價過濾 (Agoda, Booking, TripAdvisor 等通常會產出這類標題)
            if (t.contains("特價優惠") && t.contains("預訂")) return true;
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
            String domain = page.getDomain();
            
            // ★ 社群平台策略：不深度爬取，直接用 Google snippet
            if (isSocialDomain(domain)) {
                page.setCrawled(true);  // 標記為已處理
                // snippet 已在 createPageNode 時設為 textContent
                // 只需要嘗試從 snippet 提取更多資訊
                String content = page.getTextContent();
                if (content != null && !content.isEmpty()) {
                    if (page.getEventDate() == null) {
                        LocalDate d = extractDateFromContent(content, today);
                        if (d != null) page.setEventDate(d);
                    }
                    if (page.getCity() == null) {
                        String city = extractCity(content);
                        if (city != null) page.setCity(city);
                    }
                }
                return;  // 不進行網路爬取
            }
            
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
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) page.setEventDate(d);
            }

            if (page.getCity() == null || "全台".equals(page.getCity())) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") + content;
                String city = extractCity(combined);
                if (city != null) page.setCity(city);
            }
            
            // ★ 新增：處理活動列表頁的項目
            List<WebCrawler.EventItem> eventItems = result.getEventItems();
            if (eventItems != null && !eventItems.isEmpty()) {
                // 把活動項目轉為子網頁（會在後續被提升）
                for (WebCrawler.EventItem item : eventItems) {
                    if (isRelevantToQuery(item.title, queryTokens)) {
                        SubPageNode sub = new SubPageNode(
                            item.url, 
                            item.title, 
                            item.date + " " + item.snippet, 
                            page.getUrl()
                        );
                        sub.setScore(5.0);  // 給予較高的初始分數
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
    
    /**
     * 判斷是否為社群平台（使用 snippet 策略，不深度爬取）
     */
    private static boolean isSocialDomain(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        return d.contains("instagram.com") || d.contains("facebook.com") || 
               d.contains("threads.net") || d.contains("fb.com") ||
               d.contains("fb.watch");
    }
    
    /**
     * 判斷活動項目是否與查詢相關
     */
    private static boolean isRelevantToQuery(String title, List<String> queryTokens) {
        if (title == null || title.isEmpty() || queryTokens == null || queryTokens.isEmpty()) {
            return false;
        }
        String lower = title.toLowerCase();
        
        // 至少匹配一個查詢關鍵字
        for (String token : queryTokens) {
            if (lower.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * ★ 子網頁提升邏輯
     * 如果子網頁標題/內容更符合查詢，把它提升為獨立結果
     */
    private static List<PageNode> promoteRelevantSubPages(List<PageNode> pages, 
                                                          List<String> queryTokens,
                                                          LocalDate today) {
        List<PageNode> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        
        for (PageNode parent : pages) {
            List<SubPageNode> subPages = parent.getSubPages();
            List<PageNode> promotedSubs = new ArrayList<>();
            
            // ★ 找出所有相關的子網頁（不只最相關的一個）
            for (SubPageNode sub : subPages) {
                double relevance = calculateSubPageRelevance(sub, queryTokens);
                
                // 條件放寬：只要有任何關鍵字匹配就考慮提升
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
            
            // ★ 決定要保留父網頁還是子網頁
            if (!promotedSubs.isEmpty()) {
                // 有子網頁被提升，加入子網頁
                result.addAll(promotedSubs);
                
                // 如果父網頁本身也有相關內容，也保留
                if (isParentRelevant(parent, queryTokens)) {
                    if (!seenUrls.contains(parent.getUrl())) {
                        result.add(parent);
                        seenUrls.add(parent.getUrl());
                    }
                }
            } else {
                // 沒有子網頁被提升，保留父網頁
                if (!seenUrls.contains(parent.getUrl())) {
                    result.add(parent);
                    seenUrls.add(parent.getUrl());
                }
            }
        }
        
        return result;
    }
    
    /**
     * 判斷父網頁本身是否相關（不只是靠子網頁）
     */
    private static boolean isParentRelevant(PageNode parent, List<String> queryTokens) {
        String title = parent.getTitle() != null ? parent.getTitle().toLowerCase() : "";
        int matchCount = 0;
        for (String token : queryTokens) {
            if (title.contains(token.toLowerCase())) {
                matchCount++;
            }
        }
        // 至少匹配一半的關鍵字
        return matchCount >= Math.max(1, queryTokens.size() / 2);
    }
    
    /**
     * 計算子網頁與查詢的相關度
     */
    private static double calculateSubPageRelevance(SubPageNode sub, List<String> queryTokens) {
        if (sub == null || queryTokens == null) return 0;
        
        String title = sub.getTitle() != null ? sub.getTitle().toLowerCase() : "";
        String content = sub.getTextContent() != null ? sub.getTextContent().toLowerCase() : "";
        String combined = title + " " + content;
        
        double score = 0;
        int matchCount = 0;
        
        for (String token : queryTokens) {
            String t = token.toLowerCase();
            if (title.contains(t)) {
                score += 3.0;  // 標題匹配權重高
                matchCount++;
            } else if (content.contains(t)) {
                score += 1.0;  // 內容匹配
                matchCount++;
            }
        }
        
        // 如果所有關鍵字都匹配，額外加分
        if (matchCount == queryTokens.size() && queryTokens.size() > 1) {
            score += 2.0;
        }
        
        // URL 包含關鍵字也加分
        String url = sub.getUrl() != null ? sub.getUrl().toLowerCase() : "";
        for (String token : queryTokens) {
            if (url.contains(token.toLowerCase())) {
                score += 1.0;
            }
        }
        
        return score;
    }
    
    /**
     * 檢查標題是否包含查詢關鍵字
     */
    private static boolean containsQueryTerms(String title, List<String> queryTokens) {
        if (title == null || queryTokens == null || queryTokens.isEmpty()) return false;
        String lower = title.toLowerCase();
        
        int matchCount = 0;
        for (String token : queryTokens) {
            if (lower.contains(token.toLowerCase())) {
                matchCount++;
            }
        }
        
        // 至少匹配一半的關鍵字
        return matchCount >= Math.max(1, queryTokens.size() / 2);
    }
    
    /**
     * 將 SubPageNode 轉換為 PageNode
     */
    private static PageNode convertSubToPage(SubPageNode sub, PageNode parent, 
                                             List<String> queryTokens, LocalDate today) {
        if (sub == null) return null;
        
        // 從子網頁標題/內容提取日期
        String combined = sub.getTitle() + " " + sub.getTextContent();
        LocalDate eventDate = parseDate(combined, today);
        
        // 提取城市
        String city = extractCity(combined);
        if (city == null) city = parent.getCity();
        
        // 建立新的 PageNode
        PageNode promoted = PageNode.of(
            sub.getUrl(),
            sub.getTitle(),
            new HashMap<>(),
            eventDate,
            city,
            sub.getDomain(),
            new ArrayList<>(queryTokens),
            sub.getTextContent()
        );
        
        promoted.setCrawled(true);
        return promoted;
    }

    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        return parseDate(title, today);
    }
    
    private static LocalDate extractDateFromContent(String text, LocalDate today) {
        return parseDate(text, today);
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null || text.isEmpty()) return null;
        int currentYear = today.getYear();
        
        // ★ Step 0: 全局年份偵測 (Context Year)
        // 如果標題或開頭就有明確年份（例如 "2024 聖誕市集"），將其視為這段文字的「預設年份」
        // 這樣後續抓到沒有年份的日期 (e.g. 12/19) 時，就不會錯誤預設為今年 (2025)
        Integer contextYear = null;
        Pattern dominantYearP = Pattern.compile("(?i)(?:^|[\\s【\\[(#])(20[2-9]\\d)(?:$|[\\s】\\])年]|全台|精選|最新|活動|整理|市集|展覽|大賞|節|懶人包)");
        Matcher dym = dominantYearP.matcher(text);
        if (dym.find()) {
            contextYear = Integer.parseInt(dym.group(1));
        }
        
        // 0.3 ★ 民國年快速判斷（如「108年度」「111年」）
        int currentRocYear = currentYear - 1911;  // 2025 = 114年
        Pattern rocYearPattern = Pattern.compile("(1[0-9][0-9])年(?:度)?(?:原|市集|活動|展覽|好市)");
        Matcher rocMatcher = rocYearPattern.matcher(text);
        if (rocMatcher.find()) {
            int rocYear = Integer.parseInt(rocMatcher.group(1));
            if (rocYear < currentRocYear - 1) {  // 兩年前的民國年
                int adYear = rocYear + 1911;
                return LocalDate.of(adYear, 12, 31); // 標記為該年底（過期）
            }
        }
        
        // 0.5 相對日期（今天、明天、這週末等）
        LocalDate relativeDate = parseRelativeDate(text, today);
        if (relativeDate != null) {
            return relativeDate;
        }
        
        List<LocalDate> foundDates = new ArrayList<>();
        
        // ★ 1. 優先處理民國年格式（如「111年10月2日」「111年國慶」）
        Pattern pRocFull = Pattern.compile("(1[0-9][0-9])年(?:(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日?)?");
        Matcher mRocFull = pRocFull.matcher(text);
        boolean hasRocYear = false;
        int rocAdYear = 0;
        
        while (mRocFull.find()) {
            hasRocYear = true;
            int rocYear = Integer.parseInt(mRocFull.group(1));
            rocAdYear = rocYear + 1911;
            
            if (mRocFull.group(2) != null && mRocFull.group(3) != null) {
                // 有完整日期
                try {
                    int m = Integer.parseInt(mRocFull.group(2));
                    int d = Integer.parseInt(mRocFull.group(3));
                    LocalDate date = LocalDate.of(rocAdYear, m, d);
                    foundDates.add(date);
                } catch (Exception ignored) {}
            } else {
                // 只有年份
                foundDates.add(LocalDate.of(rocAdYear, 12, 31));
            }
        }
        
        // ★ 如果有民國年且是舊年份，直接回傳（用於過期判斷）
        if (hasRocYear && rocAdYear < currentYear) {
            return foundDates.isEmpty() ? LocalDate.of(rocAdYear, 12, 31) : foundDates.get(0);
        }
        
        // 2. 完整西元日期格式 yyyy/MM/dd 或 yyyy-MM-dd 或 yyyy年M月d日
        // ★ 修改：支援 2020-2099，支援 . 分隔
        Pattern p1 = Pattern.compile("(20[2-9]\\d)[/.\\-年](0?[1-9]|1[0-2])[/.\\-月](0?[1-9]|[12]\\d|3[01])");
        Matcher m1 = p1.matcher(text);
        
        while (m1.find()) {
            try {
                int y = Integer.parseInt(m1.group(1));
                int m = Integer.parseInt(m1.group(2));
                int d = Integer.parseInt(m1.group(3));
                LocalDate date = LocalDate.of(y, m, d);
                foundDates.add(date);
            } catch (Exception ignored) {}
        }
        
        // 3. 年份...月日 格式（如「2025台北花伴野餐3月15日」）
        // ★ 修改：允許年份跟關鍵字中間有空格、Hashtag (例如 "2025 #市集")
        Pattern yearPattern = Pattern.compile("(20[2-9]\\d)(?:年|\\s|#)*(?:台|活動|花|聖誕|跨年|春節|演唱|展覽|市集|節|祭)");
        Matcher yearMatcher = yearPattern.matcher(text);
        while (yearMatcher.find()) {
            int foundYear = Integer.parseInt(yearMatcher.group(1));
            String afterYear = text.substring(yearMatcher.end());
            Pattern monthDayPattern = Pattern.compile("^.{0,30}?(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日?");
            Matcher mdMatcher = monthDayPattern.matcher(afterYear);
            if (mdMatcher.find()) {
                try {
                    int m = Integer.parseInt(mdMatcher.group(1));
                    int d = Integer.parseInt(mdMatcher.group(2));
                    LocalDate date = LocalDate.of(foundYear, m, d);
                    foundDates.add(date);
                } catch (Exception ignored) {}
            }
        }
        
        // ★ 4. 檢查「張貼日期」「更新日期」等
        Pattern postDatePattern = Pattern.compile("(?:張貼日期|發布日期|更新日期|發佈)[：:]?\\s*(20[1-2]\\d)[/.\\-](0?[1-9]|1[0-2])[/.\\-](0?[1-9]|[12]\\d|3[01])");
        Matcher postMatcher = postDatePattern.matcher(text);
        if (postMatcher.find()) {
            int postYear = Integer.parseInt(postMatcher.group(1));
            if (postYear < currentYear - 1) {
                // 文章是兩年前的，標記為過期
                try {
                    int m = Integer.parseInt(postMatcher.group(2));
                    int d = Integer.parseInt(postMatcher.group(3));
                    return LocalDate.of(postYear, m, d);
                } catch (Exception ignored) {}
            }
        }
        
        // 5. 無年份格式 M月d日 或 M/d (★ 大幅優化版)
        if (foundDates.isEmpty()) {
            // ★ 修改：移除對 "." 的支援 (例如 12.15)，避免誤判小數點
            // 只支援 "/" (12/15) 和 "月" (12月15日)
            Pattern p2 = Pattern.compile("(?<!\\d)(0?[1-9]|1[0-2])[/月](0?[1-9]|[12]\\d|3[01])(?:日|\\s|\\)|$)");
            Matcher m2 = p2.matcher(text);
            
            while (m2.find()) {
                try {
                    int m = Integer.parseInt(m2.group(1));
                    int d = Integer.parseInt(m2.group(2));
                    
                    // ★ 關鍵修正：如果前面有抓到 contextYear (例如 2024)，就用它；否則才預設今年
                    int guessYear = (contextYear != null) ? contextYear : currentYear;
                    
                    // ★ 1. 嘗試找周邊是否有年份提示 (支援空格與 hashtag)
                    Pattern eventYear = Pattern.compile("(20[2-9]\\d)(?:年|\\s|#)*(?:活動|市集|展覽|演唱|音樂|聖誕|跨年|春節|節|祭)");
                    Matcher eym = eventYear.matcher(text);
                    if (eym.find()) {
                        guessYear = Integer.parseInt(eym.group(1));
                    }
                    
                    // ★ 2. 智慧推測年份 (已解除封印：找不到年份就預設 currentYear / contextYear)
                    // 這裡不再做任何自動跨年推測，避免誤判
                    LocalDate date = LocalDate.of(guessYear, m, d);
                    
                    foundDates.add(date);
                } catch (Exception ignored) {}
            }
        }
        
        if (foundDates.isEmpty()) {
            // 4. 特殊關鍵字
            if (text.contains("即日起") || text.contains("常設展") || text.contains("長期展出")) {
                return today.plusDays(30);
            }
            
            // ★ 過期保底機制 (Context Year Fallback)
            // 如果沒抓到具體日期，但標題明確是舊年份 (如 "2024 全台聖誕")
            // 直接視為該年年底，讓系統判定過期
            if (contextYear != null && contextYear < currentYear) {
                return LocalDate.of(contextYear, 12, 31);
            }
            
            return null;
        }
        
        // 優先回傳未來日期中最近的
        List<LocalDate> futureDates = foundDates.stream()
            .filter(d -> !d.isBefore(today))
            .sorted()
            .collect(Collectors.toList());
        
        if (!futureDates.isEmpty()) {
            return futureDates.get(0);
        }

        // ★ 6. 未來年份救援機制 (Rescue)
        // 如果上面只找到過去的日期 (例如只抓到更新日期 2025-12-02)，但標題/內容其實有提到明年 (2026)
        // 則應該視為明年活動，而不是過期活動
        if (futureDates.isEmpty()) {
            // 搜尋所有 202x 的年份
            Pattern futureYearP = Pattern.compile("(?<!\\d)(20[2-9]\\d)(?!\\d)");
            Matcher fym = futureYearP.matcher(text);
            int maxFutureYear = -1;
            while (fym.find()) {
                try {
                    int y = Integer.parseInt(fym.group(1));
                    // 只要找到比今年大的年份 (例如現在 2025，找到 2026)
                    if (y > currentYear) {
                        maxFutureYear = Math.max(maxFutureYear, y);
                    }
                } catch (Exception ignored) {}
            }
            
            if (maxFutureYear > currentYear) {
                // 回傳該年 1/1，確保它被視為未來活動 (復活成功！)
                return LocalDate.of(maxFutureYear, 1, 1);
            }
        }
        
        // 都是過去日期，回傳最近的（用於判斷過期）
        return foundDates.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 解析相對日期（今天、明天、週末、星期X等）
     */
    private static LocalDate parseRelativeDate(String text, LocalDate today) {
        if (text == null) return null;
        
        // 今天、明天、後天
        if (text.contains("今天") || text.contains("今日")) {
            return today;
        }
        if (text.contains("明天") || text.contains("明日")) {
            return today.plusDays(1);
        }
        if (text.contains("後天")) {
            return today.plusDays(2);
        }
        
        // 這週末、本週末
        if (text.contains("這週末") || text.contains("本週末") || text.contains("這個週末") || text.contains("本周末")) {
            // 找到這週的星期六
            int daysUntilSat = (java.time.DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            if (daysUntilSat == 0 && today.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
                daysUntilSat = 7;
            }
            return today.plusDays(daysUntilSat);
        }
        
        // 下週末
        if (text.contains("下週末") || text.contains("下個週末") || text.contains("下周末")) {
            int daysUntilSat = (java.time.DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            return today.plusDays(daysUntilSat + 7);
        }
        
        // 星期X / 週X / 禮拜X
        String[] weekdayPatterns = {
            "週一|周一|星期一|禮拜一",
            "週二|周二|星期二|禮拜二", 
            "週三|周三|星期三|禮拜三",
            "週四|周四|星期四|禮拜四",
            "週五|周五|星期五|禮拜五",
            "週六|周六|星期六|禮拜六",
            "週日|周日|星期日|星期天|禮拜日|禮拜天"
        };
        
        for (int i = 0; i < weekdayPatterns.length; i++) {
            Pattern p = Pattern.compile("(這|本|下)?" + "(" + weekdayPatterns[i] + ")");
            Matcher m = p.matcher(text);
            if (m.find()) {
                int targetDayOfWeek = (i == 6) ? 7 : i + 1; // 1=週一, 7=週日
                String prefix = m.group(1);
                
                int daysToAdd = targetDayOfWeek - today.getDayOfWeek().getValue();
                if (daysToAdd <= 0) {
                    daysToAdd += 7; // 如果已經過了，找下一週的
                }
                
                // 如果有「下」字，再加一週
                if ("下".equals(prefix)) {
                    daysToAdd += 7;
                }
                
                return today.plusDays(daysToAdd);
            }
        }
        
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
        
        // ★ 也從 snippet 提取日期（Google snippet 通常包含活動時間）
        if (eventDate == null && r.snippet != null) {
            eventDate = parseDate(r.snippet, today);
        }
        
        Map<Keyword, Integer> tf = new HashMap<>();
        String city = extractCity(r.title);
        
        // 也從 snippet 提取城市
        if (city == null && r.snippet != null) {
            city = extractCity(r.snippet);
        }
        
        String domain = extractDomain(r.link);
        
        // ★ 關鍵：傳入 snippet 作為初始內容
        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, 
                          new ArrayList<>(queryTokens), r.snippet);
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