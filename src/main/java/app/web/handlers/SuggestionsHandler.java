package app.web.handlers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.bl.KeywordSuggester;
import app.bl.Logger;
import app.web.HttpUtil;
import app.web.ServerContext;
import app.web.SessionManager;

public class SuggestionsHandler implements HttpHandler {

    private final ServerContext ctx;

    public SuggestionsHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        try {
            Map<String, String> params = HttpUtil.parseQuery(ex.getRequestURI().getQuery());

            SessionManager.Session session = ctx.sessionManager.getSession(ex);

            String query = params.get("query");
            if (query == null || query.trim().isEmpty()) query = session.lastQuery;
            query = (query == null) ? "" : query.trim();

            List<String> suggestions = KeywordSuggester.suggest(query, session.lastResults);

            JsonObject root = new JsonObject();
            root.addProperty("success", true);

            JsonArray arr = new JsonArray();
            for (int i = 0; i < suggestions.size(); i++) {
                arr.add(suggestions.get(i));
            }
            root.add("suggestions", arr);

            HttpUtil.sendJson(ex, ctx, 200, root.toString());

        } catch (Exception e) {
            Logger.error("關鍵字推薦錯誤", e);
            HttpUtil.sendError(ex, ctx, 500, "取得建議失敗");
        }
    }
}
