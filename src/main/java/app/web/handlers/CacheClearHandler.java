package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.bl.CacheManager;
import app.bl.Logger;
import app.web.HttpUtil;
import app.web.ServerContext;

public class CacheClearHandler implements HttpHandler {

    private final ServerContext ctx;

    public CacheClearHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendError(ex, ctx, 405, "只允許 POST 方法");
            return;
        }

        CacheManager.clear();

        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("message", "快取已清除");

        HttpUtil.sendJson(ex, ctx, 200, root.toString());
        Logger.info("快取已手動清除");
    }
}
