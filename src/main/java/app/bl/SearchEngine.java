package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import app.da.GoogleConnector;
import app.da.LocationRecognizer;

public class SearchEngine {

    // 🔧 設定：Google 抓 40 筆，但只爬前 15 筆以避免 Timeout
    private static final int MAX_GOOGLE_RESULTS = 40;
    private static final int MAX_DEEP_CRAWL_COUNT = 15; // 新增：限制深度爬蟲數量
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 8000;      // 整體搜尋最多 8 秒
    private static final int CRAWL_PARALLEL_LIMIT = 15;     // 同時爬取數量
    private static final ExecutorService CRAWL_EXECUTOR =
            Executors.newFixedThreadPool(CRAWL_PARALLEL_LIMIT);

    private static final List<String> EVENT_TERMS = List.of(
            "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
            "festival", "concert", "exhibition", "event",
            "表演", "藝術", "體驗", "親子", "戶外", "講座"
    );

    private static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.ofEntries(
            Map.entry("市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "週末市集")),
            Map.entry("展覽", List.of("藝術展", "美術展", "攝影展", "設計展")),
            Map.entry("音樂", List.of("音樂會", "音樂節", "樂團演出", "live house")),
            Map.entry("演唱會", List.of("演唱會", "音樂會", "巡迴演唱會")),
            Map.entry("festival", List.of("market", "festival", "fair"))
    );

    // 🚨 新增：海外關鍵字（用於過濾）
    private static final List<String> FOREIGN_KEYWORDS = List.of(
            // 日本
            "日本", "東京", "大阪", "北海道", "沖繩", "京都", "奈良", "名古屋", "福岡", "橫濱", "涉谷", "新宿",
            // 韓國
            "首爾", "釜山", "韓國", "濟州", "仁川",
            // 港澳 & 中國
            "香港", "澳門", "上海", "北京", "深圳", "廣州",
            // 東南亞 & 歐美
            "曼谷", "泰國", "新加坡", "馬來西亞", "越南", "美國", "歐洲", "倫敦", "巴黎",
            // 旅遊類雜訊
            "機票", "自由行", "入境", "簽證", "匯率", "代購"
    );

    private static final Map<String, List<String>> EXPANSION = Map.of(
            "市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "創意市集", "跳蚤市集"),
            "展覽", List.of("特展", "藝術展", "美術館展覽", "主題展覽", "攝影展", "設計展"),
            "音樂", List.of("音樂祭", "音樂會", "演唱會", "live演出", "音樂節"),
            "親子", List.of("親子活動", "家庭活動", "兒童活動", "親子市集"),
            "運動", List.of("路跑", "馬拉松", "健走", "運動賽事", "自行車")
    );

    /** 城市同義字 → 正規化城市名稱 */
    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
            Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
            Map.entry("新北", "新北"),
            Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
            Map.entry("台南", "台南"), Map.entry("臺南", "台南"), Map.entry("tainan", "台南"),
            Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
            Map.entry("桃園", "桃園"), Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹")
    );

    private static final Set<String> EXCLUDED_DOMAINS = Set.of(
            "x.com", "twitter.com", "ptt.cc",
            "amazon.co.jp", "rakuten.co.jp", "yahoo.co.jp", "booking.com", "agoda.com"
    );

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    // =====================================================
    // 主流程
    // =====================================================

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v5.2 (Foreign Filter Added)          ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        // 1. 若使用者有設定城市，但 query 沒寫城市 → 幫他加上（只是給 Google 用）
        String userCity = (user != null) ? user.getUserCity() : null;
        if (userCity != null && !userCity.isEmpty()) {
            String queryLower = query.toLowerCase();
            boolean hasCity = CITY_ALIASES.keySet().stream()
                    .anyMatch(alias -> queryLower.contains(alias.toLowerCase()));
            if (!hasCity) {
                query = userCity + " " + query;
            }
        }

        // 2. 查詢理解（意圖識別 + 時間解析 + 同義詞擴展）
        QueryUnderstanding.ParsedQuery parsedQuery = QueryUnderstanding.parse(query);
        System.out.println("[QueryUnderstanding] " + parsedQuery.primaryIntent);
        
