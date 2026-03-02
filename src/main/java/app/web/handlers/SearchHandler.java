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
import app.bl.config.CityConfig;
import app.web.HttpUtil;
import app.web.JsonUtil;
import app.web.ServerContext;
import app.web.SessionManager;

/**
 * SearchHandler 負責處理搜尋請求 (/api/search).
 * 接收 query 和 city 參數，返回包含搜尋結果的 JSON。
 */
public class SearchHandler implements HttpHandler {

    private final ServerContext ctx;

    public SearchHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 處理 CORS 的 OPTIONS 預檢請求
        if (HttpUtil.handleOptions(ex, ctx)) {
            return;
        }
        // 若伺服器正處於關閉狀態，不再接受新請求
        if (ctx.isShuttingDown.get()) {
            HttpUtil.sendError(ex, ctx, 503, "伺服器正在關閉中");
            return;
        }
        // 檢查是否觸發每 IP 限流
        if (!ctx.rateLimiter.allow(ex)) {
            HttpUtil.sendError(ex, ctx, 429, "請求過於頻繁，請稍後再試");
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // 解析查詢參數
            Map<String, String> params = HttpUtil.parseQuery(ex.getRequestURI().getQuery());
            // 驗證並取得查詢參數
            InputValidator.ValidationResult queryVal = InputValidator.validateQuery(params.getOrDefault("query", ""));
            if (!queryVal.valid) {
                HttpUtil.sendError(ex, ctx, 400, queryVal.message);
                return;
            }
            InputValidator.ValidationResult cityVal = InputValidator.validateCity(params.getOrDefault("city", ""));
            if (!cityVal.valid) {
                HttpUtil.sendError(ex, ctx, 400, cityVal.message);
                return;
            }

            String query = queryVal.sanitizedValue;
            String city = cityVal.sanitizedValue;
            int page = InputValidator.validatePage(params.get("page"), 1);

            // 快取查詢：如快取存在則直接返回結果並標記 cached=true
            String cacheKey = CacheManager.buildKey(query, city, page);
            String cachedJson = CacheManager.get(cacheKey);
            if (cachedJson != null) {
                Logger.debug("快取命中: " + cacheKey);
                // 標記快取命中並直接回傳
                String cachedResponse = JsonUtil.markCachedTrue(cachedJson);
                HttpUtil.sendJson(ex, ctx, 200, cachedResponse);
                return;
            }

            // 建立 Session（以 IP 區分）避免多用戶交叉影響
            SessionManager.Session session = ctx.sessionManager.getSession(ex);
            session.profile.setUserCity(city);  // 設定使用者偏好的城市
            session.lastQuery = query;

            // 解析 GPS 座標（前端定位後傳入）
            String latStr = params.get("lat");
            String lngStr = params.get("lng");
            if (latStr != null && lngStr != null) {
                try {
                    double lat = Double.parseDouble(latStr);
                    double lng = Double.parseDouble(lngStr);
                    if (lat >= 21.0 && lat <= 26.5 && lng >= 119.0 && lng <= 122.5) {
                        session.profile.setUserCoords(lat, lng);
                        // 若 city 未明確指定，從 GPS 自動推斷
                        if (city.isEmpty()) {
                            String detected = CityConfig.nearestCity(lat, lng);
                            session.profile.setUserCity(detected);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }

            // 執行搜尋邏輯
            List<PageNode> allResults = SearchEngine.search(query, session.profile);
            session.lastResults = allResults;  // 暫存結果於 Session 供後續使用

            // 處理分頁邏輯
            int pageSize = 10;
            int totalCount = allResults.size();
            int totalPages = (int) Math.ceil(totalCount / (double) pageSize);
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);
            List<PageNode> pageResults = (startIndex < totalCount) 
                    ? allResults.subList(startIndex, endIndex)
                    : new ArrayList<>();

            long duration = System.currentTimeMillis() - startTime;

            // 構建回傳的 JSON 結構
            JsonObject root = new JsonObject();
            root.addProperty("success", true);
            root.addProperty("query", query);
            root.addProperty("city", city);
            root.addProperty("page", page);
            root.addProperty("totalCount", totalCount);
            root.addProperty("totalPages", totalPages);
            root.addProperty("responseTime", duration);
            root.addProperty("cached", false);

            JsonArray resultsArr = new JsonArray();
            int rank = startIndex + 1;
            for (PageNode p : pageResults) {
                JsonObject obj = new JsonObject();
                obj.addProperty("rank", rank++);
                obj.addProperty("title", JsonUtil.safe(p.getTitle()));
                obj.addProperty("url", JsonUtil.safe(p.getUrl()));
                obj.addProperty("domain", JsonUtil.safe(p.getDomain()));
                obj.addProperty("score", JsonUtil.round1(p.getTotalScore()));
                obj.addProperty("subPageCount", p.getSubPageCount());
                obj.addProperty("city", JsonUtil.safe(p.getCity()));
                obj.addProperty("eventDate", p.getEventDate() == null ? "" : p.getEventDate().toString());
                obj.addProperty("crawled", p.isCrawled());
                resultsArr.add(obj);
            }
            root.add("results", resultsArr);

            // 將結果存入快取並回傳
            String jsonResponse = root.toString();
            CacheManager.put(cacheKey, jsonResponse);
            HttpUtil.sendJson(ex, ctx, 200, jsonResponse);
            Logger.info(String.format("搜尋完成: query=\"%s\", city=\"%s\", results=%d, time=%dms",
                                       query, city, totalCount, duration));

        } catch (Exception e) {
            Logger.error("搜尋過程發生錯誤", e);
            HttpUtil.sendError(ex, ctx, 500, "搜尋服務發生內部錯誤，請稍後再試");
        }
    }
}
