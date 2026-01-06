package app.web;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import app.bl.CacheManager;
import app.bl.Logger;
import app.da.Config;
import app.web.handlers.CacheClearHandler;
import app.web.handlers.CacheStatsHandler;
import app.web.handlers.CategoriesHandler;
import app.web.handlers.HealthHandler;
import app.web.handlers.ReadyHandler;
import app.web.handlers.SearchHandler;
import app.web.handlers.SubpagesHandler;
import app.web.handlers.SuggestionsHandler;

/**
 * SimpleServer v4.1 - Production-ready refactor
 *
 * ✅ Per-IP Session（避免多執行緒互撞）
 * ✅ Handler 拆分（可維護）
 * ✅ Gson JSON（安全）
 * ✅ cached=true 正確顯示
 * ✅ CORS / RateLimit / Graceful shutdown
 *
 * Java 8 compatible.
 */
public class SimpleServer {

    // ==================== 配置 ====================
    private static final int PORT = Config.getInt("server.port", 8080);
    private static final int THREAD_POOL_SIZE = Config.getInt("server.threads", 10);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = Config.getInt("server.shutdownTimeout", 30);

    // CORS
    private static final String CORS_ORIGIN = Config.get("cors.origin", "*");
    private static final String CORS_METHODS = "GET, POST, OPTIONS";
    private static final String CORS_HEADERS = "Content-Type, Authorization";

    // Rate Limit
    private static final int RATE_LIMIT_REQUESTS = Config.getInt("rateLimit.requests", 30);
    private static final int RATE_LIMIT_WINDOW_SECONDS = Config.getInt("rateLimit.windowSeconds", 60);

    // Session TTL（例如 15 分鐘不動就清掉）
    private static final long SESSION_TTL_MILLIS = Config.getInt("session.ttlSeconds", 900) * 1000L;

    // API version
    private static final String API_VERSION = "v1";
    private static final String API_PREFIX = "/api/" + API_VERSION;

    private static HttpServer server;
    private static ExecutorService executor;

    public static void main(String[] args) throws Exception {
        startServer();
    }

    public static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        server.setExecutor(executor);

        // Context init
        ServerContext ctx = new ServerContext(
                new SessionManager(SESSION_TTL_MILLIS),
                new RateLimiter(RATE_LIMIT_REQUESTS, RATE_LIMIT_WINDOW_SECONDS),
                CORS_ORIGIN, CORS_METHODS, CORS_HEADERS,
                SHUTDOWN_TIMEOUT_SECONDS
        );
        ctx.executor = executor;

        // Handlers
        SearchHandler searchHandler = new SearchHandler(ctx);
        SuggestionsHandler suggestionsHandler = new SuggestionsHandler(ctx);
        SubpagesHandler subpagesHandler = new SubpagesHandler(ctx);
        CategoriesHandler categoriesHandler = new CategoriesHandler(ctx);
        CacheStatsHandler cacheStatsHandler = new CacheStatsHandler(ctx);
        CacheClearHandler cacheClearHandler = new CacheClearHandler(ctx);
        HealthHandler healthHandler = new HealthHandler(ctx);
        ReadyHandler readyHandler = new ReadyHandler(ctx);

        // API 路由（版本化）
        server.createContext(API_PREFIX + "/search", searchHandler);
        server.createContext(API_PREFIX + "/suggestions", suggestionsHandler);
        server.createContext(API_PREFIX + "/subpages", subpagesHandler);
        server.createContext(API_PREFIX + "/categories", categoriesHandler);
        server.createContext(API_PREFIX + "/cache/stats", cacheStatsHandler);
        server.createContext(API_PREFIX + "/cache/clear", cacheClearHandler);

        // 健康檢查（不需要版本）
        server.createContext("/health", healthHandler);
        server.createContext("/ready", readyHandler);

        // 向後相容（舊路徑）
        server.createContext("/api/search", searchHandler);
        server.createContext("/api/suggestions", suggestionsHandler);
        server.createContext("/api/health", healthHandler);

        // Shutdown hook
        registerShutdownHook(ctx);

        server.start();

        Logger.banner(
                "🎪 EventFinder API Server v4.1",
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
                "  • " + API_PREFIX + "/cache/clear  清除快取(POST)",
                "  • /health                  健康檢查",
                "  • /ready                   就緒檢查"
        );
    }

    private static void registerShutdownHook(final ServerContext ctx) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                Logger.info("收到關閉信號，開始 Graceful Shutdown...");
                ctx.isShuttingDown.set(true);

                // 1) stop accept new requests
                if (server != null) {
                    server.stop(1);
                    Logger.info("HTTP Server 已停止接收新請求");
                }

                // 2) shutdown executor
                if (executor != null) {
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(ctx.shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                            Logger.warn("執行緒池未能在 %d 秒內關閉，強制終止", ctx.shutdownTimeoutSeconds);
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    Logger.info("執行緒池已關閉");
                }

                // 3) clear cache
                CacheManager.clear();

                Logger.info("Graceful Shutdown 完成");
            }
        }, "shutdown-hook"));
    }

    // 測試用
    public static void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }
}
