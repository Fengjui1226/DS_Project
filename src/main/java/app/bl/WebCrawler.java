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
 * WebCrawler v2.0 - 高效能爬蟲
 * 
 * 優化重點：
 * 1. 共享 HttpClient（連接池復用）
 * 2. 更智慧的子網頁選擇
 * 3. 優先爬取活動相關頁面
 * 4. 更嚴格的超時控制
 */
public class WebCrawler {
    
    // ============ 設定 ============
    private static final int CONNECT_TIMEOUT_SECONDS = 2;     // ★ 連線超時 2 秒
    private static final int REQUEST_TIMEOUT_SECONDS = 3;     // ★ 請求超時 3 秒
    private static final int MAX_SUBPAGES = 6;                // 最多爬 6 個子網頁
    private static final int MAX_CONTENT_LENGTH = 100000;     // ★ 100KB（減少）
    private static final int CRAWL_DELAY_MS = 50;             // ★ 爬取間隔（減少）
    private static final int MAX_SITE_TIME_MS = 4000;         // ★ 每站最多 4 秒
    
    // 共享 HttpClient（效能關鍵！）
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_2)  // 使用 HTTP/2
        .build();
    
    // User-Agent 輪替（避免被封鎖）
    private static final List<String> USER_AGENTS = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    );
    private static int userAgentIndex = 0;
    
    // 跳過的副檔名
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".svg", ".webp", ".ico",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".mp3", ".mp4", ".avi", ".mov", ".wav", ".flac",
        ".zip", ".rar", ".7z", ".gz", ".tar",
        ".css", ".js", ".json", ".xml", ".rss"
    );
    
    // 跳過的路徑
    private static final Set<String> SKIP_PATHS = Set.of(
        "/login", "/signup", "/register", "/logout", "/auth",
        "/cart", "/checkout", "/account", "/admin", "/dashboard",
        "/api/", "/static/", "/assets/", "/images/", "/img/",
        "/privacy", "/terms", "/about-us", "/contact-us",
        "/search", "/tag/", "/category/", "/author/"
    );
    
    // 優先爬取的路徑（活動相關）
    private static final Set<String> PRIORITY_PATHS = Set.of(
        "/event", "/activity", "/exhibition", "/concert", "/show",
        "/ticket", "/program", "/schedule", "/news", "/article",
        "/detail", "/info"
    );

    /**
     * 爬取單一網頁
     */
    public static CrawlResult crawl(String url) {
        CrawlResult result = new CrawlResult(url);
        
        try {
            if (!isValidUrl(url)) {
                result.setError("Invalid URL");
                return result;
            }
            
            // 輪替 User-Agent
            String userAgent = USER_AGENTS.get(userAgentIndex++ % USER_AGENTS.size());
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "identity")  // 不要壓縮，簡化處理
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            int status = response.statusCode();
            if (status != 200) {
                result.setError("HTTP " + status);
                return result;
            }
            
            String html = response.body();
            if (html.length() > MAX_CONTENT_LENGTH) {
                html = html.substring(0, MAX_CONTENT_LENGTH);
            }
            
            // 解析
            result.setHtml(html);
            result.setTitle(extractTitle(html));
            result.setTextContent(extractTextContent(html));
            result.setLinks(extractLinks(html, url));
            result.setSuccess(true);
            
        } catch (java.net.http.HttpTimeoutException e) {
            result.setError("Timeout");
        } catch (Exception e) {
            result.setError(e.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * 爬取網站（含智慧子網頁選擇）
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
        
        // 檢查時間
        if (System.currentTimeMillis() - startTime > MAX_SITE_TIME_MS) {
            return site;
        }
        
        // 2. 智慧選擇子網頁
        String domain = extractDomain(mainUrl);
        List<String> subLinks = selectBestSubpages(mainPage.getLinks(), domain, mainUrl);
        
        // 3. 並行爬取子網頁（效能提升）
        if (!subLinks.isEmpty()) {
            List<CompletableFuture<CrawlResult>> futures = subLinks.stream()
                .map(link -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(CRAWL_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return crawl(link);
                }))
                .collect(Collectors.toList());
            
            // 收集結果（有時間限制）
            long remainingTime = MAX_SITE_TIME_MS - (System.currentTimeMillis() - startTime);
            if (remainingTime > 0) {
                for (CompletableFuture<CrawlResult> future : futures) {
                    try {
                        CrawlResult subPage = future.get(
                            Math.max(remainingTime, 1000), 
                            TimeUnit.MILLISECONDS
                        );
                        if (subPage.isSuccess()) {
                            site.addSubPage(subPage);
                        }
                    } catch (Exception e) {
                        // 忽略超時的子網頁
                    }
                }
            }
        }
        
        return site;
    }
    
    /**
     * 智慧選擇最佳子網頁（核心優化）
     */
    private static List<String> selectBestSubpages(List<String> links, String domain, String mainUrl) {
        if (links == null || links.isEmpty()) return Collections.emptyList();
        
        // 分類連結
        List<String> priorityLinks = new ArrayList<>();
        List<String> normalLinks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(mainUrl.toLowerCase());
        
        for (String link : links) {
            String lowerLink = link.toLowerCase();
            
            // 跳過已見過的
            if (seen.contains(lowerLink)) continue;
            seen.add(lowerLink);
            
            // 只要同網域
            if (!isSameDomain(link, domain)) continue;
            
            // 跳過不需要的
            if (shouldSkipUrl(link)) continue;
            
            // 優先連結
            boolean isPriority = PRIORITY_PATHS.stream()
                .anyMatch(path -> lowerLink.contains(path));
            
            if (isPriority) {
                priorityLinks.add(link);
            } else {
                normalLinks.add(link);
            }
        }
        
        // 合併：優先連結在前
        List<String> result = new ArrayList<>();
        result.addAll(priorityLinks);
        result.addAll(normalLinks);
        
        // 限制數量
        return result.stream().limit(MAX_SUBPAGES).collect(Collectors.toList());
    }

    // ============ HTML 解析 ============
    
    private static String extractTitle(String html) {
        // <title>
        Pattern p1 = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(html);
        if (m1.find()) {
            String title = cleanText(m1.group(1));
            if (!title.isEmpty()) return title;
        }
        
        // og:title
        Pattern p2 = Pattern.compile("property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(html);
        if (m2.find()) {
            return cleanText(m2.group(1));
        }
        
        // <h1>
        Pattern p3 = Pattern.compile("<h1[^>]*>([^<]+)</h1>", Pattern.CASE_INSENSITIVE);
        Matcher m3 = p3.matcher(html);
        if (m3.find()) {
            return cleanText(m3.group(1));
        }
        
        return "";
    }
    
    private static String extractTextContent(String html) {
        // 移除不需要的區塊
        String text = html;
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        text = text.replaceAll("(?is)<nav[^>]*>.*?</nav>", " ");
        text = text.replaceAll("(?is)<footer[^>]*>.*?</footer>", " ");
        text = text.replaceAll("(?is)<header[^>]*>.*?</header>", " ");
        text = text.replaceAll("(?is)<!--.*?-->", " ");
        
        // 移除 HTML 標籤
        text = text.replaceAll("<[^>]+>", " ");
        
        // 清理 HTML 實體
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&#\\d+;", " ");
        
        // 清理空白
        text = text.replaceAll("\\s+", " ").trim();
        
        // 限制長度
        if (text.length() > 5000) {
            text = text.substring(0, 5000);
        }
        
        return text;
    }
    
    private static List<String> extractLinks(String html, String baseUrl) {
        List<String> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        Pattern pattern = Pattern.compile(
            "<a[^>]+href=[\"']([^\"'#?]+)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(html);
        
        while (matcher.find() && links.size() < 100) {  // 限制數量
            String href = matcher.group(1).trim();
            String absoluteUrl = resolveUrl(href, baseUrl);
            
            if (absoluteUrl != null && !seen.contains(absoluteUrl)) {
                seen.add(absoluteUrl);
                links.add(absoluteUrl);
            }
        }
        
        return links;
    }

    // ============ URL 工具 ============
    
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
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    private static boolean isSameDomain(String url, String domain) {
        String urlDomain = extractDomain(url);
        if (urlDomain.isEmpty() || domain == null) return false;
        
        // 移除 www
        urlDomain = urlDomain.replaceFirst("^www\\.", "");
        domain = domain.replaceFirst("^www\\.", "");
        
        return urlDomain.equals(domain) || urlDomain.endsWith("." + domain);
    }
    
    private static boolean shouldSkipUrl(String url) {
        String lower = url.toLowerCase();
        
        // 副檔名
        for (String ext : SKIP_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        
        // 路徑
        for (String path : SKIP_PATHS) {
            if (lower.contains(path)) return true;
        }
        
        // 太長的 URL 通常是追蹤連結
        if (url.length() > 300) return true;
        
        return false;
    }
    
    private static String resolveUrl(String href, String baseUrl) {
        try {
            if (href == null || href.isEmpty()) return null;
            if (href.startsWith("javascript:")) return null;
            if (href.startsWith("mailto:")) return null;
            if (href.startsWith("tel:")) return null;
            
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
            String basePath = baseUrl;
            int lastSlash = basePath.lastIndexOf('/');
            if (lastSlash > 8) {  // 在 "https://" 之後
                basePath = basePath.substring(0, lastSlash + 1);
            }
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
        public String getError() { return error; }
        public void setError(String error) { this.error = error; this.success = false; }
    }
    
    public static class SiteResult {
        private final String url;
        private CrawlResult mainPage;
        private final List<CrawlResult> subPages = new ArrayList<>();
        
        public SiteResult(String url) { this.url = url; }
        
        public String getUrl() { return url; }
        public CrawlResult getMainPage() { return mainPage; }
        public void setMainPage(CrawlResult mainPage) { this.mainPage = mainPage; }
        public List<CrawlResult> getSubPages() { return subPages; }
        public void addSubPage(CrawlResult page) { this.subPages.add(page); }
        public int getTotalPages() { return 1 + subPages.size(); }
        public String getDomain() { return extractDomain(url); }
    }
}