        // 3. 類別關鍵字擴充
        query = expandQuery(parsedQuery.expandedQuery);

        // 4. 給 Google 用的 query 微調（年份 / 排除「申請辦法」/ 🚨 排除「日本」）
        String refinedQuery = refineQuery(query);
        System.out.println("[Refined] " + refinedQuery);

        // ================== Step 1: Google ==================
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS, 3000); 
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        List<String> queryTokens = parseQueryTokens(query);

        // ================== Step 2: 建立 PageNode ==================
        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            
            // 建立節點
            PageNode page = createPageNode(r, query, queryTokens, userCity, today);
            
            // 🚨 這裡做第一層過濾：如果是明顯的海外標題，直接丟掉
            if (page != null) {
                if (isLikelyForeign(page.getTitle(), "")) {
                    // System.out.println("  ❌ 濾除海外標題: " + page.getTitle());
                    continue; 
                }
                pages.add(page);
            }
        }
        System.out.println("[Pages] 建立 " + pages.size() + " 個頁面節點");

        // ================== Step 3: 並行爬取 ==================
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = SEARCH_TIMEOUT_MS - elapsed - 1000;
            
            // 🚨 優化：只爬前 N 筆，避免 timeout，但邏輯不變
            List<PageNode> pagesToCrawl = pages.stream()
                .limit(MAX_DEEP_CRAWL_COUNT)
                .collect(Collectors.toList());

            if (remainingTime > 2000) {
                System.out.println("\n[Step 3] 並行爬取前 " + pagesToCrawl.size() + " 筆網頁 (限時 " + remainingTime + " ms)...");
                // 這裡使用原本的 parallelCrawl，沒改動裡面邏輯
                parallelCrawl(pagesToCrawl, queryTokens, remainingTime, today);
            } else {
                System.out.println("\n[Step 3] 跳過爬取（時間不足）");
            }
        }

        // ================== Step 3.5: 二次海外過濾 (針對內文) ==================
        // 爬蟲回來後，內容變多了，再檢查一次內文是不是海外文章
        List<PageNode> filteredPages = new ArrayList<>();
        for (PageNode p : pages) {
            String content = (p.getTextContent() != null) ? p.getTextContent() : "";
            // 如果爬完發現是日本/國外文章，丟掉
            if (isLikelyForeign(p.getTitle(), content)) {
                // 但如果有提到台灣城市，則保留 (例如: 日本展in台北)
                if (!hasTaiwanCity(p.getTitle() + " " + content)) {
                    // System.out.println("  ❌ 濾除海外內文: " + p.getTitle());
                    continue; 
                }
            }
            filteredPages.add(p);
        }
        pages = filteredPages;

        // 3.3 依使用者城市 + 內文過濾（保留你原本的邏輯）
        if (userCity != null && !userCity.isEmpty()) {
            pages = filterByUserCityWithContent(pages, userCity);
        }

        // 3.5 依「是否過期」分組
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
        // 如果 validPages 太少，還是把過期的加回來充數，不然結果太難看
        List<PageNode> pagesToRank = validPages.isEmpty() ? pages : validPages;

        System.out.printf("[Filter] 未過期: %d, 已過期: %d%n", validPages.size(), expiredPages.size());

        // ================== Step 4: 進階處理 ==================
        System.out.println("\n[Step 4] 進階處理...");
        
        TFIDFCalculator.applyTFIDFScores(pagesToRank, query);
        EventInfoExtractor.applyCompletenessBonus(pagesToRank);
        pagesToRank = Deduplicator.deduplicate(pagesToRank);

        // ================== Step 5: 排名計算 ==================
        System.out.println("\n[Step 5] 計算最終分數...");
        RankCalculator.rank(pagesToRank, user, query);
        applyUserCityBoost(pagesToRank, user);

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

    // =====================================================
    // 爬蟲相關 (保留原始邏輯)
    // =====================================================

    private static void parallelCrawl(List<PageNode> pages, List<String> queryTokens,
                                      long timeoutMs, LocalDate today) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (PageNode page : pages) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    crawlPageFast(page, queryTokens, today);
                } catch (Exception e) {
                    System.out.println("[Crawl Error] " + page.getUrl() + ": " + e.getMessage());
                }
            }, CRAWL_EXECUTOR);
            futures.add(future);
        }

        try {
            CompletableFuture<Void> allOf =
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.get(timeoutMs, TimeUnit.MILLISECONDS);
            System.out.println("[Crawl] 全部完成 (或部分完成)");
        } catch (TimeoutException e) {
            System.out.println("[Crawl] 時間到，停止等待，已完成的照樣用");
        } catch (Exception e) {
            System.out.println("[Crawl] 錯誤: " + e.getMessage());
        }

        long crawled = pages.stream().filter(PageNode::isCrawled).count();
        System.out.println("[Crawl] 成功爬取 " + crawled + "/" + pages.size() + " 個網站");
    }

    private static void crawlPageFast(PageNode page, List<String> queryTokens, LocalDate today) {
        try {
            WebCrawler.CrawlResult result = WebCrawler.crawl(page.getUrl());
            if (!result.isSuccess()) return;

            page.setCrawled(true);

            String crawledTitle = result.getTitle();
            String content = result.getTextContent();

            // (1) 若爬到更完整的標題就替換
            if (crawledTitle != null && !crawledTitle.isEmpty()
                    && crawledTitle.length() > (page.getTitle() != null ? page.getTitle().length() : 0)) {
                page.setTitle(crawledTitle);
            }

            // (2) 內文塞回 PageNode
            if (content != null) {
                page.setTextContent(content);
            }

            // (3) 用「標題 + 內文」再試一次抓日期
            if (page.getEventDate() == null) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") +
                                  (content != null ? content : "");
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) {
                    page.setEventDate(d);
                }
            }

            // (4) 用內容再試一次抓城市
            if (content != null) {
                String originCity = page.getCity();
                if (originCity == null || originCity.isEmpty() || "全台".equals(originCity)) {
                    String cityFromText = LocationRecognizer.extractCity(
                            ((crawledTitle != null) ? crawledTitle + " " : "") + content
                    );
                    if (cityFromText != null && !cityFromText.isEmpty()) {
                        page.setCity(cityFromText);
                    }
                }
            }

            // (5) 內文關鍵字 match → 額外分數
            if (content != null && !content.isEmpty()) {
                int matchCount = 0;
                String contentLower = content.toLowerCase();
                for (String token : queryTokens) {
                    if (contentLower.contains(token.toLowerCase())) {
                        matchCount++;
                    }
                }
                page.addScore(matchCount * 5);
            }

            // (6) 建立子頁節點 (保留原始邏輯)
            List<String> links = result.getLinks();
            if (links != null) {
                int subCount = Math.min(links.size(), 10);
                for (int i = 0; i < subCount; i++) {
                    String link = links.get(i);
                    SubPageNode sub = new SubPageNode(
                            link,
                            extractTitleFromUrl(link),
                            "",
                            page.getUrl()
                    );
                    page.addSubPage(sub);
                }
            }

        } catch (Exception ignore) {}
    }

    private static String extractTitleFromUrl(String url) {
        if (url == null) return "";
        try {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < url.length() - 1) {
                String segment = url.substring(lastSlash + 1);
                int dot = segment.lastIndexOf('.');
                if (dot > 0) segment = segment.substring(0, dot);
                int q = segment.indexOf('?');
                if (q > 0) segment = segment.substring(0, q);
                return segment.replace("-", " ").replace("_", " ");
            }
        } catch (Exception ignored) {}
        return "";
    }

    // =====================================================
    // 查詢 / PageNode 建立
    // =====================================================

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

    private static PageNode createPageNode(GoogleConnector.Result r, String query,
                                           List<String> queryTokens, String userCity,
                                           LocalDate today) {
        if (shouldExclude(r.title, r.link)) return null;

        LocalDate eventDate = extractDateFromTitle(r.title, today);

        Map<Keyword, Integer> tf = new HashMap<>();
        for (String token : queryTokens) {
            Keyword k = Keyword.of(token);
            tf.put(k, tf.getOrDefault(k, 0) + 1);
        }

        String titleLower = r.title.toLowerCase();
        for (String term : EVENT_TERMS) {
            if (titleLower.contains(term.toLowerCase())) {
                Keyword k = Keyword.of(term);
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
        }

        String city = LocationRecognizer.extractCity(r.title);
        if (city == null || city.isEmpty()) {
            city = "全台";
        }

        String domain = extractDomain(r.link);
        List<String> tokensCopy = new ArrayList<>(queryTokens);

        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, tokensCopy);
    }

    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase();

        boolean hasCity = CITY_ALIASES.keySet().stream()
                .anyMatch(alias -> lower.contains(alias.toLowerCase()));

        boolean hasEventWord = EVENT_TERMS.stream()
                .anyMatch(term -> lower.contains(term.toLowerCase()));

        boolean hasYear = lower.matches(".*20\\d{2}.*") || lower.contains("今年");
        boolean hasTaiwan = lower.contains("台灣") || lower.contains("臺灣") || lower.contains("taiwan");

        StringBuilder sb = new StringBuilder(q);

        if (!hasEventWord) {
            sb.append(" (活動 OR 展覽 OR 演唱會 OR 音樂會 OR 市集)");
        } else {
             for (Map.Entry<String, List<String>> e : CATEGORY_EXPANSIONS.entrySet()) {
                String key = e.getKey().toLowerCase();
                if (lower.contains(key)) {
                    sb.append(" (").append(e.getKey());
                    for (String alias : e.getValue()) {
                        if (!lower.contains(alias.toLowerCase())) {
                            sb.append(" OR ").append(alias);
                        }
                    }
                    sb.append(")");
                    break;
                }
            }
        }

        if (!hasYear) {
            int year = LocalDate.now().getYear();
            sb.append(" ").append(year).append(" OR ").append(year + 1);
        }

        if (!hasTaiwan) {
            sb.append(" 台灣");
        }

        // 🚨 這裡保留原有的排除，並加上海外排除
        sb.append(" -申請 -申請辦法 -徵選 -補助 -招標 -採購 -招生 -簡章 -課程簡章 -履歷");
        sb.append(" -日本 -東京 -大阪 -首爾 -韓國 -機票 -自由行 -簽證 -入境 -代購"); // 新增

        return sb.toString();
    }

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.isEmpty()) return tokens;
        for (String raw : query.split("\\s+")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if ("OR".equalsIgnoreCase(t) || "AND".equalsIgnoreCase(t)) continue;
            if (t.startsWith("-")) continue; // 忽略排除詞
            tokens.add(t);
        }
        
        List<String> expanded = new ArrayList<>(tokens);
        for (String t : tokens) {
            String base = t.replaceAll("[\\p{Punct}]", "");
            if (CATEGORY_EXPANSIONS.containsKey(base)) {
                expanded.addAll(CATEGORY_EXPANSIONS.get(base));
            }
        }
        return expanded;
    }

    // =====================================================
    // 工具函式：城市過濾 / 排除網址 / 日期解析
    // =====================================================

    // 🚨 新增：海外內容判斷邏輯
    private static boolean isLikelyForeign(String title, String content) {
        String text = (title + " " + content).toLowerCase();
        
        // 檢查是否有海外關鍵字
        boolean hasForeign = false;
        for (String key : FOREIGN_KEYWORDS) {
            if (text.contains(key.toLowerCase())) {
                hasForeign = true;
                break;
            }
        }
        if (!hasForeign) return false;

        // 如果有海外關鍵字，但也有台灣關鍵字，可能是「日本展在台灣」，所以不算海外
        return !hasTaiwanCity(text);
    }

    private static boolean hasTaiwanCity(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("台灣") || lower.contains("臺灣") || lower.contains("taiwan")) return true;
        for (String alias : CITY_ALIASES.keySet()) {
            if (lower.contains(alias.toLowerCase())) return true;
        }
        return false;
    }

    private static List<PageNode> filterByUserCityWithContent(List<PageNode> pages, String userCity) {
        // 保留你原本完整的邏輯
        if (pages == null || pages.isEmpty()) return pages;

        String normalizedTarget = normalizeCity(userCity);
        if (normalizedTarget == null || normalizedTarget.isEmpty()) return pages;

        String targetLower = normalizedTarget.toLowerCase();
        List<String> taiwanCityTerms = new ArrayList<>();
        for (String alias : CITY_ALIASES.keySet()) {
            taiwanCityTerms.add(alias.toLowerCase());
        }
        taiwanCityTerms.add("台灣");
        taiwanCityTerms.add("臺灣");

        List<PageNode> kept = new ArrayList<>();

        for (PageNode p : pages) {
            String city  = normalizeCity(p.getCity());
            String title = (p.getTitle() != null) ? p.getTitle() : "";
            String text  = (p.getTextContent() != null) ? p.getTextContent() : "";
            String combinedLower = (title + " " + text).toLowerCase();

            boolean isAllTaiwan = (city == null || city.isEmpty() || "全台".equals(city));
            boolean matchByCityField = city != null && !city.isEmpty() && city.equals(normalizedTarget);
            boolean matchByText = combinedLower.contains(targetLower);

            if (!matchByText && "台北".equals(normalizedTarget)) {
                if (combinedLower.contains("大台北") || combinedLower.contains("雙北") || combinedLower.contains("北北基")) {
                    matchByText = true;
                }
            }

            boolean hasTaiwanCityInText = false;
            for (String term : taiwanCityTerms) {
                if (combinedLower.contains(term)) {
                    hasTaiwanCityInText = true;
                    break;
                }
            }

            // 這裡已經有 isLikelyForeign 擋在前面了，但原本邏輯保留也無妨
            boolean hasForeignKeyword = false;
            for (String fk : FOREIGN_KEYWORDS) {
                if (combinedLower.contains(fk.toLowerCase())) {
                    hasForeignKeyword = true;
                    break;
                }
            }
            if (!hasTaiwanCityInText && hasForeignKeyword) {
                continue;
            }

            if (!isAllTaiwan && !normalizedTarget.equals(city) && !matchByText && !matchByCityField) {
                continue;
            }
            if (isAllTaiwan) {
                if (matchByText) kept.add(p);
                continue;
            }
            if (matchByCityField || matchByText) {
                kept.add(p);
            }
        }

        if (!kept.isEmpty()) {
            System.out.println("[CityFilter] 依城市過濾後剩 " + kept.size() + " 筆");
            return kept;
        } else {
            System.out.println("[CityFilter] 全部被濾掉，回退使用原始結果");
            return pages;
        }
    }

    private static boolean shouldExclude(String title, String url) {
        if (url == null) return false;
        if (title != null && title.contains("Google Custom Search")) return true;
        String lowerUrl = url.toLowerCase();
        for (String domain : EXCLUDED_DOMAINS) {
            if (lowerUrl.contains(domain)) return true;
        }
        return false;
    }

    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        if (title == null) return null;
        return parseDate(title, today);
    }

    private static LocalDate extractDateFromContent(String text, LocalDate today) {
        if (text == null) return null;
        String content = text.length() > 8000 ? text.substring(0, 8000) : text;

        String[] hints = {"活動日期", "市集時間", "活動時間", "展覽日期", "日期", "時間"};
        for (String hint : hints) {
            int idx = content.indexOf(hint);
            if (idx >= 0) {
                int start = Math.max(0, idx - 20);
                int end = Math.min(content.length(), idx + 120);
                String slice = content.substring(start, end);
                LocalDate d = parseDate(slice, today);
                if (d != null) return d;
            }
        }
        return parseDate(content, today);
    }

    // 🚨 優化後的日期解析邏輯
    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null) return null;

        Pattern p1 = Pattern.compile("(20\\d{2})[./年\\-](0?[1-9]|1[0-2])[./月\\-](0?[1-9]|[12]\\d|3[01])");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m1.group(1)), Integer.parseInt(m1.group(2)), Integer.parseInt(m1.group(3)));
            } catch (Exception ignored) {}
        }

        Pattern pRoc = Pattern.compile("(1\\d{2})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日");
        Matcher mRoc = pRoc.matcher(text);
        if (mRoc.find()) {
            try {
                return LocalDate.of(Integer.parseInt(mRoc.group(1)) + 1911, Integer.parseInt(mRoc.group(2)), Integer.parseInt(mRoc.group(3)));
            } catch (Exception ignored) {}
        }

        Pattern p2 = Pattern.compile("(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])(?:[\\-~～—至到](0?[1-9]|[12]\\d|3[01]))?日?");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                int currentYear = today.getYear();
                
                // 邏輯：如果現在是 10-12 月，但抓到的月份是 1-3 月，推測是明年
                if (today.getMonthValue() >= 10 && month <= 3) {
                    return LocalDate.of(currentYear + 1, month, day);
                }
                
                // 邏輯：如果日期明顯已經過了很久 (超過60天)，才去考慮是否為明年，否則保留當年
                LocalDate date = LocalDate.of(currentYear, month, day);
                if (date.isBefore(today.minusDays(60))) {
                     // 這裡保守一點，如果是久遠以前的日期，交給 ranking 去扣分，不要隨便加一年
                     // 除非是特定的「明年」關鍵字，但這裡 regex 沒抓到
                }
                return date;
            } catch (Exception ignored) {}
        }

        Pattern p3 = Pattern.compile("(0?[1-9]|1[0-2])[./\\-](0?[1-9]|[12]\\d|3[01])");
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            try {
                int month = Integer.parseInt(m3.group(1));
                int day = Integer.parseInt(m3.group(2));
                int currentYear = today.getYear();
                if (today.getMonthValue() >= 10 && month <= 3) {
                    return LocalDate.of(currentYear + 1, month, day);
                }
                return LocalDate.of(currentYear, month, day);
            } catch (Exception ignored) {}
        }
        
        if (text.contains("即日起") || text.contains("現正展出") || 
            text.contains("常設展") || text.contains("長期展出")) {
            return today.plusDays(30);
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

    private static String normalizeCity(String city) {
        if (city == null) return null;
        String trimmed = city.trim();
        if (trimmed.isEmpty()) return trimmed;
        String lower = trimmed.toLowerCase();
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) {
                return e.getValue();
            }
        }
        return trimmed;
    }

    private static void applyUserCityBoost(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty() || user == null) return;

        String userCity = normalizeCity(user.getUserCity());
        if (userCity == null || userCity.isEmpty()) return;

        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;

        Map<PageNode, Double> newScores = new HashMap<>();
        for (PageNode p : pages) {
            double score = p.getTotalScore();
            String pageCity = normalizeCity(p.getCity());

            if (pageCity != null && !pageCity.isEmpty()) {
                if (pageCity.equals(userCity)) {
                    score *= 1.2; 
                } else {
                    score *= 0.8; 
                }
            }
            newScores.put(p, score);
            max = Math.max(max, score);
            min = Math.min(min, score);
        }

        if (!Double.isFinite(max) || !Double.isFinite(min)) return;
        double range = max - min;
        if (range < 1e-6) range = 1.0;

        for (PageNode p : pages) {
            double score = newScores.get(p);
            double normalized = ((score - min) / range) * 90 + 10;
            p.setTotalScore(Math.round(normalized * 10) / 10.0);
        }
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
        System.out.println("\n📊 搜尋結果摘要（共 " + pages.size() + " 筆）：");
        System.out.println("─".repeat(60));
        int rank = 1;
        for (PageNode p : pages) {
            String dateStr = (p.getEventDate() != null) ? p.getEventDate().toString() : "無日期";
            System.out.printf("#%d [%.1f] %s | %s%n",
                    rank++, p.getTotalScore(), dateStr, truncate(p.getTitle(), 35));
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