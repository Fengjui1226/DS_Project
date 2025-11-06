package app.web;

import app.bl.PageNode;
import app.bl.SearchEngine;
import app.bl.UserProfile;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SimpleServer {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", SimpleServer::handleIndex);
        server.createContext("/search", SimpleServer::handleSearch);
        server.setExecutor(null);
        server.start();
        System.out.println("SimpleServer started at http://localhost:" + port);
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        String html = "<!doctype html>\n" +
                "<html lang=\"zh-TW\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                "  <title>活動搜尋引擎（輕量版）</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "<h1>活動搜尋（輕量版）</h1>\n" +
                "<form action=\"/search\" method=\"get\">\n" +
                "  <input name=\"query\" placeholder=\"輸入關鍵字，例如：台北 音樂 活動\" style=\"width:60%\"/>\n" +
                "  <select name=\"city\">\n" +
                "    <option value=\"台北\">台北</option>\n" +
                "    <option value=\"新北\">新北</option>\n" +
                "    <option value=\"台中\">台中</option>\n" +
                "    <option value=\"高雄\">高雄</option>\n" +
                "  </select>\n" +
                "  <button type=\"submit\">搜尋</button>\n" +
                "</form>\n" +
                "<p>提示：這是臨時內建伺服器，用於快速測試 UI，資料由現有搜尋引擎產生。</p>\n" +
                "</body>\n" +
                "</html>\n";
        sendHtml(ex, html);
    }

    private static void handleSearch(HttpExchange ex) throws IOException {
        String query = getQueryParam(ex.getRequestURI(), "query");
        String city = getQueryParam(ex.getRequestURI(), "city");
        if (city == null) city = "台北";
        if (query == null || query.trim().isEmpty()) query = "台北 音樂 活動";

        // Run search using existing SearchEngine
        UserProfile user = new UserProfile();
        user.setUserCity(city);
        List<PageNode> results = Collections.emptyList();
        try {
            results = SearchEngine.search(query, user);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sendHtml(ex, "<pre>搜尋錯誤:\n" + escapeHtml(sw.toString()) + "</pre>");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"zh-TW\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>搜尋結果</title></head><body>");
        sb.append("<a href=\"/\">← 回到搜尋</a>");
        sb.append("<h1>搜尋：" + escapeHtml(query) + "（城市：" + escapeHtml(city) + "）</h1>");
        if (results == null || results.isEmpty()) {
            sb.append("<p>沒有結果。</p>");
        } else {
            sb.append("<ol>");
            for (PageNode p : results) {
                sb.append("<li>");
                sb.append("<a href=\"" + escapeHtml(p.getUrl() == null ? "" : p.getUrl()) + "\" target=\"_blank\">" + escapeHtml(p.getTitle() == null ? "(無標題)" : p.getTitle()) + "</a>");
                sb.append(" <span style=\"color:#666\">[分數:" + String.format("%.2f", p.getScore()) + "]</span>");
                if (p.getCity() != null) sb.append(" <small>" + escapeHtml(p.getCity()) + "</small>");
                sb.append("<div style=\"color:#006621\">" + escapeHtml(p.getUrl() == null ? "" : p.getUrl()) + "</div>");
                sb.append("</li>");
            }
            sb.append("</ol>");
        }
        sb.append("</body></html>");

        sendHtml(ex, sb.toString());
    }

    private static String getQueryParam(URI uri, String name) {
        String raw = uri.getRawQuery();
        if (raw == null) return null;
        String[] parts = raw.split("&");
        for (String p : parts) {
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            String k = urlDecode(p.substring(0, eq));
            String v = urlDecode(p.substring(eq + 1));
            if (name.equals(k)) return v;
        }
        return null;
    }

    private static String urlDecode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } catch (Exception e) { return s; }
    }

    private static void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}