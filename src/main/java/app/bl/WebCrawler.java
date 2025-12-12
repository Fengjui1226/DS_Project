package app.bl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * WebCrawler v3.0 - 部落格與媒體網站特化版
 * * 針對 "Aggregator Strategy" (代理戰術) 進行優化：
 * 1. [瀏覽器偽裝] 完善 Header 模擬，繞過媒體網站(PopDaily/Vogue)的基礎防禦。
 * 2. [智慧提取] 優先識別 <article>, <main> 區塊，過濾廣告雜訊。
 * 3. [圖片文字] 強化 alt/title 屬性提取，彌補無法爬取 IG 圖片的缺憾。
 */
public class WebCrawler {
    
    // ============ 設定 ============
    private static final int CONNECT_TIMEOUT_SECONDS = 3;     // 放寬連線時間
    private static final int REQUEST_TIMEOUT_SECONDS = 4;     // 放寬讀取時間
    private static final int MAX_SUBPAGES = 5;                // 每個站爬 5 頁
    private static final int MAX_CONTENT_LENGTH = 150000;     // 提升到 150KB (媒體網站圖多碼雜)
    private static final int CRAWL_DELAY_MS = 200;            // 禮貌性延遲
    private static final int MAX_SITE_TIME_MS = 5000;         // 每站最多 5 秒
    
