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
        server.createContext("/static/", SimpleServer::handleStatic);
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
        "  <style>\n" +
        "    :root{--yellow:#FFEB3B;--accent:#f7c600;--bg:#fff8e1;--muted:#6b6b6b}body{font-family: 'Segoe UI', Roboto, 'Microsoft JhengHei', sans-serif;background:#fffbe6;margin:0;padding:24px;color:#222}a{color:inherit} .container{max-width:980px;margin:0 auto}header{display:flex;align-items:center;gap:16px} .logo{width:56px;height:56px;border-radius:10px;background:linear-gradient(135deg,var(--yellow),var(--accent));box-shadow:0 6px 18px rgba(0,0,0,0.08);display:flex;align-items:center;justify-content:center;font-weight:700;color:#222}h1{font-size:20px;margin:0} .hero{background:linear-gradient(180deg,rgba(255,235,59,0.12),transparent);border-radius:12px;padding:20px;margin-top:18px;display:flex;flex-direction:column;align-items:center} .search-row{width:100%;display:flex;gap:8px;align-items:center} input.search{flex:1;padding:12px 14px;border-radius:8px;border:1px solid #f0d96a;box-shadow:inset 0 1px 0 rgba(255,255,255,0.6)} select.city{padding:10px;border-radius:8px;border:1px solid #f0d96a;background:white} button.btn{background:var(--yellow);border:none;padding:10px 16px;border-radius:8px;cursor:pointer;font-weight:600} footer{margin-top:18px;color:var(--muted);font-size:13px;text-align:center} @media(max-width:640px){ .search-row{flex-direction:column} input.search{width:100%} }\n" +
        "  </style>\n" +
        "</head>\n" +
        "<body>\n" +
        "<div class=\"container\">\n" +
        "  <header>\n" +
        "    <div class=\"logo\">活動</div>\n" +
        "    <div>\n" +
        "      <h1>活動搜尋引擎（輕量版）</h1>\n" +
        "      <div style=\"color:#555;font-size:13px;margin-top:4px\">快速查看活動搜尋結果</div>\n" +
        "    </div>\n" +
        "  </header>\n" +
        "  <div class=\"hero\">\n" +
        "    <form class=\"search-row\" action=\"/search\" method=\"get\">\n" +
        "      <input class=\"search\" name=\"query\" placeholder=\"輸入關鍵字，例如：台北 音樂 活動\" />\n" +
        "      <select class=\"city\" name=\"city\">\n" +
        "        <option value=\"台北\">台北</option>\n" +
        "        <option value=\"新北\">新北</option>\n" +
        "        <option value=\"桃園\">桃園</option>\n" +
        "        <option value=\"台中\">台中</option>\n" +
        "        <option value=\"高雄\">高雄</option>\n" +
        "      </select>\n" +
        "      <button class=\"btn\" type=\"submit\">搜尋</button>\n" +
        "    </form>\n" +
        "    <footer>提示：這是臨時內建伺服器，用於快速測試 UI；結果由現有搜尋引擎產生。</footer>\n" +
        "  </div>\n" +
        "</div>\n" +
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
        sb.append("<!doctype html><html lang=\"zh-TW\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>搜尋結果</title>");
    sb.append("<link rel=\"stylesheet\" href=\"/static/style.css\">");
    sb.append("</head><body><div class=\"container\"> ");
        sb.append("<header><div class=\"logo\">活動</div><div><h2 style=\"margin:0\">搜尋結果</h2><div style=\"color:#555;font-size:13px\">查詢：" + escapeHtml(query) + "（城市：" + escapeHtml(city) + "）</div></div></header>");
        sb.append("<div class=\"topbar\"> <div class=\"search-inline\"><form action=\"/search\" method=\"get\"><input class=\"search\" name=\"query\" value=\"" + escapeHtml(query) + "\" /> <select class=\"city\" name=\"city\"> <option value=\"台北\"" + ("台北".equals(city)?" selected":"") + ">台北</option> <option value=\"新北\"" + ("新北".equals(city)?" selected":"") + ">新北</option> <option value=\"桃園\"" + ("桃園".equals(city)?" selected":"") + ">桃園</option> <option value=\"台中\"" + ("台中".equals(city)?" selected":"") + ">台中</option> <option value=\"高雄\"" + ("高雄".equals(city)?" selected":"") + ">高雄</option> </select> <button class=\"btn\" type=\"submit\">搜尋</button></form></div> <div><a href=\"/\">← 回到搜尋頁</a></div></div>");
        if (results == null || results.isEmpty()) {
            sb.append("<div style=\"margin-top:24px;color:var(--muted)\">沒有找到相關結果。</div>");
        } else {
            sb.append("<div class=\"results\">\n");
            for (PageNode p : results) {
                String title = escapeHtml(p.getTitle() == null ? "(無標題)" : p.getTitle());
                String url = escapeHtml(p.getUrl() == null ? "" : p.getUrl());
                String cityTag = p.getCity() == null ? "" : escapeHtml(p.getCity());
                String score = String.format("%.2f", p.getScore());
                sb.append("<div class=\"card\">\n");
                sb.append("  <div class=\"title\"><a href=\"" + url + "\" target=\"_blank\">" + title + "</a><span class=\"badge\">" + score + "</span></div>\n");
                if (!cityTag.isEmpty()) sb.append("  <div class=\"meta\">城市：" + cityTag + "</div>\n");
                sb.append("  <div class=\"url\">" + url + "</div>\n");
                sb.append("</div>\n");
            }
            sb.append("</div>");
        }
        sb.append("</div></body></html>");

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

    // Serve static files located under src/main/resources/static (packaged to classpath /static)
    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.startsWith("/static/")) path = path.substring("/static/".length());
        if (path.contains("..")) { ex.sendResponseHeaders(403, -1); return; }

        InputStream is = SimpleServer.class.getResourceAsStream("/static/" + path);
        if (is == null) {
            // try file-system fallback (during development)
            File f = new File("src/main/resources/static/" + path);
            if (f.exists() && f.isFile()) {
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                Headers h = ex.getResponseHeaders();
                h.set("Content-Type", guessContentType(path));
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
                return;
            }
            ex.sendResponseHeaders(404, -1);
            return;
        }

        byte[] bytes = is.readAllBytes();
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", guessContentType(path));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String guessContentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}