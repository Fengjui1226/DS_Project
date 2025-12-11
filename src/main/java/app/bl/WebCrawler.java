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
        return crawlWithRetry(url, 1);
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
    
    // ============ 內部類別 (保持不變) ============
    // (為了節省篇幅，CrawlResult 和 SiteResult 結構保持原樣，請直接使用您原本的定義)
    // 這裡只需要確保上面的 crawlWithRetry 方法被替換即可
    
    public static class CrawlResult {
        private final String url;
        private String title = "";
        private String textContent = "";
        private String html = "";
        private List<String> links = new ArrayList<>();
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
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setError(String error) { this.error = error; this.success = false; }
        public String getError() { return error; }
    }
    
    public static class SiteResult { // 保持原樣
        private final String url;
        public SiteResult(String url) { this.url = url; }
        public void setMainPage(CrawlResult r) {}
        public void addSubPage(CrawlResult r) {}
    }
}