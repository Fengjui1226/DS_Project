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
 * SimpleServer - 後端主程式
 * 支援 /api/v1/* 與舊版相容 /api/*
 */
public class SimpleServer {

    private static final int PORT = Config.getInt("server.port", 8080);
    private static final int THREAD_POOL_SIZE = Config.getInt("server.threads", 10);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = Config.getInt("server.shutdownTimeout", 30);

    private static final String CORS_ORIGIN = Config.get("cors.origin", "*");
    private static final String CORS_METHODS = "GET, POST, OPTIONS";
    private static final String CORS_HEADERS = "Content-Type, Authorization";

    private static final int RATE_LIMIT_REQUESTS = Config.getInt("rateLimit.requests", 30);
    private static final int RATE_LIMIT_WINDOW_SECONDS = Config.getInt("rateLimit.windowSeconds", 60);

    private static final long SESSION_TTL_MILLIS = Config.getInt("session.ttlSeconds", 900) * 1000L;

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

        // v1 routes
        server.createContext(API_PREFIX + "/search", searchHandler);
        server.createContext(API_PREFIX + "/suggestions", suggestionsHandler);
        server.createContext(API_PREFIX + "/subpages", subpagesHandler);
        server.createContext(API_PREFIX + "/categories", categoriesHandler);
        server.createContext(API_PREFIX + "/cache/stats", cacheStatsHandler);
        server.createContext(API_PREFIX + "/cache/clear", cacheClearHandler);

        // health routes
        server.createContext("/health", healthHandler);
        server.createContext("/ready", readyHandler);

        // legacy routes
        server.createContext("/api/search", searchHandler);
        server.createContext("/api/suggestions", suggestionsHandler);
        server.createContext("/api/subpages", subpagesHandler);
        server.createContext("/api/categories", categoriesHandler);
        server.createContext("/api/cache/stats", cacheStatsHandler);
        server.createContext("/api/cache/clear", cacheClearHandler);
        server.createContext("/api/health", healthHandler);

        registerShutdownHook(ctx);

        server.start();
        Logger.info("Server started on port %d with %d threads.", PORT, THREAD_POOL_SIZE);
    }

    private static void registerShutdownHook(final ServerContext ctx) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("收到關閉信號，開始進行 Graceful Shutdown...");
            ctx.isShuttingDown.set(true);

            if (server != null) {
                server.stop(1);
                Logger.info("HTTP Server 已停止接收新請求");
            }

            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(ctx.shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                        Logger.warn("執行緒池未能在指定時間內關閉，將強制終止");
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                Logger.info("執行緒池已關閉");
            }

            CacheManager.clear();
            Logger.info("所有快取已清除，服務正常關閉。");
        }, "shutdown-hook"));
    }

    public static void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }
}
