package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.web.HttpUtil;
import app.web.ServerContext;

/**
 * ReadyHandler 用於提供系統就緒狀態 (/ready).
 * 回傳服務是否已準備好處理請求。
 */
public class ReadyHandler implements HttpHandler {

    private final ServerContext ctx;

    public ReadyHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 設定 CORS 表頭
        HttpUtil.setCorsHeaders(ex, ctx);
        // 判斷系統是否處於就緒狀態（未在關閉中即視為就緒）
        boolean ready = !ctx.isShuttingDown.get();

        // 構建就緒狀態回應 JSON
        JsonObject root = new JsonObject();
        root.addProperty("ready", ready);

        int statusCode = ready ? 200 : 503;
        HttpUtil.sendJson(ex, ctx, statusCode, root.toString());
    }
}
