package app.web.handlers;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import app.web.JsonUtil;
import app.web.ServerContext;
import app.web.SessionManager;

/**
 * SubpagesHandler 負責處理取得特定結果之子網頁列表的請求 (/api/subpages).
 * 需要傳入主頁面的 URL 作為參數，回傳對應的子頁面列表。
 */
public class SubpagesHandler implements HttpHandler {

    private final ServerContext ctx;

    public SubpagesHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 統一處理 OPTIONS 預檢請求
        if (HttpUtil.handleOptions(ex, ctx)) {
            return;
        }
        try {
            // 解析請求參數
            Map<String, String> params = HttpUtil.parseQuery(ex.getRequestURI().getQuery());
            String url = params.get("url");
            if (url == null || url.trim().isEmpty()) {
                HttpUtil.sendError(ex, ctx, 400, "缺少 url 參數");
                return;
            }
            // 確保 URL 解碼（避免多重編碼問題）
            url = URLDecoder.decode(url, StandardCharsets.UTF_8);

            // 從 Session 取得最近一次搜尋的結果列表，在其中尋找對應 URL 的主結果
            SessionManager.Session session = ctx.sessionManager.getSession(ex);
            PageNode targetResult = null;
            List<PageNode> lastResults = session.lastResults;
            for (PageNode p : lastResults) {
                if (p != null && url.equals(p.getUrl())) {
                    targetResult = p;
                    break;
                }
            }
            if (targetResult == null) {
                HttpUtil.sendError(ex, ctx, 404, "查無相符結果（請先執行搜尋）");
                return;
            }

            // 構建回傳的 JSON：包含成功標記與子網頁列表
            JsonObject root = new JsonObject();
            root.addProperty("success", true);
            JsonArray subPagesArr = new JsonArray();
            List<SubPageNode> subs = targetResult.getSubPages();
            for (SubPageNode sub : subs) {
                JsonObject obj = new JsonObject();
                obj.addProperty("url", JsonUtil.safe(sub.getUrl()));
                obj.addProperty("title", JsonUtil.safe(sub.getTitle()));
                obj.addProperty("score", JsonUtil.round1(sub.getScore()));
                subPagesArr.add(obj);
            }
            root.add("subPages", subPagesArr);
            // 回傳子網頁列表 JSON
            HttpUtil.sendJson(ex, ctx, 200, root.toString());

        } catch (Exception e) {
            Logger.error("子網頁查詢錯誤", e);
            HttpUtil.sendError(ex, ctx, 500, "取得子網頁資料時發生錯誤");
        }
    }
}
