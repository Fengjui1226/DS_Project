package app.bl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set; // Added missing import
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
 * SearchEngine v7.1 - Fixed Compilation Errors (Missing methods and imports)
 */
public class SearchEngine {

    private static final int MAX_GOOGLE_RESULTS = 40;
    private static final int MAX_DEEP_CRAWL_COUNT = 40;
    private static final boolean ENABLE_CRAWLING = true;
    private static final int SEARCH_TIMEOUT_MS = 20000;
    private static final int CRAWL_PARALLEL_LIMIT = 20;
    private static final ExecutorService CRAWL_EXECUTOR =
            Executors.newFixedThreadPool(CRAWL_PARALLEL_LIMIT);

    private static Tree lastSearchTree;
    private static List<PageNode> lastResults = new ArrayList<>();

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   🔍 EventFinder v7.1 (Debugged)                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("[Query] " + query);

        long startTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

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
                if (Constants.isLikelyForeign(page.getTitle())) {
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

        // ============ 新版過濾邏輯 ============
        int currentYear = today.getYear();
        List<PageNode> filteredPages = new ArrayList<>();
        int foreignFiltered = 0;
        int oldYearFiltered = 0;
        
        for (PageNode p : pages) {
            String title = (p.getTitle() != null) ? p.getTitle() : "";
            String content = (p.getTextContent() != null) ? p.getTextContent() : "";
            String combined = title + " " + content;
            
            // 1. 海外內容嚴格過濾（標題有海外地名直接移除）
            if (containsForeignLocation(title)) {
                foreignFiltered++;
                System.out.println("  [過濾] 海外內容: " + truncate(title, 40));
                continue;
            }
            
            // 2. 檢查年份 - 過去年份直接過濾
            int detectedYear = detectYearInText(combined);
            if (detectedYear > 0 && detectedYear < currentYear) {
                oldYearFiltered++;
                System.out.println("  [過濾] 過期年份(" + detectedYear + "): " + truncate(title, 40));
                continue;
            }
            
            // 3. 年份加分機制
            if (detectedYear == currentYear) {
                p.addScore(15);  // 今年 +15
            } else if (detectedYear == currentYear + 1) {
                p.addScore(25);  // 明年 +25（2026 大加分）
            } else if (detectedYear == 0) {
                // 無年份，檢查是否有台灣地名來判斷相關性
                if (!hasTaiwanLocation(combined)) {
                    p.addScore(-10);  // 無年份且無台灣地名，扣分
                }
            }
            
            filteredPages.add(p);
        }
        pages = filteredPages;
        
        System.out.printf("[Filter] 海外過濾: %d, 舊年份過濾: %d, 保留: %d%n", 
                          foreignFiltered, oldYearFiltered, pages.size());

        if (effectiveCity != null && !effectiveCity.isEmpty()) {
            pages = filterByEffectiveCity(pages, effectiveCity);
        }

        // 日期過期檢查（有明確日期的才過濾）
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

            if (!matchByText) {
                // 特定城市的地標對照
                if ("台北".equals(normalizedTarget)) {
                    if (combinedLower.contains("大台北") || combinedLower.contains("雙北") ||
                        combinedLower.contains("北北基") || combinedLower.contains("新北") ||
                        combinedLower.contains("信義區") || combinedLower.contains("松菸")) {
                        matchByText = true;
                    }
                } else if ("台中".equals(normalizedTarget)) {
                    if (combinedLower.contains("勤美") || combinedLower.contains("草悟道") ||
                        combinedLower.contains("審計新村") || combinedLower.contains("歌劇院") ||
                        combinedLower.contains("逢甲") || combinedLower.contains("西屯")) {
                        matchByText = true;
                    }
                } else if ("高雄".equals(normalizedTarget)) {
                    if (combinedLower.contains("駁二") || combinedLower.contains("衛武營") ||
                        combinedLower.contains("高流") || combinedLower.contains("愛河")) {
                        matchByText = true;
                    }
                }
            }

            String combined = title + " " + text;
            if (Constants.isLikelyForeign(combined)) {
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
            return kept;
        } else {
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
        } catch (Exception ignore) {}
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
            if (content != null) page.setTextContent(content);

            if (page.getEventDate() == null) {
                String combined = (crawledTitle != null ? crawledTitle + " " : "") +
                                  (content != null ? content : "");
                LocalDate d = extractDateFromContent(combined, today);
                if (d != null) page.setEventDate(d);
            }

            if (content != null) {
                String originCity = page.getCity();
                if (originCity == null || originCity.isEmpty() || "全台".equals(originCity)) {
                    String cityFromText = extractCity(
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
                return segment.replace("-", " ").replace("_", " ");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String expandQuery(String q) {
        StringBuilder result = new StringBuilder(q);
        String lower = q.toLowerCase();
        for (Map.Entry<String, List<String>> e : CATEGORY_EXPANSIONS.entrySet()) {
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

        String city = extractCity(r.title);
        if (city == null || city.isEmpty()) city = "全台";

        String domain = extractDomain(r.link);
        List<String> tokensCopy = new ArrayList<>(queryTokens);

        return PageNode.of(r.link, r.title, tf, eventDate, city, domain, tokensCopy);
    }

    private static String refineQuery(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return q;
        StringBuilder sb = new StringBuilder(q);
        String lower = q.toLowerCase();

        boolean hasEventWord = EVENT_TERMS.stream().anyMatch(t -> lower.contains(t.toLowerCase()));
        if (!hasEventWord) {
            sb.append(" (活動 OR 展覽 OR 演唱會 OR 音樂會 OR 市集)");
        } else {
            for (Map.Entry<String, List<String>> e : CATEGORY_EXPANSIONS.entrySet()) {
                String key = e.getKey().toLowerCase();
                if (lower.contains(key)) {
                    sb.append(" (").append(e.getKey());
                    for (String alias : e.getValue()) {
                        if (!lower.contains(alias.toLowerCase())) sb.append(" OR ").append(alias);
                    }
                    sb.append(")");
                    break;
                }
            }
        }

        if (!lower.contains("20")) {
            int year = LocalDate.now().getYear();
            sb.append(" ").append(year).append(" OR ").append(year + 1);
        }
        if (!lower.contains("台灣") && !lower.contains("taiwan")) {
            sb.append(" 台灣");
        }

        sb.append(" -申請 -辦法 -徵選 -補助 -招標 -簡章");
        sb.append(" -日本 -東京 -大阪 -首爾 -機票 -簽證 -代購 -攻略");
        return sb.toString();
    }

    private static List<String> parseQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.isEmpty()) return tokens;
        for (String raw : query.split("\\s+")) {
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("-")) continue;
            tokens.add(t);
        }
        List<String> expanded = new ArrayList<>(tokens);
        for (String t : tokens) {
            String base = t.replaceAll("[\\p{Punct}]", "");
            if (CATEGORY_EXPANSIONS.containsKey(base)) expanded.addAll(CATEGORY_EXPANSIONS.get(base));
        }
        return expanded;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return null;
        return extractCity(query);
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
                if (pageCity.equals(userCity)) score *= 1.15;
                else score *= 0.85;
            }
            newScores.put(p, score);
            max = Math.max(max, score);
            min = Math.min(min, score);
        }

        if (!Double.isFinite(max)) return;
        double range = max - min;
        if (range < 1e-6) range = 1.0;

        for (PageNode p : pages) {
            double normalized = ((newScores.get(p) - min) / range) * 90 + 10;
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
                return parseDate(content.substring(start, end), today);
            }
        }
        return parseDate(content, today);
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null) return null;
        int currentYear = today.getYear();

        // 0. 先檢查標題是否明確標記過去年份（如「2024全台活動」「2023精選」）
        //    這類標題通常沒有完整日期，但年份本身就說明是過期內容
        Pattern yearOnlyPattern = Pattern.compile("(20[0-2][0-9])(?:年|全台|精選|最新|活動|整理|懶人包)");
        Matcher yearOnlyMatcher = yearOnlyPattern.matcher(text);
        if (yearOnlyMatcher.find()) {
            int mentionedYear = Integer.parseInt(yearOnlyMatcher.group(1));
            if (mentionedYear < currentYear) {
                // 明確過去年份，標記為該年年底（確保被判定過期）
                return LocalDate.of(mentionedYear, 12, 31);
            }
        }

        // 1. 完整年份格式
        Pattern p1 = Pattern.compile("(20\\d{2})\\s*?[./年\\-]\\s*?(0?[1-9]|1[0-2])\\s*?[./月\\-]\\s*?(0?[1-9]|[12]\\d|3[01])");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m1.group(1)),
                                   Integer.parseInt(m1.group(2)),
                                   Integer.parseInt(m1.group(3)));
            } catch (Exception ignored) {}
        }

        // 2. 民國年
        Pattern pRoc = Pattern.compile("(1\\d{2})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日");
        Matcher mRoc = pRoc.matcher(text);
        if (mRoc.find()) {
            try {
                return LocalDate.of(Integer.parseInt(mRoc.group(1)) + 1911,
                                   Integer.parseInt(mRoc.group(2)),
                                   Integer.parseInt(mRoc.group(3)));
            } catch (Exception ignored) {}
        }

        // 3. 無年份格式
        Pattern p2 = Pattern.compile("(0?[1-9]|1[0-2])\\s*?[./月]\\s*?(0?[1-9]|[12]\\d|3[01])(?:[\\-~～—至到].*?)?");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            try {
                int month = Integer.parseInt(m2.group(1));
                int day = Integer.parseInt(m2.group(2));
                
                // 先檢查前面有沒有年份
                int start = m2.start();
                if (start >= 4) {
                    String prefix = text.substring(Math.max(0, start - 10), start);
                    Matcher yearMatcher = Pattern.compile("(20\\d{2})").matcher(prefix);
                    if (yearMatcher.find()) {
                        int foundYear = Integer.parseInt(yearMatcher.group(1));
                        return LocalDate.of(foundYear, month, day);
                    }
                }
                
                // 沒有年份時，檢查整個文字中是否有過去年份的暗示
                Matcher anyYearMatcher = Pattern.compile("(20[0-2][0-9])").matcher(text);
                if (anyYearMatcher.find()) {
                    int foundYear = Integer.parseInt(anyYearMatcher.group(1));
                    // 如果文中提到的年份是過去的，用那個年份
                    if (foundYear < currentYear) {
                        return LocalDate.of(foundYear, month, day);
                    } else if (foundYear == currentYear || foundYear == currentYear + 1) {
                        return LocalDate.of(foundYear, month, day);
                    }
                }
                
                return LocalDate.of(currentYear, month, day);
                
            } catch (Exception ignored) {}
        }
        
        if (text.contains("即日起") || text.contains("常設展")) {
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
        } catch (Exception e) { return ""; }
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

    // ============ 海外地名檢測 ============
    private static final Set<String> FOREIGN_LOCATIONS = Set.of(
        // 日本
        "日本", "東京", "大阪", "京都", "北海道", "沖繩", "名古屋", "福岡", "橫濱", "神戶",
        "奈良", "札幌", "鹿兒島", "廣島", "仙台", "金澤", "箱根", "富士山", "淺草", "銀座",
        "新宿", "涉谷", "原宿", "秋葉原", "上野", "池袋", "六本木", "表參道", "青山",
        // 韓國
        "韓國", "首爾", "釜山", "濟州", "仁川", "大邱", "明洞", "弘大", "江南", "梨泰院",
        "東大門", "南大門", "景福宮", "北村", "三清洞", "狎鷗亭", "聖水洞",
        // 中國大陸
        "中國", "大陸", "北京", "上海", "廣州", "深圳", "杭州", "成都", "重慶", "西安", 
        "南京", "蘇州", "武漢", "天津", "青島", "廈門", "昆明", "三亞", "海南",
        // 港澳
        "香港", "澳門", "九龍", "旺角", "尖沙咀", "銅鑼灣", "中環",
        // 東南亞
        "泰國", "曼谷", "清邁", "普吉", "芭達雅", "新加坡", "馬來西亞", "吉隆坡", "檳城",
        "越南", "河內", "胡志明", "峴港", "印尼", "峇里島", "巴里", "雅加達", "菲律賓", "馬尼拉",
        "長灘島", "宿霧", "柬埔寨", "吳哥窟", "寮國", "緬甸",
        // 歐美
        "美國", "紐約", "洛杉磯", "舊金山", "拉斯維加斯", "邁阿密", "西雅圖", "芝加哥", "波士頓",
        "英國", "倫敦", "法國", "巴黎", "德國", "柏林", "慕尼黑", "義大利", "羅馬", "米蘭",
        "威尼斯", "佛羅倫斯", "西班牙", "馬德里", "巴塞隆納", "荷蘭", "阿姆斯特丹",
        "瑞士", "蘇黎世", "奧地利", "維也納", "捷克", "布拉格", "希臘", "雅典", "土耳其", "伊斯坦堡",
        // 其他
        "澳洲", "雪梨", "墨爾本", "紐西蘭", "奧克蘭", "杜拜", "埃及", "摩洛哥", "肯亞", "南非"
    );
    
    /**
     * 檢查標題是否包含海外地名
     */
    private static boolean containsForeignLocation(String title) {
        if (title == null || title.isEmpty()) return false;
        String lower = title.toLowerCase();
        
        // 先檢查是否有台灣地名（有的話不算海外）
        if (hasTaiwanLocation(title)) return false;
        
        for (String loc : FOREIGN_LOCATIONS) {
            if (lower.contains(loc.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 從文字中偵測年份（回傳最顯著的年份，0 表示沒有）
     */
    private static int detectYearInText(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        // 優先找標題格式的年份（如「2024全台活動」「2025年市集」）
        Pattern titleYearPattern = Pattern.compile("(20[2-3][0-9])(?:年|全台|精選|最新|活動|整理|懶人包|市集|展覽|演唱會)");
        Matcher m1 = titleYearPattern.matcher(text);
        if (m1.find()) {
            return Integer.parseInt(m1.group(1));
        }
        
        // 再找一般年份格式
        Pattern yearPattern = Pattern.compile("(20[2-3][0-9])(?:[./年\\-]|\\s)");
        Matcher m2 = yearPattern.matcher(text);
        if (m2.find()) {
            return Integer.parseInt(m2.group(1));
        }
        
        // 最後找獨立年份
        Pattern standaloneYear = Pattern.compile("\\b(20[2-3][0-9])\\b");
        Matcher m3 = standaloneYear.matcher(text);
        if (m3.find()) {
            return Integer.parseInt(m3.group(1));
        }
        
        return 0;
    }

    /**
     * 檢查是否包含台灣相關地名 (Added missing method)
     */
    private static boolean hasTaiwanLocation(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("台灣") || lower.contains("taiwan") || 
               lower.contains("台北") || lower.contains("新北") || 
               lower.contains("桃園") || lower.contains("台中") || 
               lower.contains("台南") || lower.contains("高雄") || 
               lower.contains("基隆") || lower.contains("新竹") || 
               lower.contains("嘉義") || lower.contains("苗栗") || 
               lower.contains("彰化") || lower.contains("南投") || 
               lower.contains("雲林") || lower.contains("屏東") || 
               lower.contains("宜蘭") || lower.contains("花蓮") || 
               lower.contains("台東") || lower.contains("澎湖") ||
               lower.contains("金門") || lower.contains("馬祖");
    }

    public static Tree getLastSearchTree() { return lastSearchTree; }
    public static List<PageNode> getLastResults() { return lastResults; }
}