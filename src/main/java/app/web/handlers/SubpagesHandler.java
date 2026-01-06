package app.web.handlers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.bl.Logger;
import app.bl.PageNode;
import app.bl.SubPageNode;
import app.web.HttpUtil;
import app.web.ServerContext;
import app.web.SessionManager;

public class SubpagesHandler implements HttpHandler {

    private final ServerContext ctx;

    public SubpagesHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        try {
            Map<String, String> params = HttpUtil.parseQuery(ex.getRequestURI().getQuery());
            String url = params.get("url");

            if (url == null || url.trim().isEmpty()) {
                HttpUtil.sendError(ex, ctx, 400, "缺少 url 參數");
                return;
            }

            SessionManager.Session session = ctx.sessionManager.getSession(ex);

            PageNode target = null;
            List<PageNode> last = session.lastResults;
            for (int i = 0; i < last.size(); i++) {
                PageNode p = last.get(i);
                if (p != null && url.equals(p.getUrl())) {
                    target = p;
                    break;
                }
            }

            if (target == null) {
                HttpUtil.sendError(ex, ctx, 404, "找不到對應的結果（請先搜尋）");
                return;
            }

            JsonObject root = new JsonObject();
            root.addProperty("success", true);

            JsonArray arr = new JsonArray();

            List<SubPageNode> subs = target.getSubPages();
            for (int i = 0; i < subs.size(); i++) {
                SubPageNode s = subs.get(i);

                JsonObject o = new JsonObject();
                o.addProperty("url", safe(s.getUrl()));
                o.addProperty("title", safe(s.getTitle()));
                o.addProperty("score", round1(s.getScore()));
                arr.add(o);
            }

            root.add("subPages", arr);
            HttpUtil.sendJson(ex, ctx, 200, root.toString());

        } catch (Exception e) {
            Logger.error("子網頁查詢錯誤", e);
            HttpUtil.sendError(ex, ctx, 500, "查詢子網頁失敗");
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double round1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }
}