    // 共享 HttpClient
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_2)
        .build();
    
    // 更真實的 User-Agent
    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    // IG 專用：模擬手機瀏覽器（IG 對手機版限制較少）
    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    // 跳過的資源
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".svg", ".webp", ".ico",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".mp3", ".mp4", ".avi", ".mov", ".zip", ".rar", ".css", ".js", ".json"
    );
    
    // 跳過的無效路徑
    private static final Set<String> SKIP_PATHS = Set.of(
        "/login", "/signup", "/register", "/auth", "/cart", "/checkout", 
        "/account", "/admin", "/api/", "/static/", "/assets/", 
        "/privacy", "/terms", "/policy", "/contact", "/about"
    );
    
    // 優先爬取的路徑 (針對活動網站)
    private static final Set<String> PRIORITY_PATHS = Set.of(
        "/event", "/activity", "/exhibition", "/show", "/news", "/article", 
        "/post", "/topic", "/life", "/travel", "/spot" // PopDaily/Vogue 常用的路徑
    );

    public static CrawlResult crawl(String url) {
        // ★ IG 特殊處理
        if (isInstagramUrl(url)) {
            return crawlInstagram(url);
        }
        return crawlWithRetry(url, 1);
    }
    
    /**
     * 判斷是否為 Instagram URL
     */
    private static boolean isInstagramUrl(String url) {
        return url != null && (url.contains("instagram.com") || url.contains("instagr.am"));
    }
    
    /**
     * IG 專用爬蟲 - 嘗試從 meta tags 提取資訊
     * 
     * IG 頁面雖然主要靠 JS 渲染，但 meta tags (og:title, og:description) 
     * 通常會包含貼文的基本資訊，這是不需要登入就能取得的。
     */
    private static CrawlResult crawlInstagram(String url) {
        CrawlResult result = new CrawlResult(url);
        
        try {
            // 使用手機版 User-Agent，IG 對手機版限制較少
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            int status = response.statusCode();
            
            // IG 可能回傳 302 重定向到登入頁
            if (status == 302 || status == 301) {
                result.setError("IG Redirect (需登入)");
                return result;
            }
            
            if (status != 200) {
                result.setError("HTTP " + status);
                return result;
            }
            
            String html = response.body();
            
            // 檢查是否被重定向到登入頁
            if (html.contains("loginForm") || html.contains("Login • Instagram")) {
                result.setError("IG 需要登入");
                return result;
            }
            
            // 從 meta tags 提取資訊
            String title = extractMetaContent(html, "og:title");
            String description = extractMetaContent(html, "og:description");
            String siteName = extractMetaContent(html, "og:site_name");
            
            // 嘗試從 JSON-LD 提取更多資訊
            String jsonLdContent = extractJsonLdContent(html);
            
            // 組合內容
            StringBuilder content = new StringBuilder();
            if (title != null && !title.isEmpty()) {
                content.append(title).append(" ");
            }
            if (description != null && !description.isEmpty()) {
                content.append(description).append(" ");
            }
            if (jsonLdContent != null && !jsonLdContent.isEmpty()) {
                content.append(jsonLdContent);
            }
            
            String finalContent = content.toString().trim();
            
            if (finalContent.isEmpty()) {
                result.setError("IG 無法提取內容");
                return result;
            }
            
            result.setSuccess(true);
            result.setTitle(title != null ? title : "Instagram");
            result.setTextContent(finalContent);
            result.setHtml(html.length() > 50000 ? html.substring(0, 50000) : html);
            
            return result;
            
        } catch (Exception e) {
            result.setError("IG Error: " + e.getClass().getSimpleName());
            return result;
        }
    }
    
    /**
     * 從 HTML 提取 meta tag 內容
     */
    private static String extractMetaContent(String html, String property) {
        // 嘗試 property 格式: <meta property="og:title" content="...">
        Pattern p1 = Pattern.compile(
            "<meta[^>]+property=[\"']" + Pattern.quote(property) + "[\"'][^>]+content=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return cleanText(m1.group(1));
        
        // 嘗試反向格式: <meta content="..." property="og:title">
        Pattern p2 = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']" + Pattern.quote(property) + "[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return cleanText(m2.group(1));
        
        // 嘗試 name 格式: <meta name="description" content="...">
        Pattern p3 = Pattern.compile(
            "<meta[^>]+name=[\"']" + Pattern.quote(property) + "[\"'][^>]+content=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m3 = p3.matcher(html);
        if (m3.find()) return cleanText(m3.group(1));
        
        return null;
    }
    
    /**
     * 嘗試從 JSON-LD 結構化資料提取內容
     */
    private static String extractJsonLdContent(String html) {
        Pattern p = Pattern.compile(
            "<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>([^<]+)</script>",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(html);
        
        StringBuilder content = new StringBuilder();
        while (m.find()) {
            String json = m.group(1);
            // 簡單提取 "caption", "description", "name" 等欄位
            Pattern fieldPattern = Pattern.compile("\"(caption|description|name|headline)\"\\s*:\\s*\"([^\"]+)\"");
            Matcher fm = fieldPattern.matcher(json);
            while (fm.find()) {
                content.append(fm.group(2)).append(" ");
            }
        }
        return content.toString().trim();
    }
    
    private static CrawlResult crawlWithRetry(String url, int maxRetries) {
        CrawlResult result = new CrawlResult(url);
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (!isValidUrl(url) || shouldSkipUrl(url)) {
                    result.setError("Skipped URL");
                    return result;
                }
                
                // 模擬真實瀏覽器請求
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", CHROME_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", "https://www.google.com/") // 偽裝來自 Google
                    .header("DNT", "1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();
                
                HttpResponse<String> response = HTTP_CLIENT.send(request, 
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    if (attempt < maxRetries) {
                        Thread.sleep(1000);
                        continue;
                    }
                    result.setError("HTTP " + status);
                    return result;
                }
                
                if (status != 200) {
                    result.setError("HTTP " + status);
                    return result;
                }
                
                String html = response.body();
                if (html.length() > MAX_CONTENT_LENGTH) {
                    html = html.substring(0, MAX_CONTENT_LENGTH);
                }
                
                // 解析
                result.setSuccess(true);
                result.setHtml(html);
                result.setTitle(extractTitle(html));
                // ★ 重點：使用智慧提取邏輯
                result.setTextContent(extractSmartContent(html)); 
                result.setLinks(extractLinks(html, url));
                
                // ★ 新增：嘗試解析活動列表
                if (isEventListPage(url, html)) {
                    List<EventItem> eventItems = extractEventItems(html, url);
                    result.setEventItems(eventItems);
                    if (!eventItems.isEmpty()) {
                        System.out.println("[WebCrawler] 發現活動列表頁，提取 " + eventItems.size() + " 個活動");
                    }
                }
                
                return result;
                
            } catch (Exception e) {
                if (attempt < maxRetries) continue;
                result.setError(e.getClass().getSimpleName());
            }
        }
        return result;
    }

    // ============ HTML 解析優化 ============
    
    /**
     * 智慧內容提取：優先抓取文章核心區塊
     */
    private static String extractSmartContent(String html) {
        // 1. 預處理：移除 Script, Style, Comments
        String clean = html.replaceAll("(?is)<script.*?>.*?</script>", " ")
                           .replaceAll("(?is)<style.*?>.*?</style>", " ")
                           .replaceAll("(?is)<!--.*?-->", " ");

        // 2. 嘗試提取核心區塊 (<article>, <main>, <div class="content/post">)
        // 這是針對 PopDaily, Vogue 等部落格型網站的優化
        Pattern articlePattern = Pattern.compile("(?is)<(article|main)[^>]*>(.*?)</\\1>");
        Matcher m = articlePattern.matcher(clean);
        
        StringBuilder coreContent = new StringBuilder();
        if (m.find()) {
            coreContent.append(m.group(2)); // 找到核心區塊
        } else {
            // 如果找不到語意標籤，嘗試找 class 名稱
            Pattern divPattern = Pattern.compile("(?is)<div[^>]*(class=[\"'][^\"']*(content|post|article|detail)[^\"']*[\"'])[^>]*>(.*?)</div>");
            Matcher m2 = divPattern.matcher(clean);
            if (m2.find()) {
                coreContent.append(m2.group(3));
            } else {
                coreContent.append(clean); // 降級：使用全文
            }
        }
        
        String text = coreContent.toString();

        // 3. ★ 圖片文字提取：很多懶人包資訊都在圖片 alt 或 title 裡
        // 提取 <img alt="..."> 和 <img title="...">
        Pattern imgPattern = Pattern.compile("(?is)<img[^>]+(alt|title)=[\"']([^\"']+)[\"']");
        Matcher imgMatcher = imgPattern.matcher(clean); // 這裡用 clean 因為圖片可能在 article 外
        StringBuilder imgText = new StringBuilder();
        while (imgMatcher.find()) {
            String alt = imgMatcher.group(2).trim();
            if (alt.length() > 2 && !alt.toLowerCase().contains("logo")) {
                imgText.append(alt).append(" ");
            }
        }

        // 4. 清理標籤
        text = text.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("&[a-zA-Z0-9#]+;", " ");
        
        // 5. 合併本文與圖片文字
        return (text + " " + imgText.toString()).replaceAll("\\s+", " ").trim();
    }

    private static String extractTitle(String html) {
        Pattern p1 = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return cleanText(m1.group(1));
        
        // 增加 og:title 支援
        Pattern p2 = Pattern.compile("property=[\"']og:title[\"']\\s*content=[\"']([^\"']+)[\"']");
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return cleanText(m2.group(1));
        
        return "";
    }
    
    private static List<String> extractLinks(String html, String baseUrl) {
        List<String> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String domain = extractDomain(baseUrl);
        
        Pattern pattern = Pattern.compile("<a[^>]+href=[\"']([^\"'#]+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        
        while (matcher.find() && links.size() < 60) {
            String href = matcher.group(1).trim();
            String absUrl = resolveUrl(href, baseUrl);
            
            if (absUrl != null && !seen.contains(absUrl)) {
                // 簡單過濾：只抓同網域
                if (isSameDomain(absUrl, domain)) {
                    seen.add(absUrl);
                    links.add(absUrl);
                }
            }
        }
        return links;
    }

    // ============ 工具方法 ============

    private static boolean isValidUrl(String url) {
        return url != null && url.startsWith("http");
    }

    private static boolean shouldSkipUrl(String url) {
        String lower = url.toLowerCase();
        for (String ext : SKIP_EXTENSIONS) if (lower.endsWith(ext)) return true;
        for (String path : SKIP_PATHS) if (lower.contains(path)) return true;
        return url.length() > 300; // 過長網址通常是追蹤碼
    }

    private static String resolveUrl(String href, String baseUrl) {
        try {
            return URI.create(baseUrl).resolve(href).toString();
        } catch (Exception e) { return null; }
    }
    
    private static String extractDomain(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) { return ""; }
    }
    
    private static boolean isSameDomain(String url, String domain) {
        String d = extractDomain(url);
        return d != null && (d.equals(domain) || d.endsWith("." + domain));
    }

    private static String cleanText(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * 判斷是否為活動列表頁
     */
    private static boolean isEventListPage(String url, String html) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        
        // URL 特徵判斷
        boolean urlMatch = 
            lower.contains("category") ||
            lower.contains("list") ||
            lower.contains("search") ||
            lower.contains("type=") ||
            lower.contains("演唱會") ||
            lower.contains("展覽") ||
            lower.contains("活動") ||
            lower.contains("concert") ||
            lower.contains("events") ||
            (lower.contains("ticket") && lower.contains(".aspx"));
        
        // 內容特徵判斷：包含多個活動項目
        if (html != null) {
            // 計算活動相關連結數量
            int eventLinkCount = 0;
            Pattern p = Pattern.compile("href=[\"'][^\"']*(?:event|activity|show|UTK)[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(html);
            while (m.find()) eventLinkCount++;
            
            if (eventLinkCount >= 3) return true;
        }
        
        return urlMatch;
    }
    
    /**
     * ★ 解析活動列表頁，提取每個活動項目
     * 適用於 ticket.com.tw, accupass, kktix 等售票網站的列表頁
     */
    public static List<EventItem> extractEventItems(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        if (html == null || html.isEmpty()) return items;
        
        String domain = extractDomain(baseUrl).toLowerCase();
        
        // 根據不同網站使用不同的解析策略
        if (domain.contains("ticket.com.tw") || domain.contains("era.com")) {
            items = parseTicketComTw(html, baseUrl);
        } else if (domain.contains("accupass")) {
            items = parseAccupass(html, baseUrl);
        } else if (domain.contains("kktix")) {
            items = parseKktix(html, baseUrl);
        } else if (domain.contains("opentix")) {
            items = parseOpentix(html, baseUrl);
        } else if (domain.contains("tixcraft")) {
            items = parseTixcraft(html, baseUrl);
        } else {
            // 通用解析：找所有包含活動資訊的連結
            items = parseGenericEventList(html, baseUrl);
        }
        
        return items;
    }
    
    /**
     * 年代售票 ticket.com.tw 解析
     */
    private static List<EventItem> parseTicketComTw(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        
        // 年代售票的活動項目通常在 <a> 標籤裡，包含活動圖片和標題
        // 格式: <a href="..."><img alt="活動名稱">...</a>
        // 或者: <div class="event">...<a href="...">活動名稱</a>...</div>
        
        // 方法1: 找包含日期的連結
        Pattern p1 = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*(?:UTK|event|activity|show)[^\"']*)[\"'][^>]*>.*?" +
            "(?:<img[^>]+alt=[\"']([^\"']+)[\"']|([^<]{5,50}))" +
            ".*?</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        // 方法2: 找活動卡片區塊
        Pattern cardPattern = Pattern.compile(
            "(?:<div[^>]*class=[\"'][^\"']*(?:event|item|card|product)[^\"']*[\"'][^>]*>)" +
            "(.*?)" +
            "(?:</div>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher cardMatcher = cardPattern.matcher(html);
        while (cardMatcher.find() && items.size() < 20) {
            String card = cardMatcher.group(1);
            
            // 從卡片中提取連結
            Pattern linkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
            Matcher linkMatcher = linkPattern.matcher(card);
            String url = linkMatcher.find() ? resolveUrl(linkMatcher.group(1), baseUrl) : null;
            
            // 從卡片中提取標題
            String title = extractTitleFromCard(card);
            
            // 從卡片中提取日期
            String date = extractDateFromCard(card);
            
            if (url != null && title != null && !title.isEmpty() && title.length() > 3) {
                items.add(new EventItem(title, url, date, ""));
            }
        }
        
        // 如果上面沒找到，用更寬鬆的方式
        if (items.isEmpty()) {
            Pattern imgLinkPattern = Pattern.compile(
                "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>\\s*<img[^>]+alt=[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE
            );
            Matcher m = imgLinkPattern.matcher(html);
            while (m.find() && items.size() < 20) {
                String url = resolveUrl(m.group(1), baseUrl);
                String title = cleanText(m.group(2));
                if (url != null && !title.isEmpty() && title.length() > 3 && 
                    !title.toLowerCase().contains("logo") && !title.toLowerCase().contains("banner")) {
                    items.add(new EventItem(title, url, "", ""));
                }
            }
        }
        
        return items;
    }
    
    /**
     * Accupass 解析
     */
    private static List<EventItem> parseAccupass(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        
        // Accupass 活動連結格式: /event/xxxxx
        Pattern p = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*?/event/[^\"']+)[\"'][^>]*>.*?" +
            "(?:<[^>]*>)*([^<]{5,100})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<>();
        
        while (m.find() && items.size() < 20) {
            String url = resolveUrl(m.group(1), baseUrl);
            String title = cleanText(m.group(2).replaceAll("<[^>]+>", ""));
            
            if (url != null && !seen.contains(url) && !title.isEmpty() && title.length() > 3) {
                seen.add(url);
                items.add(new EventItem(title, url, "", ""));
            }
        }
        
        return items;
    }
    
    /**
     * KKTIX 解析
     */
    private static List<EventItem> parseKktix(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        
        // KKTIX 活動連結格式: /events/xxxxx 或 kktix.com/events/xxx
        Pattern p = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*?/events?/[^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<>();
        
        while (m.find() && items.size() < 20) {
            String url = resolveUrl(m.group(1), baseUrl);
            if (url != null && !seen.contains(url)) {
                seen.add(url);
                // 嘗試從附近提取標題
                int pos = m.end();
                String nearby = html.substring(pos, Math.min(pos + 200, html.length()));
                String title = extractFirstText(nearby);
                if (!title.isEmpty()) {
                    items.add(new EventItem(title, url, "", ""));
                }
            }
        }
        
        return items;
    }
    
    /**
     * Opentix 解析
     */
    private static List<EventItem> parseOpentix(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        
        Pattern p = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*?/event(?:s)?/[^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<>();
        
        while (m.find() && items.size() < 20) {
            String url = resolveUrl(m.group(1), baseUrl);
            if (url != null && !seen.contains(url)) {
                seen.add(url);
                int pos = m.end();
                String nearby = html.substring(pos, Math.min(pos + 200, html.length()));
                String title = extractFirstText(nearby);
                if (!title.isEmpty()) {
                    items.add(new EventItem(title, url, "", ""));
                }
            }
        }
        
        return items;
    }
    
    /**
     * Tixcraft 解析
     */
    private static List<EventItem> parseTixcraft(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        
        Pattern p = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*?/activity/detail/[^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<>();
        
        while (m.find() && items.size() < 20) {
            String url = resolveUrl(m.group(1), baseUrl);
            if (url != null && !seen.contains(url)) {
                seen.add(url);
                int pos = m.end();
                String nearby = html.substring(pos, Math.min(pos + 200, html.length()));
                String title = extractFirstText(nearby);
                if (!title.isEmpty()) {
                    items.add(new EventItem(title, url, "", ""));
                }
            }
        }
        
        return items;
    }
    
    /**
     * 通用活動列表解析
     */
    private static List<EventItem> parseGenericEventList(String html, String baseUrl) {
        List<EventItem> items = new ArrayList<>();
        
        // 找所有可能是活動的連結（包含 event, activity, show, concert 等關鍵字）
        Pattern p = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*?(?:event|activity|show|concert|ticket|演唱|展覽|活動)[^\"']*)[\"'][^>]*>" +
            "([^<]*(?:<[^>]+>[^<]*)*)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<>();
        
        while (m.find() && items.size() < 20) {
            String url = resolveUrl(m.group(1), baseUrl);
            String content = m.group(2).replaceAll("<[^>]+>", " ").trim();
            String title = cleanText(content);
            
            if (url != null && !seen.contains(url) && !title.isEmpty() && 
                title.length() > 5 && title.length() < 100) {
                seen.add(url);
                items.add(new EventItem(title, url, "", ""));
            }
        }
        
        return items;
    }
    
    /**
     * 從卡片 HTML 提取標題
     */
    private static String extractTitleFromCard(String card) {
        // 優先從 <h1>-<h6> 提取
        Pattern hPattern = Pattern.compile("<h[1-6][^>]*>([^<]+)</h[1-6]>", Pattern.CASE_INSENSITIVE);
        Matcher hm = hPattern.matcher(card);
        if (hm.find()) return cleanText(hm.group(1));
        
        // 從 img alt 提取
        Pattern imgPattern = Pattern.compile("<img[^>]+alt=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher im = imgPattern.matcher(card);
        if (im.find()) return cleanText(im.group(1));
        
        // 從 title 屬性提取
        Pattern titlePattern = Pattern.compile("title=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher tm = titlePattern.matcher(card);
        if (tm.find()) return cleanText(tm.group(1));
        
        // 從第一段文字提取
        return extractFirstText(card);
    }
    
    /**
     * 從卡片 HTML 提取日期
     */
    private static String extractDateFromCard(String card) {
        // 常見日期格式
        Pattern datePattern = Pattern.compile(
            "(\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2})" +  // 2025/01/17
            "|(\\d{1,2}[/\\-.]\\d{1,2})" +               // 01/17
            "|(\\d{1,2}月\\d{1,2}日?)",                   // 1月17日
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = datePattern.matcher(card);
        if (m.find()) {
            return m.group();
        }
        return "";
    }
    
    /**
     * 提取第一段有意義的文字
     */
    private static String extractFirstText(String html) {
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        // 取前 100 字元
        if (text.length() > 100) {
            text = text.substring(0, 100);
            int lastSpace = text.lastIndexOf(' ');
            if (lastSpace > 50) text = text.substring(0, lastSpace);
        }
        return text;
    }
    
    // ============ 內部類別 ============
    
    /**
     * 活動項目（從列表頁提取）
     */
    public static class EventItem {
        public final String title;
        public final String url;
        public final String date;
        public final String snippet;
        
        public EventItem(String title, String url, String date, String snippet) {
            this.title = title != null ? title : "";
            this.url = url != null ? url : "";
            this.date = date != null ? date : "";
            this.snippet = snippet != null ? snippet : "";
        }
        
        @Override
        public String toString() {
            return title + " | " + date + " -> " + url;
        }
    }
    
    public static class CrawlResult {
        private final String url;
        private String title = "";
        private String textContent = "";
        private String html = "";
        private List<String> links = new ArrayList<>();
        private List<EventItem> eventItems = new ArrayList<>();  // ★ 新增：活動項目列表
        private boolean success = false;
        private String error = null;
        public CrawlResult(String url) { this.url = url; }
        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title != null ? title : ""; }
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent != null ? textContent : ""; }
        public String getHtml() { return html; }
        public void setHtml(String html) { this.html = html != null ? html : ""; }
        public List<String> getLinks() { return links; }
        public void setLinks(List<String> links) { this.links = links != null ? links : new ArrayList<>(); }
        public List<EventItem> getEventItems() { return eventItems; }
        public void setEventItems(List<EventItem> items) { this.eventItems = items != null ? items : new ArrayList<>(); }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setError(String error) { this.error = error; this.success = false; }
        public String getError() { return error; }
    }
    
    public static class SiteResult {
        private final String url;
        public SiteResult(String url) { this.url = url; }
        public void setMainPage(CrawlResult r) {}
        public void addSubPage(CrawlResult r) {}
    }
}