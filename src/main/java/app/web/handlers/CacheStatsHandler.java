package app.web.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.bl.CacheManager;
import app.web.HttpUtil;
import app.web.ServerContext;

/**
 * CacheStatsHandler - 快取統計
 * GET /api/v1/cache/stats
 */
public class CacheStatsHandler implements HttpHandler {

    private final ServerContext ctx;

    public CacheStatsHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        CacheManager.CacheStats stats = CacheManager.getStats();
        String json = "{\"success\":true,\"cache\":" + stats.toJson() + "}";

        HttpUtil.sendJson(ex, ctx, 200, json);
    }
}
