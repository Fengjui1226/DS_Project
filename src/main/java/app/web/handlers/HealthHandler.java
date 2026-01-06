package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.web.HttpUtil;
import app.web.ServerContext;

public class HealthHandler implements HttpHandler {

    private final ServerContext ctx;

    public HealthHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        HttpUtil.setCorsHeaders(ex, ctx);

        String status = ctx.isShuttingDown.get() ? "shutting_down" : "healthy";

        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("status", status);
        root.addProperty("version", "4.1");
        root.addProperty("timestamp", System.currentTimeMillis());

        int code = ctx.isShuttingDown.get() ? 503 : 200;
        HttpUtil.sendJson(ex, ctx, code, root.toString());
    }
}
