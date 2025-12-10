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

/**
 * SearchEngine v6.0 - 城市優先權修復版
 * 
 * 核心修復：
 * 1. 查詢中的城市 > 選單選擇的城市 > GPS 定位城市
 * 2. 統一使用 "effectiveCity" 貫穿整個搜尋流程
 * 3. 優化海外內容過濾效能
 * 4. 改善日期處理邏輯
 */
public class SearchEngine {

    private static final int MAX_GOOGLE_RESULTS = 40;
    private static final int MAX_DEEP_CRAWL_COUNT = 15;
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 8000;
    private static final int CRAWL_PARALLEL_LIMIT = 15;
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

    private static final Set<String> FOREIGN_KEYWORDS = Set.of(
            "日本", "東京", "大阪", "北海道", "沖繩", "京都", "奈良", "名古屋", "福岡", "橫濱", 
            "涉谷", "新宿", "銀座", "池袋", "原宿", "淺草", "秋葉原", "神戶", "廣島", "仙台",
            "金澤", "札幌", "函館", "輕井澤", "富士山", "鎌倉", "箱根", "河口湖",
            "首爾", "釜山", "韓國", "濟州", "仁川", "明洞", "弘大", "江南", "東大門",
            "香港", "澳門", "上海", "北京", "深圳", "廣州", "成都", "杭州", "蘇州", "西安",
            "曼谷", "泰國", "新加坡", "馬來西亞", "越南", "峇里島", "印尼", "菲律賓", "吉隆坡",
            "清邁", "普吉島", "河內", "胡志明", "柬埔寨", "寮國", "緬甸",
            "紐西蘭", "新西蘭", "奧克蘭", "威靈頓", "基督城", "皇后鎮", "澳洲", "澳大利亞",
            "雪梨", "悉尼", "墨爾本", "布里斯本", "伯斯", "黃金海岸",
            "美國", "歐洲", "倫敦", "巴黎", "紐約", "洛杉磯", "舊金山", "芝加哥", "西雅圖",
            "德國", "法國", "義大利", "西班牙", "荷蘭", "瑞士", "奧地利", "捷克",
            "羅馬", "米蘭", "佛羅倫斯", "巴塞隆納", "阿姆斯特丹", "維也納", "布拉格",
            "機票", "自由行", "入境", "簽證", "匯率", "代購", "旅遊攻略", "行程規劃",
            "海外", "出國", "國外", "境外", "跨境", "航班"
    );

