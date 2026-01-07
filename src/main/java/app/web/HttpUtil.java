package app.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/**
 * HTTP 諮詢與回應相關的工具方法類別 (處理 CORS、回傳 JSON 等)
 */
public class HttpUtil {

    // 私有建構子避免實例化
    private HttpUtil() {}

    /**
     * 處理 HTTP OPTIONS 預檢請求。如果請求方法是 OPTIONS，則設定 CORS 表頭並回傳 204，然後回傳 true 表示已處理。
     */
    public static boolean handleOptions(HttpExchange ex, ServerContext ctx) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex, ctx);
            // 回傳 204 No Content 並不帶有 body
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    /**
     * 將 CORS 所需的表頭加入回應中。
     */
    public static void setCorsHeaders(HttpExchange ex, ServerContext ctx) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", ctx.corsOrigin);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", ctx.corsMethods);
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", ctx.corsHeaders);
        ex.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    /**
     * 回傳 JSON 並正確設定 CORS 和 Content-Type 表頭。
     */
    public static void sendJson(HttpExchange ex, ServerContext ctx, int statusCode, String jsonBody) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        setCorsHeaders(ex, ctx);
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 回傳錯誤訊息的 JSON。格式：{"success": false, "error": "...", "code": ...}
     */
    public static void sendError(HttpExchange ex, ServerContext ctx, int statusCode, String message) throws IOException {
        // 將錯誤訊息內容做必要的 JSON 特殊字元跳脫
        String safeMsg = app.bl.InputValidator.escapeJson(message);
        String json = String.format("{\"success\":false,\"error\":\"%s\",\"code\":%d}", safeMsg, statusCode);
        sendJson(ex, ctx, statusCode, json);
    }

    /**
     * 解析 URL 查詢字串參數為 Map。自動對參數值進行 URL decode (UTF-8)。
     */
    public static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (Exception e) {
                    // 發生 decode 異常時，直接使用原始值（不太可能發生）
                    params.put(kv[0], kv[1]);
                }
            }
        }
        return params;
    }
}
