package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.web.HttpUtil;
import app.web.ServerContext;

public class CategoriesHandler implements HttpHandler {

    private final ServerContext ctx;

    public CategoriesHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        JsonObject root = new JsonObject();
        root.addProperty("success", true);

        JsonArray arr = new JsonArray();
        arr.add("市集");
        arr.add("展覽");
        arr.add("演唱會");
        arr.add("音樂節");
        arr.add("講座");
        arr.add("工作坊");
        arr.add("親子");
        arr.add("戶外");
        arr.add("美食");
        arr.add("運動");
        arr.add("寵物");
        arr.add("節慶");

        root.add("categories", arr);

        HttpUtil.sendJson(ex, ctx, 200, root.toString());
    }
}
