package app.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import app.bl.InputValidator;

public class HttpUtil {

    private HttpUtil() {}

    public static boolean handleOptions(HttpExchange ex, ServerContext ctx) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCorsHeaders(ex, ctx);
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    public static void setCorsHeaders(HttpExchange ex, ServerContext ctx) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", ctx.corsOrigin);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", ctx.corsMethods);
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", ctx.corsHeaders);
        ex.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    public static void sendJson(HttpExchange ex, ServerContext ctx, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        setCorsHeaders(ex, ctx);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        OutputStream os = null;
        try {
            os = ex.getResponseBody();
            os.write(bytes);
        } finally {
            if (os != null) os.close();
        }
    }

    public static void sendError(HttpExchange ex, ServerContext ctx, int status, String message) throws IOException {
        String json = String.format(
                "{\"success\":false,\"error\":\"%s\",\"code\":%d}",
                InputValidator.escapeJson(message), status
        );
        sendJson(ex, ctx, status, json);
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<String, String>();
        if (query == null || query.isEmpty()) return params;

        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (Exception e) {
                    params.put(kv[0], kv[1]);
                }
            }
        }
        return params;
    }
}
