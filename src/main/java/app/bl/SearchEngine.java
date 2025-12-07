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

import app.da.GoogleConnector;
import app.da.LocationRecognizer;

// 同 package 類別
import app.bl.PageNode;
import app.bl.SubPageNode;
import app.bl.Keyword;
import app.bl.WebCrawler;
import app.bl.RankCalculator;
import app.bl.Tree;
import app.bl.UserProfile;

public class SearchEngine {

    // 🔧 改這裡：讓 Google 一次最多抓 40 筆結果（GoogleConnector 會自動翻頁）
    private static final int MAX_GOOGLE_RESULTS = 40;
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
        /** 粗略判斷「海外文章」用的關鍵字 */
    private static final List<String> FOREIGN_KEYWORDS = List.of(
            "日本", "東京", "大阪", "北海道", "沖繩",
            "首爾", "釜山", "韓國",
            "香港", "澳門",
            "曼谷", "清邁", "泰國",
            "新加坡", "馬來西亞", "吉隆坡",
            "紐約", "洛杉磯", "舊金山", "美國",
            "倫敦", "巴黎", "歐洲", "澳洲", "雪梨", "悉尼",
            "海外", "國外旅遊"
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
            "x.com", "twitter.com", "ptt.cc"
    );

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    // =====================================================
    // 主流程
    // =====================================================

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v4.2 (城市 + 內文強化版) ║");
        System.out.println("╚══════════════════════════════════════════╝");
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

        // 2. 類別關鍵字擴充（市集 → 文創市集…）
        query = expandQuery(query);

        // 3. 給 Google 用的 query 微調（年份 / 排除「申請辦法」等）
        String refinedQuery = refineQuery(query);
        System.out.println("[Refined] " + refinedQuery);