    private static final Map<String, List<String>> EXPANSION = Map.of(
            "市集", List.of("文創市集", "手作市集", "假日市集", "聖誕市集", "創意市集", "跳蚤市集"),
            "展覽", List.of("特展", "藝術展", "美術館展覽", "主題展覽", "攝影展", "設計展"),
            "音樂", List.of("音樂祭", "音樂會", "演唱會", "live演出", "音樂節"),
            "親子", List.of("親子活動", "家庭活動", "兒童活動", "親子市集"),
            "運動", List.of("路跑", "馬拉松", "健走", "運動賽事", "自行車")
    );

    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
            Map.entry("台北", "台北"), Map.entry("臺北", "台北"), Map.entry("taipei", "台北"),
            Map.entry("新北", "新北"),
            Map.entry("台中", "台中"), Map.entry("臺中", "台中"), Map.entry("taichung", "台中"),
            Map.entry("台南", "台南"), Map.entry("臺南", "台南"), Map.entry("tainan", "台南"),
            Map.entry("高雄", "高雄"), Map.entry("kaohsiung", "高雄"),
            Map.entry("桃園", "桃園"), Map.entry("基隆", "基隆"), Map.entry("新竹", "新竹"),
            Map.entry("嘉義", "嘉義"), Map.entry("花蓮", "花蓮"), Map.entry("台東", "台東"),
            Map.entry("宜蘭", "宜蘭"), Map.entry("屏東", "屏東"), Map.entry("彰化", "彰化"),
            Map.entry("南投", "南投"), Map.entry("苗栗", "苗栗"), Map.entry("雲林", "雲林")
    );

    private static final Set<String> TAIWAN_LANDMARKS = Set.of(
            "華山", "松菸", "駁二", "高流", "北流", "小巨蛋", "大巨蛋", "兩廳院", "國家音樂廳",
            "故宮", "北美館", "當代藝術館", "科博館", "海生館", "衛武營", "信義區", "西門町",
            "忠孝", "中山", "士林", "淡水", "九份", "平溪", "陽明山", "墾丁", "日月潭",
            "阿里山", "太魯閣", "清境", "草悟道", "勤美", "逢甲", "一中街", "東海",
            "新光三越", "sogo", "遠百", "誠品", "國父紀念館", "中正紀念堂"
    );

    private static final Set<String> EXCLUDED_DOMAINS = Set.of(
            "x.com", "twitter.com", "ptt.cc",
            "amazon.co.jp", "rakuten.co.jp", "yahoo.co.jp", "booking.com", "agoda.com"
    );

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v6.0 (City Priority Fixed)           ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        // 核心修復：建立城市優先權
        // 優先權：查詢中的城市 > UserProfile 的城市（選單/定位）
        
        String queryCity = detectCityFromQuery(query);
        String userCity = (user != null) ? user.getUserCity() : null;
        
        String effectiveCity;
        if (queryCity != null && !queryCity.isEmpty()) {
            effectiveCity = queryCity;
            System.out.println("[CityPriority] 使用查詢中的城市: " + effectiveCity);
        } else if (userCity != null && !userCity.isEmpty()) {
            effectiveCity = userCity;
            System.out.println("[CityPriority] 使用定位/選單城市: " + effectiveCity);
        } else {
            effectiveCity = null;
            System.out.println("[CityPriority] 無城市限制，搜尋全台");
        }

        String googleQuery = query;
        if (queryCity == null && effectiveCity != null) {
            googleQuery = effectiveCity + " " + query;
        }

        QueryUnderstanding.ParsedQuery parsedQuery = QueryUnderstanding.parse(googleQuery);
        System.out.println("[QueryUnderstanding] " + parsedQuery.primaryIntent);
        
        googleQuery = expandQuery(parsedQuery.expandedQuery);
        String refinedQuery = refineQuery(googleQuery);
        System.out.println("[Refined] " + refinedQuery);

        System.out.println("\n[Step 1] 呼叫 Google API...");
        List<GoogleConnector.Result> googleResults =
                GoogleConnector.search(refinedQuery, MAX_GOOGLE_RESULTS, 3000); 
        System.out.println("[Google] 取得 " + googleResults.size() + " 個結果");

        List<String> queryTokens = parseQueryTokens(query);

        System.out.println("\n[Step 2] 建立頁面節點...");
        List<PageNode> pages = new ArrayList<>();
        for (GoogleConnector.Result r : googleResults) {
            if (r == null || r.title == null || r.link == null) continue;
            
            PageNode page = createPageNode(r, query, queryTokens, effectiveCity, today);
            
            if (page != null) {
                if (isLikelyForeign(page.getTitle(), "")) {
                    continue; 
                }
                pages.add(page);
            }
        }
        System.out.println("[Pages] 建立 " + pages.size() + " 個頁面節點");

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

        List<PageNode> filteredPages = new ArrayList<>();
        for (PageNode p : pages) {
            String content = (p.getTextContent() != null) ? p.getTextContent() : "";
            if (isLikelyForeign(p.getTitle(), content)) {
                if (!hasTaiwanCity(p.getTitle() + " " + content)) {
                    continue; 
                }
            }
            filteredPages.add(p);
        }
        pages = filteredPages;

        if (effectiveCity != null && !effectiveCity.isEmpty()) {
            pages = filterByEffectiveCity(pages, effectiveCity);
        }

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

        System.out.printf("[Filter] 未過期: %d, 已過期: %d%n", validPages.size(), expiredPages.size());

        System.out.println("\n[Step 4] 進階處理...");
        
        TFIDFCalculator.applyTFIDFScores(pagesToRank, query);
        EventInfoExtractor.applyCompletenessBonus(pagesToRank);
        pagesToRank = Deduplicator.deduplicate(pagesToRank);

        System.out.println("\n[Step 5] 計算最終分數...");
        
        UserProfile effectiveUser = new UserProfile();
        if (effectiveCity != null) {
            effectiveUser.setUserCity(effectiveCity);
        }
        
        RankCalculator.rank(pagesToRank, effectiveUser, query);
        applyUserCityBoost(pagesToRank, effectiveUser);

        Tree tree = new Tree();
        tree.addPages(pagesToRank);
        lastSearchTree = tree;
        lastResults = pagesToRank;

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n========== 搜尋完成 ==========");
        System.out.println("[Time] " + duration + " ms");
        System.out.println("[Results] " + pagesToRank.size() + " 個網站");
        System.out.println("[EffectiveCity] " + (effectiveCity != null ? effectiveCity : "全台"));
        printResultsSummary(pagesToRank);

        return pagesToRank;
    }

    private static List<PageNode> filterByEffectiveCity(List<PageNode> pages, String effectiveCity) {
        if (pages == null || pages.isEmpty()) return pages;

        String normalizedTarget = normalizeCity(effectiveCity);
        if (normalizedTarget == null || normalizedTarget.isEmpty()) return pages;

        String targetLower = normalizedTarget.toLowerCase();
        
        List<PageNode> kept = new ArrayList<>();
        List<PageNode> allTaiwan = new ArrayList<>();

        for (PageNode p : pages) {
            String city = normalizeCity(p.getCity());
            String title = (p.getTitle() != null) ? p.getTitle() : "";
            String text = (p.getTextContent() != null) ? p.getTextContent() : "";
            String combinedLower = (title + " " + text).toLowerCase();

            boolean isAllTaiwan = (city == null || city.isEmpty() || "全台".equals(city));
            boolean matchByCityField = city != null && !city.isEmpty() && city.equals(normalizedTarget);
            boolean matchByText = combinedLower.contains(targetLower);

            if (!matchByText && "台北".equals(normalizedTarget)) {
                if (combinedLower.contains("大台北") || combinedLower.contains("雙北") || 
                    combinedLower.contains("北北基") || combinedLower.contains("新北")) {
                    matchByText = true;
                }
            }

            if (isLikelyForeign(title, text) && !hasTaiwanCity(title + " " + text)) {
                continue;
            }

            if (matchByCityField || matchByText) {
                kept.add(p);
            } else if (isAllTaiwan) {
                allTaiwan.add(p);
            }
        }

        if (!kept.isEmpty()) {
            int allTaiwanLimit = Math.min(allTaiwan.size(), 5);
            kept.addAll(allTaiwan.subList(0, allTaiwanLimit));
            System.out.println("[CityFilter] 依城市 '" + effectiveCity + "' 過濾後剩 " + kept.size() + " 筆");
            return kept;
        } else {
            System.out.println("[CityFilter] 無明確匹配 '" + effectiveCity + "'，回退使用原始結果");
            return pages;
        }
    }

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
            System.out.println("[Crawl] 全部完成");
        } catch (TimeoutException e) {
            System.out.println("[Crawl] 時間到，已完成的照樣用");
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

            if (crawledTitle != null && !crawledTitle.isEmpty()
                    && crawledTitle.length() > (page.getTitle() != null ? page.getTitle().length() : 0)) {
                page.setTitle(crawledTitle);
            }

            if (content != null) {
                page.setTextContent(content);
            }

            if (page.getEventDate() == null) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") +
                                  (content != null ? content : "");
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) {
                    page.setEventDate(d);
                }
            }

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

            List<String> links = result.getLinks();
            if (links != null) {
                int subCount = Math.min(links.size(), 10);
                for (int i = 0; i < subCount; i++) {
                    String link = links.get(i);
                    SubPageNode sub = new SubPageNode(link, extractTitleFromUrl(link), "", page.getUrl());
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
                                           List<String> queryTokens, String effectiveCity,
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

        sb.append(" -申請 -申請辦法 -徵選 -補助 -招標 -採購 -招生 -簡章 -課程簡章 -履歷");
        sb.append(" -日本 -東京 -大阪 -首爾 -韓國 -機票 -自由行 -簽證 -入境 -代購");
        sb.append(" -紐西蘭 -新西蘭 -奧克蘭 -澳洲 -雪梨 -墨爾本 -香港 -曼谷 -新加坡");
        sb.append(" -北海道 -沖繩 -京都 -峇里島 -普吉島 -濟州 -旅遊攻略");

        return sb.toString();
    }

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.isEmpty()) return tokens;
        for (String raw : query.split("\\s+")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if ("OR".equalsIgnoreCase(t) || "AND".equalsIgnoreCase(t)) continue;
            if (t.startsWith("-")) continue;
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

    private static boolean isLikelyForeign(String title, String content) {
        String text = (title + " " + content).toLowerCase();
        
        int foreignCount = 0;
        for (String key : FOREIGN_KEYWORDS) {
            if (text.contains(key.toLowerCase())) {
                foreignCount++;
                if (foreignCount >= 2) break;
            }
        }
        
        if (foreignCount == 0) return false;
        
        int taiwanCount = 0;
        if (text.contains("台灣") || text.contains("臺灣") || text.contains("taiwan")) {
            taiwanCount += 2;
        }
        for (String alias : CITY_ALIASES.keySet()) {
            if (text.contains(alias.toLowerCase())) {
                taiwanCount++;
            }
        }
        for (String landmark : TAIWAN_LANDMARKS) {
            if (text.contains(landmark.toLowerCase())) {
                taiwanCount++;
            }
        }
        
        String titleLower = title.toLowerCase();
        boolean titleHasForeign = FOREIGN_KEYWORDS.stream()
                .anyMatch(k -> titleLower.contains(k.toLowerCase()));
        
        boolean titleHasTaiwan = titleLower.contains("台灣") || titleLower.contains("臺灣") || 
                titleLower.contains("taiwan") ||
                CITY_ALIASES.keySet().stream().anyMatch(a -> titleLower.contains(a.toLowerCase()));
        
        if (titleHasForeign && !titleHasTaiwan) {
            return true;
        }
        
        if (foreignCount >= 2 && taiwanCount < 2) {
            return true;
        }
        
        if (foreignCount >= 1 && taiwanCount == 0) {
            return true;
        }
        
        return false;
    }

    private static boolean hasTaiwanCity(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("台灣") || lower.contains("臺灣") || lower.contains("taiwan")) return true;
        
        for (String alias : CITY_ALIASES.keySet()) {
            if (lower.contains(alias.toLowerCase())) return true;
        }
        
        for (String landmark : TAIWAN_LANDMARKS) {
            if (lower.contains(landmark.toLowerCase())) return true;
        }
        
        return false;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return null;
        String lower = query.toLowerCase();
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) {
                return e.getValue();
            }
        }
        return null;
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

            if (pageCity != null && !pageCity.isEmpty() && !"全台".equals(pageCity)) {
                if (pageCity.equals(userCity)) {
                    score *= 1.15;
                } else {
                    score *= 0.85;
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

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null) return null;

        Pattern p1 = Pattern.compile("(20\\d{2})[./年\\-](0?[1-9]|1[0-2])[./月\\-](0?[1-9]|[12]\\d|3[01])");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m1.group(1)), 
                                   Integer.parseInt(m1.group(2)), 
                                   Integer.parseInt(m1.group(3)));
            } catch (Exception ignored) {}
        }

        Pattern pRoc = Pattern.compile("(1\\d{2})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日");
        Matcher mRoc = pRoc.matcher(text);
        if (mRoc.find()) {
            try {
                return LocalDate.of(Integer.parseInt(mRoc.group(1)) + 1911, 
                                   Integer.parseInt(mRoc.group(2)), 
                                   Integer.parseInt(mRoc.group(3)));
            } catch (Exception ignored) {}
        }

        Pattern p2 = Pattern.compile("(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])(?:[\\-~～—至到](0?[1-9]|[12]\\d|3[01]))?日?");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                int currentYear = today.getYear();
                
                if (today.getMonthValue() >= 10 && month <= 4) {
                    return LocalDate.of(currentYear + 1, month, day);
                }
                
                LocalDate date = LocalDate.of(currentYear, month, day);
                if (date.isBefore(today.minusDays(30))) {
                    LocalDate nextYearDate = LocalDate.of(currentYear + 1, month, day);
                    if (nextYearDate.isBefore(today.plusMonths(14))) {
                        return nextYearDate;
                    }
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
                if (today.getMonthValue() >= 10 && month <= 4) {
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
        System.out.println("─".repeat(70));
        int rank = 1;
        for (PageNode p : pages) {
            String dateStr = (p.getEventDate() != null) ? p.getEventDate().toString() : "無日期";
            String cityStr = (p.getCity() != null) ? p.getCity() : "全台";
            System.out.printf("#%d [%.1f] %s | %s | %s%n",
                    rank++, p.getTotalScore(), dateStr, cityStr, truncate(p.getTitle(), 30));
            if (rank > 15) {
                System.out.println("... 以及更多 " + (pages.size() - 15) + " 筆結果");
                break;
            }
        }
        System.out.println("─".repeat(70));
    }

    public static Tree getLastSearchTree() {
        return lastSearchTree;
    }

    public static List<PageNode> getLastResults() {
        return lastResults;
    }
}