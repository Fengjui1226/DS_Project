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

/**
 * SuggestionsHandler 負責處理關鍵字建議請求 (/api/suggestions).
 * 根據使用者當前輸入或最後一次查詢結果提供相關的建議關鍵字列表。
 */
public class SuggestionsHandler implements HttpHandler {

    private final ServerContext ctx;

    public SuggestionsHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 處理 OPTIONS 預檢請求的共通邏輯
        if (HttpUtil.handleOptions(ex, ctx)) {
            return;
        }
        try {
            // 解析請求參數
            Map<String, String> params = HttpUtil.parseQuery(ex.getRequestURI().getQuery());
            // 取得 Session，以便存取使用者最近一次的搜尋結果和查詢
            SessionManager.Session session = ctx.sessionManager.getSession(ex);

            // 取得查詢關鍵字：優先使用傳入參數，否則使用 Session 中的 lastQuery
            String query = params.get("query");
            if (query == null || query.trim().isEmpty()) {
                query = session.lastQuery;
            }
            query = (query == null) ? "" : query.trim();

            // 根據當前查詢字串和上次的結果列表產生建議清單
            List<String> suggestions = KeywordSuggester.suggest(query, session.lastResults);

            // 構建回應 JSON，包含 success 與建議字串陣列
            JsonObject root = new JsonObject();
            root.addProperty("success", true);
            JsonArray sugArr = new JsonArray();
            for (String sug : suggestions) {
                sugArr.add(sug);  // 建議關鍵字為純字串，直接加入陣列
            }
            root.add("suggestions", sugArr);

            HttpUtil.sendJson(ex, ctx, 200, root.toString());
        } catch (Exception e) {
            Logger.error("取得關鍵字建議時發生錯誤", e);
            HttpUtil.sendError(ex, ctx, 500, "無法提供建議清單");
        }
    }
}
