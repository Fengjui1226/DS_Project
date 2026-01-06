package app.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import app.bl.*;
import app.da.Config;

/**
 * SimpleServer v4.0 - 生產環境就緒版
 * 
 * 改進：
 * 1. 環境變數配置
 * 2. Graceful Shutdown
 * 3. 完整的快取機制
 * 4. 統一日誌
 * 5. 輸入驗證
 * 6. API 版本化
 * 7. CORS 可配置
 */
public class SimpleServer {

    // ==================== 配置 ====================
    private static final int PORT = Config.getInt("server.port", 8080);
    private static final int THREAD_POOL_SIZE = Config.getInt("server.threads", 10);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = Config.getInt("server.shutdownTimeout", 30);
    
    // CORS 配置
    private static final String CORS_ORIGIN = Config.get("cors.origin", "*");
    private static final String CORS_METHODS = "GET, POST, OPTIONS";
    private static final String CORS_HEADERS = "Content-Type, Authorization";

    // 限流配置
    private static final int RATE_LIMIT_REQUESTS = Config.getInt("rateLimit.requests", 30);
    private static final int RATE_LIMIT_WINDOW_SECONDS = Config.getInt("rateLimit.windowSeconds", 60);
    private static final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    // 伺服器狀態
    private static HttpServer server;
    private static ExecutorService executor;
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // 用戶狀態
    private static UserProfile userProfile = new UserProfile();
    private static List<PageNode> lastResults = new ArrayList<>();
    private static String lastQuery = "";

    // API 版本
    private static final String API_VERSION = "v1";
    private static final String API_PREFIX = "/api/" + API_VERSION;

    // ==================== 限流 ====================
    static class RateLimitInfo {
        int count = 0;
        long windowStart = System.currentTimeMillis();
    }

    // ==================== 啟動入口 ====================
    public static void main(String[] args) throws Exception {
        startServer();
    }

    public static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        server.setExecutor(executor);

        // API 路由（版本化）
        server.createContext(API_PREFIX + "/search", SimpleServer::handleSearch);
        server.createContext(API_PREFIX + "/suggestions", SimpleServer::handleSuggestions);
        server.createContext(API_PREFIX + "/subpages", SimpleServer::handleSubpages);
        server.createContext(API_PREFIX + "/categories", SimpleServer::handleCategories);
        server.createContext(API_PREFIX + "/cache/stats", SimpleServer::handleCacheStats);
        server.createContext(API_PREFIX + "/cache/clear", SimpleServer::handleCacheClear);
        
        // 健康檢查（不需要版本）
        server.createContext("/health", SimpleServer::handleHealth);
        server.createContext("/ready", SimpleServer::handleReady);

        // 向後相容（舊路徑）
        server.createContext("/api/search", SimpleServer::handleSearch);
        server.createContext("/api/suggestions", SimpleServer::handleSuggestions);
        server.createContext("/api/health", SimpleServer::handleHealth);

        // 註冊 Shutdown Hook
        registerShutdownHook();

        server.start();

