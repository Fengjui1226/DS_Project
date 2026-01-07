package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.web.HttpUtil;
import app.web.ServerContext;

/**
 * HealthHandler 用於提供系統健康狀態 (/health).
 * 直接回傳當前系統狀態（健康或正在關閉）。
 */
public class HealthHandler implements HttpHandler {

    private final ServerContext ctx;

    public HealthHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 設定 CORS 表頭，允許跨網域請求健康檢查
        HttpUtil.setCorsHeaders(ex, ctx);
        // 判斷系統狀態：若正在關閉則標記狀態為 shutting_down
        boolean shuttingDown = ctx.isShuttingDown.get();
        String status = shuttingDown ? "shutting_down" : "healthy";

        // 構建健康狀態回應 JSON
        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("status", status);
        root.addProperty("version", "4.1");
        root.addProperty("timestamp", System.currentTimeMillis());

        int statusCode = shuttingDown ? 503 : 200;
        HttpUtil.sendJson(ex, ctx, statusCode, root.toString());
    }
}
