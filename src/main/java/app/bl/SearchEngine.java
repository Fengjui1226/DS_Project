package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.bl.WebCrawler.CrawlResult;
import app.bl.WebCrawler.SiteResult;
import app.da.GoogleConnector;
import app.da.LocationRecognizer;

/**
 * SearchEngine - 整合 Google CSE + 爬蟲 + Ranking
 *
 * 這個版本的重點：
 * 1. 不會因為「日期過期」直接丟掉結果，只交給 RankCalculator 做新鮮度調整
 * 2. 保留 Facebook / Instagram / YouTube / Dcard 等社群網站來源
 * 3. 仍可利用城市與活動關鍵字，提升搜尋的精準度
 */
public class SearchEngine {

    // Google 一次抓多少筆結果
    private static final int MAX_GOOGLE_RESULTS = 20;

    // 是否啟用爬蟲（可以在 debug 時暫時關閉以加速）
    private static final boolean ENABLE_CRAWLING = true;

    // 活動相關關鍵字：用來計算 TF / 加強活動頁面的分數
    private static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "festival", "concert", "exhibition", "event",
        "表演", "藝術", "體驗", "親子", "戶外", "講座"
    );
    // 類型關鍵字 → 要自動擴充的相關搜尋詞
    // 例如使用者按「市集」，會自動加上 文創市集、假日市集、聖誕市集...
    private static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.of(
        "音樂", List.of(
            "音樂會", "演唱會", "live house", "現場演出",
            "音樂祭", "音樂節", "音樂表演", "concert", "live music"
        ),
        "展覽", List.of(
            "藝術展", "美術展", "設計展", "攝影展",
            "特展", "主題展", "exhibition", "art exhibition"
        ),
        "市集", List.of(
            "文創市集", "手作市集", "假日市集", "週末市集",
            "聖誕市集", "耶誕市集", "跳蚤市場", "市集活動", "市集攤位",
            "market", "bazaar", "flea market"
        ),
        "戶外", List.of(
            "露營", "野營", "登山", "健行", "步道",
            "野餐", "戶外活動", "戶外體驗", "outdoor event", "hiking", "camping"
        ),
        "親子", List.of(
            "親子活動", "親子同樂", "親子體驗", "親子市集",
            "親子課程", "親子工作坊", "kids event", "family event"
        ),
        "運動", List.of(
            "路跑", "馬拉松", "半馬", "全馬",
            "籃球賽", "棒球賽", "足球賽", "羽球賽",
            "自行車賽", "慢跑活動", "sports event"
        )
    );
    // 城市別名：把使用者輸入的別種寫法 mapping 成標準城市名稱
    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
        Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
        Map.entry("新北", "新北"),
        Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
        Map.entry("台南", "台南"), Map.entry("臺南", "台南"),
        Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
        Map.entry("桃園", "桃園"),
        Map.entry("基隆", "基隆"),
        Map.entry("新竹", "新竹")
    );

    // 想排除的網站（與活動關聯度較低或容易造成噪音）
    // 保留 Facebook / IG / YouTube / Dcard，因為很多活動只在社群平台上宣傳
    private static final Set<String> EXCLUDED_DOMAINS = Set.of(
        "x.com", "twitter.com",
        "ptt.cc"
    );

    // 最近一次搜尋的樹與結果（若之後 /api/tree /api/subpages 需要可以用）
    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    /**
     * 主要搜尋入口：給 SimpleServer 呼叫
     */
    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n========== 開始搜尋 ==========");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();

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

        // 2. 優化送給 Google 的查詢
        String refinedQuery = refineQuery(query);
        System.out.println("[Refined] " + refinedQuery);

        // 3. 呼叫 Google 搜尋
        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS);
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        // 4. 解析查詢 tokens
        List<String> queryTokens = parseQueryTokens(query);
        System.out.println("[Tokens] " + queryTokens);

        // 5. 逐筆建立 PageNode + 爬取內容
        System.out.println("\n[Step 2] 爬取網頁內容...");
        List<PageNode> pages = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;

            // 建立 PageNode（包含日期 / 城市 / domain 等欄位）
            PageNode page = createPageNode(r, query, queryTokens, userCity, today);
            if (page == null) continue;

            // 啟用爬蟲：抓主頁文字內容與子網頁
            if (ENABLE_CRAWLING) {
                crawlPageAndSubpages(page, queryTokens);
            }

            pages.add(page);
        }
        System.out.println("[Crawl] 完成爬取 " + pages.size() + " 個網站");

        // 6. 先依日期把結果拆成「未過期 / 無日期」與「已過期」
        List<PageNode> validPages = new ArrayList<>();
        List<PageNode> expiredPages = new ArrayList<>();

        for (PageNode p : pages) {
            LocalDate d = p.getEventDate();   // PageNode 裡應該有這個 getter
            if (d != null && d.isBefore(today)) {
                expiredPages.add(p);          // 明確早於今天 → 已過期
            } else {
                validPages.add(p);            // 未來 / 今天 / 無日期
            }
        }

        // 如果有未過期/無日期的結果，就只對這些做 ranking；
        // 如果一個都沒有（超冷門關鍵字），才退而求其次用全部 pages。
        List<PageNode> pagesToRank = validPages.isEmpty() ? pages : validPages;

        System.out.printf("[Filter] 未過期或無日期: %d, 已過期: %d%n",
                pagesToRank.size(), expiredPages.size());

        // 7. 計算 ranking 分數（只算 pagesToRank）
        System.out.println("\n[Step 3] 計算分數...");
        RankCalculator.rank(pagesToRank, user);

        // 8. 建立樹狀結構
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
     * 爬取頁面內容和子網頁（具 10 秒 timeout）
     */
    private static void crawlPageAndSubpages(PageNode page, List<String> queryTokens) {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
        Future<?> future = executor.submit(() -> {
            try {
                // 爬整個網站（含主頁與子頁）
                SiteResult site = WebCrawler.crawlSite(page.getUrl());

                if (site.getMainPage() != null && site.getMainPage().isSuccess()) {

                    // 🟢 取得主頁文字
                    String mainText = site.getMainPage().getTextContent();
                    page.setTextContent(mainText);
                    page.setCrawled(true);

                    // 🟢 如果 PageNode 目前還沒 eventDate（標題沒抓到日期）
                    try {
                        LocalDate today = LocalDate.now();
                        LocalDate contentDate = extractDateFromContent(mainText, today);

                        if (page.getEventDate() == null && contentDate != null) {
                            page.setEventDate(contentDate);
                        }
                    } catch (Exception ignore) {}

                    // 🟢 處理子網頁
                    for (CrawlResult subResult : site.getSubPages()) {
                        SubPageNode subPage = new SubPageNode(
                                subResult.getUrl(),
                                subResult.getTitle(),
                                subResult.getTextContent(),
                                page.getUrl()
                        );

                        subPage.calculateTF(queryTokens);
                        page.addSubPage(subPage);
                    }
                }
            } catch (Exception e) {
                System.out.println("[Crawl Error] " + page.getUrl() + " - " + e.getMessage());
            }
        });

        // 最多等 10 秒
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
     * 建立 PageNode（不再因為日期過期就把頁面丟掉）
     */
    private static PageNode createPageNode(GoogleConnector.Result r,
                                           String query,
                                           List<String> queryTokens,
                                           String userCity,
                                           LocalDate today) {

        if (r.title == null || r.link == null) return null;
        if (r.title.contains("Google Custom Search")) return null;
        if (shouldExclude(r.title, r.link)) return null;

        // 從標題裡抓活動日期（若有）
        LocalDate eventDate = extractDateFromTitle(r.title, today);

        // 計算關鍵字 TF
        Map<Keyword, Integer> tf = new HashMap<>();
        for (String token : queryTokens) {
            Keyword k = Keyword.of(token);
            tf.put(k, tf.getOrDefault(k, 0) + 1);
        }

        // 標題內含活動關鍵字時，額外加分
        String titleLower = r.title.toLowerCase();
        for (String term : EVENT_TERMS) {
            if (titleLower.contains(term.toLowerCase())) {
                Keyword k = Keyword.of(term);
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
        }

        // 判斷城市：標題 > 查詢 > 使用者設定
        String city = LocationRecognizer.extractCity(r.title);
        if (city == null || city.isEmpty()) {
            city = detectCityFromQuery(query);
        }
        if (city == null || city.isEmpty()) {
            city = (userCity != null) ? userCity : "";
        }

        String domain = extractDomain(r.link);
        List<String> tokensCopy = new ArrayList<>(queryTokens);

        return PageNode.of(
                r.link,
                r.title,
                tf,
                eventDate,
                city,
                domain,
                tokensCopy
        );
    }

       /**
     * 優化查詢字串：
     * - 補上「活動」
     * - 補上「台灣」
     * - 依照使用者選的類型（音樂/展覽/市集/戶外/親子/運動）自動擴充關鍵字
     */
    private static String refineQuery(String query) {
    if (query == null) return "";
    String q = query.trim();

    // 先做一份小寫版本
    String lower = q.toLowerCase();

    // 1. 檢查 query 裡有沒有任何「活動相關詞」
    boolean hasEventWord = false;
    for (String term : EVENT_TERMS) {
        if (lower.contains(term.toLowerCase())) {
            hasEventWord = true;
            break;
        }
    }

    // 2. 如果完全沒有活動詞，才補「活動」
    if (!hasEventWord && !lower.contains("活動") && !lower.contains("event")) {
        q = q + " 活動";
    }

    // 3. 可能剛剛有加字了，重算一次 lower
    lower = q.toLowerCase();

    // 4. 沒有「台灣」就補上
    if (!lower.contains("台灣") && !lower.contains("taiwan")) {
        q = q + " 台灣";
        lower = q.toLowerCase();
    }

    // 5. 依類型關鍵字擴充（市集 → 文創市集／假日市集／聖誕市集…）
    for (Map.Entry<String, List<String>> entry : CATEGORY_EXPANSIONS.entrySet()) {
        String key = entry.getKey();
        if (lower.contains(key.toLowerCase())) {
            for (String extra : entry.getValue()) {
                q += " " + extra;
            }
        }
    }

    return q;
}
    /**
     * 把查詢字串拆成 token 清單
     */
    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;

        String[] parts = query.trim().split("\\s+");
        for (String part : parts) {
            String p = part.trim();
            if (!p.isEmpty()) {
                tokens.add(p);
            }
        }
        return tokens;
    }

    /**
     * 要不要排除這個結果（目前只排除噪音性質網站）
     */
    private static boolean shouldExclude(String title, String url) {
        if (url == null) return false;

        String lowerUrl = url.toLowerCase();
        for (String domain : EXCLUDED_DOMAINS) {
            if (lowerUrl.contains(domain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 從標題中解析日期：
     * - 2024/10/26
     * - 10月26日（若已過去，視為明年同一天）
     */
    private static LocalDate extractDateFromTitle(String title, LocalDate today) {
        if (title == null) return null;

        // 2024/10/26、2024-10-26、2024.10.26
        Pattern p1 = Pattern.compile("(202\\d)[./\\-](\\d{1,2})[./\\-](\\d{1,2})");
        Matcher m1 = p1.matcher(title);
        if (m1.find()) {
            try {
                int year = Integer.parseInt(m1.group(1));
                int month = Integer.parseInt(m1.group(2));
                int day = Integer.parseInt(m1.group(3));
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) { }
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
            } catch (Exception ignored) { }
        }

        return null;
    }

    /**
     * 從查詢字串中偵測城市（例如「台北 音樂祭」）
     */
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
    /**
     * 從完整內文中嘗試抓出活動日期
     * 做法：
     *  - 先找有「活動日期」「市集時間」這類關鍵詞附近的日期
     *  - 再找常見的日期格式
     */
    private static LocalDate extractDateFromContent(String text, LocalDate today) {
        if (text == null) return null;

        // 先縮短一點：避免整篇太長
        String content = text;
        if (content.length() > 8000) {
            content = content.substring(0, 8000);
        }

        // 優先找「活動日期」「市集時間」「活動時間」附近的字串
        String[] hints = { "活動日期", "市集時間", "活動時間", "展覽日期", "日期", "時間" };
        for (String hint : hints) {
            int idx = content.indexOf(hint);
            if (idx >= 0) {
                int start = Math.max(0, idx - 10);
                int end   = Math.min(content.length(), idx + 40);
                String slice = content.substring(start, end);

                LocalDate d = extractDateFromTitle(slice, today); // 重用前面的規則
                if (d != null) {
                    return d;
                }
            }
        }

        // 找不到關鍵詞附近，就直接在全文上跑一次日期偵測
        LocalDate d = extractDateFromTitle(content, today);
        if (d != null) return d;

        return null;
    }
    /**
     * 由 URL 抽出 domain
     */
    private static String extractDomain(String url) {
        if (url == null) return "";
        try {
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) {
                u = u.substring(p + 3);
            }
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Console 上印出前 5 筆結果摘要
     */
    private static void printResultsSummary(List<PageNode> pages) {
        System.out.println("\n--- 結果摘要 ---");
        int rank = 1;
        for (PageNode p : pages) {
            System.out.printf("#%d [%.1f] %s (%d 子網頁)%n",
                    rank++, p.getTotalScore(), p.getTitle(), p.getSubPageCount());
            if (rank > 5) break;
        }
    }

    // ============ Getter ============

    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }

    public static List<PageNode> getLastResults() {
        return lastResults;
    }
}