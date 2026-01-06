package app.web.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.bl.CacheManager;
import app.bl.InputValidator;
import app.bl.Logger;
import app.bl.PageNode;
import app.bl.SearchEngine;
import app.web.HttpUtil;
import app.web.JsonUtil;
import app.web.ServerContext;
import app.web.SessionManager;

public class SearchHandler implements HttpHandler {

    private final ServerContext ctx;

    public SearchHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.handleOptions(ex, ctx)) return;

        if (ctx.isShuttingDown.get()) {
            HttpUtil.sendError(ex, ctx, 503, "伺服器正在關閉中");
            return;
        }

        if (!ctx.rateLimiter.allow(ex)) {
            HttpUtil.sendError(ex, ctx, 429, "請求太頻繁，請稍後再試");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            Map<String, String> params = HttpUtil.parseQuery(ex.getRequestURI().getQuery());

            InputValidator.ValidationResult queryResult =
                    InputValidator.validateQuery(getOrDefault(params, "query", ""));
            if (!queryResult.valid) {
                HttpUtil.sendError(ex, ctx, 400, queryResult.message);
                return;
            }

            InputValidator.ValidationResult cityResult =
                    InputValidator.validateCity(getOrDefault(params, "city", ""));
            if (!cityResult.valid) {
                HttpUtil.sendError(ex, ctx, 400, cityResult.message);
                return;
            }

            String query = queryResult.sanitizedValue;
            String city = cityResult.sanitizedValue;
            int page = InputValidator.validatePage(params.get("page"), 1);

            // 快取
            String cacheKey = CacheManager.buildKey(query, city, page);
            String cached = CacheManager.get(cacheKey);
            if (cached != null) {
                Logger.debug("快取命中: %s", cacheKey);
                HttpUtil.sendJson(ex, ctx, 200, JsonUtil.markCachedTrue(cached));
                return;
            }

            // Session（避免多人互撞）
            SessionManager.Session session = ctx.sessionManager.getSession(ex);
            session.profile.setUserCity(city);
            session.lastQuery = query;

            // 搜尋
            List<PageNode> results = SearchEngine.search(query, session.profile);
            session.lastResults = results;

            // 分頁
            int pageSize = 10;
            int totalCount = results.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, totalCount);

            List<PageNode> pageResults = (start < totalCount)
                    ? results.subList(start, end)
                    : new ArrayList<PageNode>();

            long duration = System.currentTimeMillis() - startTime;

            String json = buildSearchResponse(query, city, page, totalCount, totalPages, pageResults, duration, start, false);

            CacheManager.put(cacheKey, json);
            HttpUtil.sendJson(ex, ctx, 200, json);

            Logger.info("搜尋完成: query=\"%s\", city=\"%s\", results=%d, time=%dms",
                    query, city, totalCount, duration);

        } catch (Exception e) {
            Logger.error("搜尋錯誤", e);
            HttpUtil.sendError(ex, ctx, 500, "搜尋發生錯誤，請稍後再試");
        }
    }

    private static String getOrDefault(Map<String, String> map, String key, String def) {
        String v = map.get(key);
        return (v == null) ? def : v;
    }

    private static String buildSearchResponse(String query, String city, int page,
                                              int totalCount, int totalPages,
                                              List<PageNode> pageResults,
                                              long duration, int startRank, boolean cached) {

        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("query", query);
        root.addProperty("city", city);
        root.addProperty("page", page);
        root.addProperty("totalCount", totalCount);
        root.addProperty("totalPages", totalPages);
        root.addProperty("responseTime", duration);
        root.addProperty("cached", cached);

        JsonArray arr = new JsonArray();
        int rank = startRank + 1;

        for (int i = 0; i < pageResults.size(); i++) {
            PageNode p = pageResults.get(i);

            JsonObject o = new JsonObject();
            o.addProperty("rank", rank++);
            o.addProperty("title", safe(p.getTitle()));
            o.addProperty("url", safe(p.getUrl()));
            o.addProperty("domain", safe(p.getDomain()));
            o.addProperty("score", round1(p.getTotalScore()));
            o.addProperty("subPageCount", p.getSubPageCount());

            String pc = p.getCity();
            o.addProperty("city", pc == null ? "" : pc);

            o.addProperty("eventDate", p.getEventDate() == null ? "" : p.getEventDate().toString());
            o.addProperty("crawled", p.isCrawled());

            arr.add(o);
        }

        root.add("results", arr);
        return root.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double round1(double x) {
        // 只保留 1 位小數（避免 String.format 造成 locale 問題）
        return Math.round(x * 10.0) / 10.0;
    }
}