        Logger.banner(
            "🎪 EventFinder API Server v4.0",
            "",
            "Status:    ✅ Running",
            "Port:      " + PORT,
            "Threads:   " + THREAD_POOL_SIZE,
            "Cache:     ✅ Enabled (TTL: 300s)",
            "Log Level: " + Logger.getCurrentLevel(),
            "",
            "Endpoints:",
            "  • " + API_PREFIX + "/search       搜尋活動",
            "  • " + API_PREFIX + "/suggestions  關鍵字推薦",
            "  • " + API_PREFIX + "/subpages     子網頁詳情",
            "  • " + API_PREFIX + "/categories   分類列表",
            "  • " + API_PREFIX + "/cache/stats  快取統計",
            "  • /health                  健康檢查",
            "  • /ready                   就緒檢查"
        );
    }

    // ==================== Graceful Shutdown ====================
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("收到關閉信號，開始 Graceful Shutdown...");
            isShuttingDown.set(true);

            // 1. 停止接收新請求
            if (server != null) {
                server.stop(1);
                Logger.info("HTTP Server 已停止接收新請求");
            }

            // 2. 關閉執行緒池
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        Logger.warn("執行緒池未能在 %d 秒內關閉，強制終止", SHUTDOWN_TIMEOUT_SECONDS);
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                Logger.info("執行緒池已關閉");
            }

            // 3. 清理快取
            CacheManager.clear();

            Logger.info("Graceful Shutdown 完成");
        }, "shutdown-hook"));
    }

    // ==================== 共用方法 ====================
    private static boolean checkRateLimit(HttpExchange ex) {
        String clientIP = ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();

        RateLimitInfo info = rateLimitMap.computeIfAbsent(clientIP, k -> new RateLimitInfo());

        synchronized (info) {
            if (now - info.windowStart > RATE_LIMIT_WINDOW_SECONDS * 1000L) {
                info.count = 0;
                info.windowStart = now;
            }
            info.count++;
            
            if (info.count > RATE_LIMIT_REQUESTS) {
                Logger.warn("限流觸發: IP=%s, count=%d", clientIP, info.count);
                return false;
            }
            return true;
        }
    }

    private static void setCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", CORS_ORIGIN);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", CORS_METHODS);
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", CORS_HEADERS);
        ex.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        setCorsHeaders(ex);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        String json = String.format(
            "{\"success\":false,\"error\":\"%s\",\"code\":%d}",
            InputValidator.escapeJson(message), status
        );
        sendJson(ex, status, json);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception e) {
                    params.put(kv[0], kv[1]);
                }
            }
        }
        return params;
    }

    // ==================== 搜尋 API ====================
    private static void handleSearch(HttpExchange ex) throws IOException {
        // OPTIONS 預檢
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }

        // 關閉中拒絕請求
        if (isShuttingDown.get()) {
            sendError(ex, 503, "伺服器正在關閉中");
            return;
        }

        // 限流檢查
        if (!checkRateLimit(ex)) {
            sendError(ex, 429, "請求太頻繁，請稍後再試");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());

            // 輸入驗證
            InputValidator.ValidationResult queryResult = 
                InputValidator.validateQuery(params.getOrDefault("query", ""));
            if (!queryResult.valid) {
                sendError(ex, 400, queryResult.message);
                return;
            }

            InputValidator.ValidationResult cityResult = 
                InputValidator.validateCity(params.getOrDefault("city", ""));
            if (!cityResult.valid) {
                sendError(ex, 400, cityResult.message);
                return;
            }

            String query = queryResult.sanitizedValue;
            String city = cityResult.sanitizedValue;
            int page = InputValidator.validatePage(params.get("page"), 1);

            // 檢查快取
            String cacheKey = CacheManager.buildKey(query, city, page);
            String cachedResult = CacheManager.get(cacheKey);
            if (cachedResult != null) {
                Logger.debug("快取命中: %s", cacheKey);
                sendJson(ex, 200, cachedResult);
                return;
            }

            // 執行搜尋
            userProfile.setUserCity(city);
            lastQuery = query;

            List<PageNode> results = SearchEngine.search(query, userProfile);
            lastResults = results;

            // 分頁
            int pageSize = 10;
            int totalCount = results.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, totalCount);

            List<PageNode> pageResults = (start < totalCount)
                ? results.subList(start, end)
                : new ArrayList<>();

            long duration = System.currentTimeMillis() - startTime;

            // 建構回應
            String json = buildSearchResponse(query, city, page, totalCount, totalPages, pageResults, duration, start);

            // 寫入快取
            CacheManager.put(cacheKey, json);

            sendJson(ex, 200, json);

            Logger.info("搜尋完成: query=\"%s\", city=\"%s\", results=%d, time=%dms",
                query, city, totalCount, duration);

        } catch (Exception e) {
            Logger.error("搜尋錯誤", e);
            sendError(ex, 500, "搜尋發生錯誤，請稍後再試");
        }
    }

    private static String buildSearchResponse(String query, String city, int page, 
            int totalCount, int totalPages, List<PageNode> pageResults, long duration, int start) {
        
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,");
        json.append("\"query\":\"").append(InputValidator.escapeJson(query)).append("\",");
        json.append("\"city\":\"").append(InputValidator.escapeJson(city)).append("\",");
        json.append("\"page\":").append(page).append(",");
        json.append("\"totalCount\":").append(totalCount).append(",");
        json.append("\"totalPages\":").append(totalPages).append(",");
        json.append("\"responseTime\":").append(duration).append(",");
        json.append("\"cached\":false,");
        json.append("\"results\":[");

        int rank = start + 1;
        for (int i = 0; i < pageResults.size(); i++) {
            PageNode p = pageResults.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"rank\":").append(rank++).append(",");
            json.append("\"title\":\"").append(InputValidator.escapeJson(p.getTitle())).append("\",");
            json.append("\"url\":\"").append(InputValidator.escapeJson(p.getUrl())).append("\",");
            json.append("\"domain\":\"").append(InputValidator.escapeJson(p.getDomain())).append("\",");
            json.append("\"score\":").append(String.format("%.1f", p.getTotalScore())).append(",");
            json.append("\"subPageCount\":").append(p.getSubPageCount()).append(",");
            json.append("\"city\":\"").append(InputValidator.escapeJson(p.getCity() != null ? p.getCity() : "")).append("\",");
            json.append("\"eventDate\":\"").append(p.getEventDate() != null ? p.getEventDate().toString() : "").append("\",");
            json.append("\"crawled\":").append(p.isCrawled());
            json.append("}");
        }

        json.append("]}");
        return json.toString();
    }

    // ==================== 關鍵字推薦 API ====================
    private static void handleSuggestions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }

        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String query = params.getOrDefault("query", lastQuery).trim();

            List<String> suggestions = KeywordSuggester.suggest(query, lastResults);

            StringBuilder json = new StringBuilder();
            json.append("{\"success\":true,\"suggestions\":[");
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(InputValidator.escapeJson(suggestions.get(i))).append("\"");
            }
            json.append("]}");

            sendJson(ex, 200, json.toString());

        } catch (Exception e) {
            Logger.error("關鍵字推薦錯誤", e);
            sendError(ex, 500, "取得建議失敗");
        }
    }

    // ==================== 子網頁 API ====================
    private static void handleSubpages(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }

        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String url = params.get("url");

            if (url == null || url.isEmpty()) {
                sendError(ex, 400, "缺少 url 參數");
                return;
            }

            // 找到對應的 PageNode
            PageNode target = null;
            for (PageNode p : lastResults) {
                if (url.equals(p.getUrl())) {
                    target = p;
                    break;
                }
            }

            if (target == null) {
                sendError(ex, 404, "找不到對應的結果");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"success\":true,\"subPages\":[");

            List<SubPageNode> subPages = target.getSubPages();
            for (int i = 0; i < subPages.size(); i++) {
                SubPageNode sub = subPages.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"url\":\"").append(InputValidator.escapeJson(sub.getUrl())).append("\",");
                json.append("\"title\":\"").append(InputValidator.escapeJson(sub.getTitle())).append("\",");
                json.append("\"score\":").append(String.format("%.1f", sub.getScore()));
                json.append("}");
            }

            json.append("]}");
            sendJson(ex, 200, json.toString());

        } catch (Exception e) {
            Logger.error("子網頁查詢錯誤", e);
            sendError(ex, 500, "查詢子網頁失敗");
        }
    }

    // ==================== 分類 API ====================
    private static void handleCategories(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String json = "{\"success\":true,\"categories\":[" +
            "\"市集\",\"展覽\",\"演唱會\",\"音樂節\",\"講座\",\"工作坊\"," +
            "\"親子\",\"戶外\",\"美食\",\"運動\",\"寵物\",\"節慶\"" +
            "]}";

        sendJson(ex, 200, json);
    }

    // ==================== 快取 API ====================
    private static void handleCacheStats(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }

        CacheManager.CacheStats stats = CacheManager.getStats();
        String json = "{\"success\":true,\"cache\":" + stats.toJson() + "}";
        sendJson(ex, 200, json);
    }

    private static void handleCacheClear(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "只允許 POST 方法");
            return;
        }

        CacheManager.clear();
        String json = "{\"success\":true,\"message\":\"快取已清除\"}";
        sendJson(ex, 200, json);
        Logger.info("快取已手動清除");
    }

    // ==================== 健康檢查 ====================
    private static void handleHealth(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        
        String status = isShuttingDown.get() ? "shutting_down" : "healthy";
        String json = String.format(
            "{\"success\":true,\"status\":\"%s\",\"version\":\"4.0\",\"timestamp\":%d}",
            status, System.currentTimeMillis()
        );
        
        int code = isShuttingDown.get() ? 503 : 200;
        sendJson(ex, code, json);
    }

    private static void handleReady(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        
        // 檢查是否準備好接收流量
        boolean ready = !isShuttingDown.get();
        
        String json = String.format("{\"ready\":%b}", ready);
        int code = ready ? 200 : 503;
        sendJson(ex, code, json);
    }

    // ==================== 公開方法（測試用）====================
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}