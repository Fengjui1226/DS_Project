package app.bl;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

/**
 * WebCrawler - 網頁爬蟲（修復版）
 * 
 * 功能：
 * 1. 爬取網頁 HTML 內容
 * 2. 提取標題、文字內容
 * 3. 提取所有子連結 <a href>
 * 4. 支援同網域子網頁爬取
 */
public class WebCrawler {
    
    // 設定 - 更嚴格的超時
    private static final int TIMEOUT_SECONDS = 5;           // 請求超時（縮短到 5 秒）
    private static final int MAX_SUBPAGES = 5;              // 每個網站最多爬 5 個子網頁
    private static final int MAX_CONTENT_LENGTH = 200000;   // 最大內容長度 (200KB)
    private static final int CRAWL_DELAY_MS = 200;          // 爬取間隔
    private static final int MAX_TOTAL_TIME_MS = 8000;      // 每個網站最多花 8 秒
    
    // HTTP Client（重複使用）- 更短的超時
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    
    // User-Agent（模擬瀏覽器）
    private static final String USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // 應該跳過的 URL 模式
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".svg", ".webp",  // 圖片
        ".pdf", ".doc", ".docx", ".xls", ".xlsx",          // 文件
        ".mp3", ".mp4", ".avi", ".mov",                    // 媒體
        ".zip", ".rar", ".7z",                             // 壓縮檔
        ".css", ".js", ".json", ".xml"                     // 資源檔
    );
    
    // 應該跳過的路徑
    private static final Set<String> SKIP_PATHS = Set.of(
        "/login", "/signup", "/register", "/logout",
        "/cart", "/checkout", "/account", "/admin",
        "/api/", "/static/", "/assets/", "/images/",
        "/privacy", "/terms", "/about", "/contact"
    );

    /**
     * 爬取單一網頁
     * 
     * @param url 網頁 URL
     * @return CrawlResult 爬取結果
     */
    public static CrawlResult crawl(String url) {
        CrawlResult result = new CrawlResult(url);
        
        try {
            // 驗證 URL
            if (!isValidUrl(url)) {
                result.setError("Invalid URL");
                return result;
            }
            
            // 發送請求
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            // 檢查狀態碼
            if (response.statusCode() != 200) {
                result.setError("HTTP " + response.statusCode());
                return result;
            }
            
            String html = response.body();
            
            // 限制大小
            if (html.length() > MAX_CONTENT_LENGTH) {
                html = html.substring(0, MAX_CONTENT_LENGTH);
            }
            
            // 解析 HTML
            result.setHtml(html);
            result.setTitle(extractTitle(html));
            result.setTextContent(extractTextContent(html));
            result.setLinks(extractLinks(html, url));
            result.setSuccess(true);
            
            System.out.println("[Crawler] ✓ " + url + " (" + result.getLinks().size() + " links)");
            
        } catch (Exception e) {
            result.setError(e.getMessage());
            System.out.println("[Crawler] ✗ " + url + " - " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 爬取網站（包含子網頁）- 有時間限制
     * 
     * @param mainUrl 主網頁 URL
     * @return SiteResult 網站爬取結果（含子網頁）
     */
    public static SiteResult crawlSite(String mainUrl) {
        SiteResult site = new SiteResult(mainUrl);
        long startTime = System.currentTimeMillis();
        
        // 1. 爬取主網頁
        CrawlResult mainPage = crawl(mainUrl);
        site.setMainPage(mainPage);
        
        if (!mainPage.isSuccess()) {
            return site;
        }
        
        // 檢查是否還有時間
        if (System.currentTimeMillis() - startTime > MAX_TOTAL_TIME_MS) {
            System.out.println("[Crawler] Time limit reached for: " + mainUrl);
            return site;
        }
        
        // 2. 取得同網域的子連結
        String domain = extractDomain(mainUrl);
        List<String> subLinks = new ArrayList<>();
        
        for (String link : mainPage.getLinks()) {
            // 只爬同網域
            if (isSameDomain(link, domain) && !link.equals(mainUrl)) {
                // 跳過不需要的頁面
                if (shouldSkipUrl(link)) continue;
                
                subLinks.add(link);
                if (subLinks.size() >= MAX_SUBPAGES) break;
            }
        }
        
        // 3. 爬取子網頁（有時間限制）
        for (String subLink : subLinks) {
            // 檢查是否還有時間
            if (System.currentTimeMillis() - startTime > MAX_TOTAL_TIME_MS) {
                System.out.println("[Crawler] Time limit, stopping subpage crawl");
                break;
            }
            
            try {
                Thread.sleep(CRAWL_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            CrawlResult subPage = crawl(subLink);
            if (subPage.isSuccess()) {
                site.addSubPage(subPage);
            }
        }
        
        System.out.println("[Crawler] Site done: " + domain + 
            " (1 main + " + site.getSubPages().size() + " sub) in " + 
            (System.currentTimeMillis() - startTime) + "ms");
        
        return site;
    }
    
    /**
     * 批量爬取多個網站（並行）
     */
    public static List<SiteResult> crawlSites(List<String> urls) {
        List<SiteResult> results = new ArrayList<>();
        
        // 使用執行緒池並行爬取
        ExecutorService executor = Executors.newFixedThreadPool(3); // 3 個並行
        List<Future<SiteResult>> futures = new ArrayList<>();
        
        for (String url : urls) {
            futures.add(executor.submit(() -> crawlSite(url)));
        }
        
        for (Future<SiteResult> future : futures) {
            try {
                SiteResult result = future.get(30, TimeUnit.SECONDS);
                results.add(result);
            } catch (Exception e) {
                System.out.println("[Crawler] Site timeout or error");
            }
        }
        
        executor.shutdown();
        return results;
    }

    // ============ HTML 解析方法 ============
    
    /**
     * 提取標題 <title>
     */
    private static String extractTitle(String html) {
        Pattern pattern = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }
        
        // 嘗試 <h1>
        pattern = Pattern.compile("<h1[^>]*>([^<]+)</h1>", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(html);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }
        
        return "";
    }
    
    /**
     * 提取文字內容（移除 HTML 標籤）
     */
    private static String extractTextContent(String html) {
        // 移除 script 和 style
        String text = html.replaceAll("(?i)<script[^>]*>[\\s\\S]*?</script>", "");
        text = text.replaceAll("(?i)<style[^>]*>[\\s\\S]*?</style>", "");
        
        // 移除 HTML 標籤
        text = text.replaceAll("<[^>]+>", " ");
        
        // 清理空白
        text = text.replaceAll("\\s+", " ").trim();
        
        // 限制長度
        if (text.length() > 5000) {
            text = text.substring(0, 5000);
        }
        
        return text;
    }
    
    /**
     * 提取所有連結 <a href>
     */
    private static List<String> extractLinks(String html, String baseUrl) {
        List<String> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        Pattern pattern = Pattern.compile("<a[^>]+href=[\"']([^\"'#]+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            
            // 轉換相對路徑為絕對路徑
            String absoluteUrl = resolveUrl(href, baseUrl);
            
            if (absoluteUrl != null && !seen.contains(absoluteUrl)) {
                seen.add(absoluteUrl);
                links.add(absoluteUrl);
            }
        }
        
        return links;
    }
    
    // ============ URL 工具方法 ============
    
    private static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }
    
    private static boolean isSameDomain(String url, String domain) {
        String urlDomain = extractDomain(url);
        if (urlDomain == null || domain == null) return false;
        
        // 處理 www 前綴
        urlDomain = urlDomain.replaceFirst("^www\\.", "");
        domain = domain.replaceFirst("^www\\.", "");
        
        return urlDomain.equals(domain) || urlDomain.endsWith("." + domain);
    }
    
    private static boolean shouldSkipUrl(String url) {
        String lower = url.toLowerCase();
        
        // 跳過特定副檔名
        for (String ext : SKIP_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        
        // 跳過特定路徑
        for (String path : SKIP_PATHS) {
            if (lower.contains(path)) return true;
        }
        
        return false;
    }
    
    private static String resolveUrl(String href, String baseUrl) {
        try {
            if (href.startsWith("http://") || href.startsWith("https://")) {
                return href;
            }
            
            URI base = URI.create(baseUrl);
            
            if (href.startsWith("//")) {
                return base.getScheme() + ":" + href;
            }
            
            if (href.startsWith("/")) {
                return base.getScheme() + "://" + base.getHost() + href;
            }
            
            // 相對路徑
            String basePath = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
            return basePath + href;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }
    
    // ============ 資料結構 ============
    
    /**
     * 單一網頁爬取結果
     */
    public static class CrawlResult {
        private String url;
        private String title = "";
        private String textContent = "";
        private String html = "";
        private List<String> links = new ArrayList<>();
        private boolean success = false;
        private String error = null;
        
        public CrawlResult(String url) { this.url = url; }
        
        // Getters & Setters
        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        public String getHtml() { return html; }
        public void setHtml(String html) { this.html = html; }
        public List<String> getLinks() { return links; }
        public void setLinks(List<String> links) { this.links = links; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; this.success = false; }
    }
    
    /**
     * 網站爬取結果（含子網頁）
     */
    public static class SiteResult {
        private String url;
        private CrawlResult mainPage;
        private List<CrawlResult> subPages = new ArrayList<>();
        
        public SiteResult(String url) { this.url = url; }
        
        public String getUrl() { return url; }
        public CrawlResult getMainPage() { return mainPage; }
        public void setMainPage(CrawlResult mainPage) { this.mainPage = mainPage; }
        public List<CrawlResult> getSubPages() { return subPages; }
        public void addSubPage(CrawlResult page) { this.subPages.add(page); }
        
        public int getTotalPages() {
            return 1 + subPages.size();
        }
        
        public String getDomain() {
            return extractDomain(url);
        }
    }
}