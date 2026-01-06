package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.bl.CacheManager;
import app.web.HttpUtil;
import app.web.ServerContext;

public class CacheStatsHandler implements HttpHandler {

    private final ServerContext ctx;

    public CacheStatsHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        CacheManager.CacheStats stats = CacheManager.getStats();

        JsonObject root = new JsonObject();
        root.addProperty("success", true);

        // 你原本 stats.toJson() 是字串，這裡直接塞 raw string 會被 escape
        // 所以用小技巧：把 stats.toJson() parse 成 JsonObject 再塞入
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(stats.toJson()).getAsJsonObject();
            root.add("cache", obj);
        } catch (Exception e) {
            root.addProperty("cache", stats.toJson());
        }

        HttpUtil.sendJson(ex, ctx, 200, root.toString());
    }
}
