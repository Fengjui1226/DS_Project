package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.web.HttpUtil;
import app.web.ServerContext;

public class ReadyHandler implements HttpHandler {

    private final ServerContext ctx;

    public ReadyHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        HttpUtil.setCorsHeaders(ex, ctx);

        boolean ready = !ctx.isShuttingDown.get();

        JsonObject root = new JsonObject();
        root.addProperty("ready", ready);

        int code = ready ? 200 : 503;
        HttpUtil.sendJson(ex, ctx, code, root.toString());
    }
}