        // ================== Step 1: Google ==================
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS, 3000); // timeout 3s
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        List<String> queryTokens = parseQueryTokens(query);
        System.out.println("[Tokens] " + queryTokens);

        // ================== Step 2: 建立 PageNode ==================
        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            PageNode page = createPageNode(r, query, queryTokens, userCity, today);
            if (page != null) pages.add(page);
        }
        System.out.println("[Pages] 建立 " + pages.size() + " 個頁面節點");

        // ================== Step 3: 並行爬取 ==================
        if (ENABLE_CRAWLING && !pages.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = SEARCH_TIMEOUT_MS - elapsed - 1000;
            if (remainingTime > 2000) {
                System.out.println("\n[Step 3] 並行爬取網頁 (限時 " + remainingTime + " ms)...");
                parallelCrawl(pages, queryTokens, remainingTime, today);
            } else {
                System.out.println("\n[Step 3] 跳過爬取（時間不足）");
            }
        }

        // 3.3 依使用者城市 + 內文過濾（放鬆版）
        if (userCity != null && !userCity.isEmpty()) {
            pages = filterByUserCityWithContent(pages, userCity);
        }

        // 3.5 依「是否過期」分組（沒有日期的一律當作未過期）
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
                validPages.size(), expiredPages.size());

        // ================== Step 4: 排名計算 ==================
        System.out.println("\n[Step 4] 計算分數...");
        RankCalculator.rank(pagesToRank, user, query);

        // 額外城市加權：使用者選城市時，加強該城市，壓低其他縣市
        applyUserCityBoost(pagesToRank, user);

        // 建 search tree / log
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
    // 爬蟲相關
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

    /**
     *  爬主頁：
     *   1. 把完整內文寫回 PageNode
     *   2. 用「內文 + 標題」重新抓日期 & 城市
     *   3. 內文關鍵字 match 給加分
     */
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

            // (2) 內文塞回 PageNode，給 RankCalculator 用
            if (content != null) {
                page.setTextContent(content);
            }

            // (3) 用「標題 + 內文」再試一次抓日期（舊的 eventDate 為 null 才補）
            if (page.getEventDate() == null) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") +
                                  (content != null ? content : "");
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) {
                    page.setEventDate(d);
                }
            }

            // (4) 用內容再試一次抓城市（原本 city 是「全台」或空再覆蓋）
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
                page.addScore(matchCount * 5);   // 每 match 一個加 5 分
            }

            // (6) 建立子頁節點（先只放 URL & 推測出來的短標題）
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

        } catch (Exception ignore) {
            // 爬失敗就當沒看到
        }
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

        // 先從標題抓城市，抓不到先暫標「全台」
        String city = LocationRecognizer.extractCity(r.title);
        if (city == null || city.isEmpty()) {
            city = "全台";
        }

        String domain = extractDomain(r.link);
        List<String> tokensCopy = new ArrayList<>(queryTokens);

        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, tokensCopy);
    }

    /**
     * 新版 refineQuery
     */
    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase();

        boolean hasCity = CITY_ALIASES.keySet().stream()
                .anyMatch(alias -> lower.contains(alias.toLowerCase()));

        boolean hasEventWord = EVENT_TERMS.stream()
                .anyMatch(term -> lower.contains(term.toLowerCase()));

        boolean hasYear =
                lower.matches(".*20\\d{2}.*") ||
                        lower.matches(".*1\\d{2}年.*") ||
                        lower.contains("今年") ||
                        lower.contains("明年");

        boolean hasTaiwan =
                lower.contains("台灣") ||
                        lower.contains("臺灣") ||
                        lower.contains("taiwan");

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

        if (!hasTaiwan && !hasCity) {
            sb.append(" 台灣");
        }

        sb.append(" -申請 -申請辦法 -徵選 -補助 -招標 -採購 -招生 -簡章 -課程簡章 -履歷");

        return sb.toString();
    }

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.isEmpty()) return tokens;

        for (String raw : query.split("\\s+")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if ("OR".equalsIgnoreCase(t) || "AND".equalsIgnoreCase(t)) continue;
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
    // 工具函式：城市過濾 / 排除網址 / 日期解析 / 城市處理 / 分數調整
    // =====================================================

    /**
     * 城市過濾（放鬆版）：
     *  - 明確標成別的城市，而且內文也沒提到 userCity → 直接丟掉
     *  - 「全台 / 未寫城市」一律保留，交給 ranking + 城市加權處理
     */
        /**
     * 用「page.city + 標題 + 內文」做城市過濾：
     *  - 明確標成別的城市，而且內文也沒提到 userCity → 丟掉
     *  - 「全台 / 未寫城市」的頁面，必須在文本出現 userCity 或「大台北 / 雙北 / 北北基」等才保留
     *  - 額外：如果整篇看起來都是國外關鍵字、幾乎沒有台灣城市 → 視為海外文章，直接丟掉
     *  - 如果全部被濾掉，就退回原 pages（避免結果為 0）
     */
    private static List<PageNode> filterByUserCityWithContent(List<PageNode> pages, String userCity) {
        if (pages == null || pages.isEmpty()) return pages;

        String normalizedTarget = normalizeCity(userCity);
        if (normalizedTarget == null || normalizedTarget.isEmpty()) return pages;

        String targetLower = normalizedTarget.toLowerCase();

        // 順便準備「所有台灣城市/關鍵字」列表，判斷文章裡到底有沒有提到台灣
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
            boolean matchByCityField =
                    city != null && !city.isEmpty() && city.equals(normalizedTarget);
            boolean matchByText = combinedLower.contains(targetLower);

            // 台北特例：出現「大台北、雙北、北北基」也算
            if (!matchByText && "台北".equals(normalizedTarget)) {
                if (combinedLower.contains("大台北") ||
                    combinedLower.contains("雙北")   ||
                    combinedLower.contains("北北基")) {
                    matchByText = true;
                }
            }

            // ===== 新增：粗略判斷是不是「海外文章」 =====
            boolean hasTaiwanCityInText = false;
            for (String term : taiwanCityTerms) {
                if (combinedLower.contains(term)) {
                    hasTaiwanCityInText = true;
                    break;
                }
            }

            boolean hasForeignKeyword = false;
            for (String fk : FOREIGN_KEYWORDS) {
                if (combinedLower.contains(fk.toLowerCase())) {
                    hasForeignKeyword = true;
                    break;
                }
            }

            // 如果文章幾乎沒有任何台灣城市字眼，又充滿國外關鍵字 → 視為海外文章，丟掉
            if (!hasTaiwanCityInText && hasForeignKeyword) {
                System.out.println("[CityFilter] 判定為海外文章，丟掉: " + truncate(title, 30));
                continue;
            }
            // ===== 海外判斷到這邊結束 =====

            // 明確標示「其他城市」且內文也沒提到 userCity → 直接丟掉
            if (!isAllTaiwan && !normalizedTarget.equals(city)
                    && !matchByText && !matchByCityField) {
                continue;
            }

            // 「全台 / 未寫城市」：必須在內容有提到 userCity 才保留
            if (isAllTaiwan) {
                if (matchByText) {
                    kept.add(p);
                }
                continue;
            }

            // 一般情況：城市欄位是 userCity 或內文有提到 userCity
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

    /** 強化版日期解析：標題內的各種寫法都盡量抓一個「起始日期」 */
    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        if (title == null) return null;
        return parseDate(title, today);
    }

    /** 內文解析日期：會先在「活動日期/時間」附近找，再整段掃描 */
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

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null) return null;

        Pattern p1 = Pattern.compile(
                "(20\\d{2})[./年\\-](0?[1-9]|1[0-2])[./月\\-](0?[1-9]|[12]\\d|3[01])"
        );
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            try {
                int year = Integer.parseInt(m1.group(1));
                int month = Integer.parseInt(m1.group(2));
                int day = Integer.parseInt(m1.group(3));
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {}
        }

        Pattern pRoc = Pattern.compile(
                "(1\\d{2})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日"
        );
        Matcher mRoc = pRoc.matcher(text);
        if (mRoc.find()) {
            try {
                int roc = Integer.parseInt(mRoc.group(1));
                int year = roc + 1911;
                int month = Integer.parseInt(mRoc.group(2));
                int day = Integer.parseInt(mRoc.group(3));
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {}
        }

        Pattern p2 = Pattern.compile(
                "(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])(?:[\\-~～—至到](0?[1-9]|[12]\\d|3[01]))?日"
        );
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                LocalDate date = LocalDate.of(today.getYear(), month, day);
                if (date.isBefore(today.minusDays(7))) {
                    date = date.plusYears(1);
                }
                return date;
            } catch (Exception ignored) {}
        }

        Pattern p3 = Pattern.compile(
                "(0?[1-9]|1[0-2])[./\\-](0?[1-9]|[12]\\d|3[01])(?:[\\-~～—至到](0?[1-9]|[12]\\d|3[01]))?"
        );
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            try {
                int month = Integer.parseInt(m3.group(1));
                int day = Integer.parseInt(m3.group(2));
                LocalDate date = LocalDate.of(today.getYear(), month, day);
                if (date.isBefore(today.minusDays(7))) {
                    date = date.plusYears(1);
                }
                return date;
            } catch (Exception ignored) {}
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

    /** 正規化城市名稱（臺北 / taipei → 台北） */
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

    /** 額外城市加權：同城市拉高、不同城市壓低，最後再 normalize 一次分數 */
    private static void applyUserCityBoost(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty() || user == null) return;

        String userCity = normalizeCity(user.getUserCity());
        if (userCity == null || userCity.isEmpty()) return;

        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;

        Map<PageNode, Double> newScores = new HashMap<>();
        for (PageNode p : pages) {
            double s = p.getTotalScore();
            String c = normalizeCity(p.getCity());

            if (c != null && !c.isEmpty() && !"全台".equals(c)) {
                if (c.equals(userCity)) {
                    s *= 1.3;
                } else {
                    s *= 0.5;
                }
            }
            newScores.put(p, s);
            max = Math.max(max, s);
            min = Math.min(min, s);
        }

        if (!Double.isFinite(max) || !Double.isFinite(min)) return;
        double range = max - min;
        if (range < 1e-6) range = 1.0;

        for (PageNode p : pages) {
            double s = newScores.get(p);
            double normalized = ((s - min) / range) * 90 + 10;
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
