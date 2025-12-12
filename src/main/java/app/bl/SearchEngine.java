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
            
            // 找出最相關的子網頁
            SubPageNode bestSub = null;
            double bestSubScore = 0;
            
            for (SubPageNode sub : subPages) {
                // 計算子網頁與查詢的相關度
                double relevance = calculateSubPageRelevance(sub, queryTokens);
                if (relevance > bestSubScore) {
                    bestSubScore = relevance;
                    bestSub = sub;
                }
            }
            
            // 判斷是否要提升子網頁
            // 條件：子網頁相關度 > 閾值，且標題包含查詢關鍵字
            boolean shouldPromote = bestSub != null && 
                                   bestSubScore >= 3.0 &&
                                   containsQueryTerms(bestSub.getTitle(), queryTokens);
            
            if (shouldPromote && !seenUrls.contains(bestSub.getUrl())) {
                // 把子網頁轉換為 PageNode
                PageNode promoted = convertSubToPage(bestSub, parent, queryTokens, today);
                if (promoted != null) {
                    // 繼承父網頁的部分分數
                    promoted.setScore(parent.getScore() * 0.8 + bestSubScore);
                    result.add(promoted);
                    seenUrls.add(promoted.getUrl());
                    System.out.println("[Promote] 提升子網頁: " + truncate(bestSub.getTitle(), 40));
                }
            }
            
            // 保留原始父網頁（如果還沒被加入）
            if (!seenUrls.contains(parent.getUrl())) {
                result.add(parent);
                seenUrls.add(parent.getUrl());
            }
        }
        
        return result;
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
        
        // 0. 標題年份快速判斷（如「2024全台活動」→ 過期）
        Pattern yearOnlyPattern = Pattern.compile("(20[2-3][0-9])(?:年|全台|精選|最新|活動|整理|市集|展覽)");
        Matcher ym = yearOnlyPattern.matcher(text);
        if (ym.find()) {
            int mentionedYear = Integer.parseInt(ym.group(1));
            if (mentionedYear < currentYear) {
                return LocalDate.of(mentionedYear, 12, 31); // 標記為該年底（過期）
            }
        }
        
        // 0.5 相對日期（今天、明天、這週末等）
        LocalDate relativeDate = parseRelativeDate(text, today);
        if (relativeDate != null) {
            return relativeDate;
        }
        
        // 1. 完整日期格式 yyyy/MM/dd 或 yyyy-MM-dd 或 yyyy年M月d日
        Pattern p1 = Pattern.compile("(20[2-3]\\d)[/.\\-年](0?[1-9]|1[0-2])[/.\\-月](0?[1-9]|[12]\\d|3[01])");
        Matcher m1 = p1.matcher(text);
        List<LocalDate> foundDates = new ArrayList<>();
        
        while (m1.find()) {
            try {
                int y = Integer.parseInt(m1.group(1));
                int m = Integer.parseInt(m1.group(2));
                int d = Integer.parseInt(m1.group(3));
                LocalDate date = LocalDate.of(y, m, d);
                foundDates.add(date);
            } catch (Exception ignored) {}
        }
        
        // 2. 民國年格式 1XX年M月d日
        Pattern pRoc = Pattern.compile("(1[0-1]\\d)年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日?");
        Matcher mRoc = pRoc.matcher(text);
        while (mRoc.find()) {
            try {
                int y = Integer.parseInt(mRoc.group(1)) + 1911;
                int m = Integer.parseInt(mRoc.group(2));
                int d = Integer.parseInt(mRoc.group(3));
                LocalDate date = LocalDate.of(y, m, d);
                foundDates.add(date);
            } catch (Exception ignored) {}
        }
        
        // 3. 無年份格式 M月d日（推測年份）
        Pattern p2 = Pattern.compile("(?<!\\d)(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日?");
        Matcher m2 = p2.matcher(text);
        while (m2.find()) {
            try {
                int m = Integer.parseInt(m2.group(1));
                int d = Integer.parseInt(m2.group(2));
                
                // 先檢查文中有沒有年份提示
                int guessYear = currentYear;
                Pattern anyYear = Pattern.compile("(20[2-3]\\d)");
                Matcher ym2 = anyYear.matcher(text);
                if (ym2.find()) {
                    guessYear = Integer.parseInt(ym2.group(1));
                }
                
                LocalDate date = LocalDate.of(guessYear, m, d);
                // 如果日期已經過了超過2個月，可能是明年的
                if (date.isBefore(today.minusMonths(2)) && guessYear == currentYear) {
                    date = LocalDate.of(currentYear + 1, m, d);
                }
                foundDates.add(date);
            } catch (Exception ignored) {}
        }
        
        if (foundDates.isEmpty()) {
            // 4. 特殊關鍵字
            if (text.contains("即日起") || text.contains("常設展") || text.contains("長期展出")) {
                return today.plusDays(30);
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