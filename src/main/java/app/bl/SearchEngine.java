package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.bl.WebCrawler.CrawlResult;
import app.bl.WebCrawler.SiteResult;
import app.da.GoogleConnector;
import app.da.HTMLHandler;
import app.da.LocationRecognizer;
/**
 * SearchEngine v4.0 - 快速回應版
 * 
 * 核心優化：
 * 1. ★ 快速模式：先回傳結果，背景爬取
 * 2. ★ 並行爬取：同時爬取多個網站
 * 3. ★ 超時控制：整體搜尋最多 8 秒
 * 4. 活動類型擴充字典 (EXPANSION)
 * 5. 動態年份 + 台灣限定
 */
public class SearchEngine {

    private static final int MAX_GOOGLE_RESULTS = 15;           // 減少到 15 個
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 8000;          // ★ 整體搜尋超時 8 秒
    private static final int CRAWL_PARALLEL_LIMIT = 5;          // ★ 同時爬取 5 個網站
    private static final ExecutorService CRAWL_EXECUTOR = 
        Executors.newFixedThreadPool(CRAWL_PARALLEL_LIMIT);     // 共享執行緒池

    // ============ 活動相關關鍵字 ============
    private static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "festival", "concert", "exhibition", "event",
        "表演", "藝術", "體驗", "親子", "戶外", "講座"
    );
    // 活動類別擴充，例如「市集」→「文創市集、手作市集…」
    private static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.ofEntries(
        Map.entry("市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "週末市集")),
        Map.entry("展覽", List.of("藝術展", "美術展", "攝影展", "設計展")),
        Map.entry("音樂", List.of("音樂會", "音樂節", "樂團演出", "live house")),
        Map.entry("演唱會", List.of("演唱會", "音樂會", "巡迴演唱會")),
        Map.entry("市集 festival", List.of("market", "festival", "fair"))
    );

    // ★ 新增：活動類型擴充字典
    private static final Map<String, List<String>> EXPANSION = Map.of(
        "市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "創意市集", "跳蚤市集"),
        "展覽", List.of("特展", "藝術展", "美術館展覽", "主題展覽", "攝影展", "設計展"),
        "音樂", List.of("音樂祭", "音樂會", "演唱會", "live演出", "音樂節"),
        "親子", List.of("親子活動", "家庭活動", "兒童活動", "親子市集"),
        "運動", List.of("路跑", "馬拉松", "健走", "運動賽事", "自行車")
    );

    // 語義關聯關鍵字
    private static final Map<String, List<String>> SEMANTIC_GROUPS = Map.ofEntries(
        Map.entry("市集", List.of(
            "市集", "創意市集", "文創市集", "假日市集", "週末市集",
            "手作市集", "跳蚤市場", "攤位", "攤商", "市集活動"
        )),
        Map.entry("展覽", List.of(
            "展覽", "特展", "展出", "作品展", "聯展",
            "藝術展", "art exhibition", "exhibition"
        )),
        Map.entry("音樂", List.of(
            "音樂", "音樂節", "音樂會", "演唱會", "live house",
            "concert", "live concert"
        )),
        Map.entry("親子", List.of(
            "親子活動", "親子", "親子同樂", "家庭活動", "小朋友",
            "親子市集"
        )),
        Map.entry("戶外", List.of(
            "戶外", "戶外活動", "露營", "野餐", "健行", "登山",
            "outdoor", "hiking", "camping"
        )),
        Map.entry("運動", List.of(
            "運動", "運動賽事", "馬拉松", "路跑", "跑步",
            "籃球賽", "足球賽", "比賽", "競賽"
        ))
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

    // 排除的網域
    private static final Set<String> EXCLUDED_DOMAINS = Set.of(
        "x.com", "twitter.com", "ptt.cc"
    );

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    /**
     * ★ 主要搜尋入口 - v4.0 快速回應版
     * 
     * 策略：
     * 1. 先建立 PageNode（不爬取）→ 快速回應
     * 2. 並行爬取網頁，有時間限制
     * 3. 整體搜尋控制在 8 秒內
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v4.0 (快速回應版)        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        
        // ★ 保存原始查詢
        String originalQuery = query;

        // 1. 確保城市有被放進查詢字串
        String userCity = (user != null) ? user.getUserCity() : null;
        if (userCity != null && !userCity.isEmpty()) {
            String queryLower = query.toLowerCase();
            boolean hasCity = CITY_ALIASES.keySet().stream()
                    .anyMatch(alias -> queryLower.contains(alias.toLowerCase()));
            if (!hasCity) {
                query = userCity + " " + query;
            }
        }

        // 2. 查詢擴展
        query = expandQuery(query);

        // 3. 優化送給 Google 的查詢
        String refinedQuery = refineQuery(query);
        System.out.println("[Refined] " + refinedQuery);

        // 4. 呼叫 Google 搜尋（這是最快的部分）
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS);
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        // 5. 解析查詢 tokens
        List<String> queryTokens = parseQueryTokens(query);
        System.out.println("[Tokens] " + queryTokens);

        // 6. ★ 快速建立所有 PageNode（不爬取）
        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();

        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;

            PageNode page = createPageNode(r, query, queryTokens, userCity, today);
            if (page != null) {
                pages.add(page);
            }
        }
        System.out.println("[Pages] 建立 " + pages.size() + " 個頁面節點");

        // 7. ★ 並行爬取（有時間限制）
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = SEARCH_TIMEOUT_MS - elapsed - 1000; // 預留 1 秒做 ranking
            
            if (remainingTime > 2000) { // 至少要 2 秒才爬取
                System.out.println("\n[Step 3] 並行爬取網頁 (限時 " + remainingTime + "ms)...");
                parallelCrawl(pages, queryTokens, remainingTime, today);
            } else {
                System.out.println("\n[Step 3] 跳過爬取（時間不足）");
            }
        }

        // 8. 過濾過期活動
        List<PageNode> validPages = new ArrayList<>();
        List<PageNode> expiredPages = new ArrayList<>();

        for (PageNode p : pages) {
            LocalDate d = p.getEventDate();
            if (d != null && d.isBefore(today)) {
                expiredPages.add(p);
            } else {
                validPages.add(p);
            }
        }

        List<PageNode> pagesToRank = validPages.isEmpty() ? pages : validPages;

        System.out.printf("[Filter] 未過期: %d, 已過期: %d%n",
                pagesToRank.size(), expiredPages.size());

        // 9. 計算 ranking 分數
        System.out.println("\n[Step 4] 計算分數...");
        RankCalculator.rank(pagesToRank, user);

        // 10. 建立樹狀結構
        Tree tree = new Tree();
        tree.addPages(pagesToRank);
        lastSearchTree = tree;
        lastResults = pagesToRank;

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n========== 搜尋完成 ==========");
        System.out.println("[Time] " + duration + " ms");
        System.out.println("[Results] " + pagesToRank.size() + " 個網站");

        printResultsSummary(pagesToRank);

        return pagesToRank;
    }

    /**
     * ★ 並行爬取多個網站（核心優化）
     */
    private static void parallelCrawl(List<PageNode> pages, List<String> queryTokens, 
                                       long timeoutMs, LocalDate today) {
        // 建立爬取任務
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (PageNode page : pages) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    crawlPageFast(page, queryTokens, today);
                } catch (Exception e) {
                    System.out.println("[Crawl Error] " + page.getDomain() + ": " + e.getMessage());
                }
            }, CRAWL_EXECUTOR);
            futures.add(future);
        }
        
        // 等待所有任務完成（有時間限制）
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            allOf.get(timeoutMs, TimeUnit.MILLISECONDS);
            System.out.println("[Crawl] 全部完成");
        } catch (TimeoutException e) {
            System.out.println("[Crawl] 部分超時，繼續處理已完成的結果");
            // 取消未完成的任務
            for (CompletableFuture<Void> f : futures) {
                f.cancel(true);
            }
        } catch (Exception e) {
            System.out.println("[Crawl] 錯誤: " + e.getMessage());
        }
        
        // 統計爬取成功數
        long crawled = pages.stream().filter(PageNode::isCrawled).count();
        System.out.println("[Crawl] 成功爬取 " + crawled + "/" + pages.size() + " 個網站");
    }

    /**
     * ★ 快速爬取單一網頁（只爬主頁，不爬子頁）
     */
    private static void crawlPageFast(PageNode page, List<String> queryTokens, LocalDate today) {
        try {
            // 只爬取主頁（不爬子網頁，節省時間）
            WebCrawler.CrawlResult result = WebCrawler.crawl(page.getUrl());
            
            if (result.isSuccess()) {
                page.setCrawled(true);
                
                // 更新標題（如果爬到更好的）
                String crawledTitle = result.getTitle();
                if (crawledTitle != null && !crawledTitle.isEmpty() 
                    && crawledTitle.length() > page.getTitle().length()) {
                    page.setTitle(crawledTitle);
                }
                
                // 從內文提取日期
                if (page.getEventDate() == null) {
                    LocalDate contentDate = extractDateFromContent(result.getTextContent(), today);
                    if (contentDate != null) {
                        page.setEventDate(contentDate);
                    }
                }
                
                // 計算關鍵字匹配分數
                String content = result.getTextContent();
                if (content != null && !content.isEmpty()) {
                    int matchCount = 0;
                    String contentLower = content.toLowerCase();
                    for (String token : queryTokens) {
                        if (contentLower.contains(token.toLowerCase())) {
                            matchCount++;
                        }
                    }
                    // 加分：每匹配一個關鍵字加 5 分
                    page.addScore(matchCount * 5);
                }
                
                // 提取子網頁連結（但不爬取）
                List<String> links = result.getLinks();
                if (links != null) {
                    int subCount = Math.min(links.size(), 10);
                    for (int i = 0; i < subCount; i++) {
                        String link = links.get(i);
                        SubPageNode sub = new SubPageNode(link, extractTitleFromUrl(link), 0);
                        page.addSubPage(sub);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略爬取錯誤
        }
    }
    
    private static String extractTitleFromUrl(String url) {
        if (url == null) return "";
        try {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < url.length() - 1) {
                String segment = url.substring(lastSlash + 1);
                // 移除副檔名和參數
                int dot = segment.lastIndexOf('.');
                if (dot > 0) segment = segment.substring(0, dot);
                int q = segment.indexOf('?');
                if (q > 0) segment = segment.substring(0, q);
                return segment.replace("-", " ").replace("_", " ");
            }
        } catch (Exception e) {}
        return "";
    }

    /**
     * ★ 新增：查詢擴展方法
     * 根據 EXPANSION 字典自動加入相關關鍵字
     */
    private static String expandQuery(String q) {
        StringBuilder result = new StringBuilder(q);
        String lower = q.toLowerCase();
        
        for (Map.Entry<String, List<String>> e : EXPANSION.entrySet()) {
            if (lower.contains(e.getKey())) {
                for (String add : e.getValue()) {
                    if (!lower.contains(add.toLowerCase())) {
                        result.append(" ").append(add);
                    }
                }
            }
        }
        
        return result.toString();
    }

    /**
     * 建立 PageNode
     */
    private static PageNode createPageNode(GoogleConnector.Result r, String query,
            List<String> queryTokens, String userCity, LocalDate today) {

        if (shouldExclude(r.title, r.link)) return null;

        // 提取日期
        LocalDate eventDate = extractDateFromTitle(r.title, today);

        // 計算詞頻
        Map<Keyword, Integer> tf = new HashMap<>();
        for (String token : queryTokens) {
            Keyword k = Keyword.of(token);
            tf.put(k, tf.getOrDefault(k, 0) + 1);
        }

        // 活動關鍵字加分
        String titleLower = r.title.toLowerCase();
        for (String term : EVENT_TERMS) {
            if (titleLower.contains(term.toLowerCase())) {
                Keyword k = Keyword.of(term);
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
        }

        // 城市偵測
        String city = LocationRecognizer.extractCity(r.title);
        if (city == null || city.isEmpty()) {
            city = detectCityFromQuery(query);
        }
        if (city == null || city.isEmpty()) {
            city = (userCity != null) ? userCity : "";
        }

        String domain = extractDomain(r.link);
        List<String> tokensCopy = new ArrayList<>(queryTokens);

        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, tokensCopy);
    }

    /**
     * 優化查詢字串
     */
    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase();

        boolean hasCity = CITY_ALIASES.keySet().stream()
            .anyMatch(alias -> lower.contains(alias.toLowerCase()));

        boolean hasEventWord = EVENT_TERMS.stream()
            .anyMatch(term -> lower.contains(term.toLowerCase()));

        // 如果有城市但沒明講活動類型，幫忙加一點活動相關字
        if (hasCity && !hasEventWord) {
            q += " 活動 OR 展覽 OR 演唱會 OR 市集";
        }

        // 年份：如果使用者沒自己打 20xx，就加今年 + 明年
        if (!lower.matches(".*20\\d{2}.*")) {
            int year = LocalDate.now().getYear();
            q += " " + year + " OR " + (year + 1);
        }

        // 加上台灣（避免跑去其他國家）
        if (!lower.contains("台灣") && !lower.contains("臺灣")) {
            q += " 台灣";
        }

        // 加一點社群關鍵字（讓 IG / FB 活動有機會浮上來，但是不是用 site: 限制）
        q += " IG instagram FB 粉絲專頁 活動頁";

        // 排除比較常見的「非活動」關鍵字
        q += " -申請辦法 -徵選 -補助 -招生 -履歷 -課程簡章";

        return q;
    }

    /**
     * 解析查詢 tokens（含語義展開）
     */
    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.isEmpty()) return tokens;

        // 先切基本字詞
        for (String raw : query.split("\\s+")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            // 過濾掉 OR / AND / 符號
            if ("OR".equalsIgnoreCase(t) || "AND".equalsIgnoreCase(t)) continue;
            tokens.add(t);
        }

        // 針對活動類別做擴充
        List<String> expanded = new ArrayList<>(tokens);
        for (String t : tokens) {
            String base = t.replaceAll("[\\p{Punct}]", ""); // 去標點
            if (CATEGORY_EXPANSIONS.containsKey(base)) {
                expanded.addAll(CATEGORY_EXPANSIONS.get(base));
            }
        }

        return expanded;
    }
    private static boolean shouldExclude(String title, String url) {
        if (url == null) return false;
        if (title != null && title.contains("Google Custom Search")) return true;

        String lowerUrl = url.toLowerCase();
        for (String domain : EXCLUDED_DOMAINS) {
            if (lowerUrl.contains(domain)) {
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
            } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * ★ 從內文提取日期
     */
    private static LocalDate extractDateFromContent(String text, LocalDate today) {
        if (text == null) return null;

        String content = text.length() > 8000 ? text.substring(0, 8000) : text;

        // 優先找關鍵詞附近
        String[] hints = {"活動日期", "市集時間", "活動時間", "展覽日期", "日期", "時間"};
        for (String hint : hints) {
            int idx = content.indexOf(hint);
            if (idx >= 0) {
                int start = Math.max(0, idx - 10);
                int end = Math.min(content.length(), idx + 50);
                String slice = content.substring(start, end);
                LocalDate d = extractDateFromTitle(slice, today);
                if (d != null) return d;
            }
        }

        // 直接在全文找
        return extractDateFromTitle(content, today);
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
        if (url == null) return "";
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
            String dateStr = p.getEventDate() != null ? p.getEventDate().toString() : "無日期";
            System.out.printf("#%d [%.1f] %s | %s%n",
                rank++, p.getTotalScore(), dateStr, truncate(p.getTitle(), 35));
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
}