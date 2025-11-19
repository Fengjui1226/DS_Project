package app.web;

import app.bl.PageNode;
import app.bl.SearchEngine;
import app.bl.SemanticAnalyzer;
import app.bl.Tree;
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
        server.createContext("/tree", SimpleServer::handleTree);
        server.createContext("/semantic", SimpleServer::handleSemantic);
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
            "  <title>活動搜尋引擎</title>\n" +
            "  <style>\n" +
            "    body{font-family:sans-serif;background:#fffbe6;margin:0;padding:24px}\n" +
            "    .container{max-width:800px;margin:0 auto}\n" +
            "    h1{color:#333}\n" +
            "    .search-box{display:flex;gap:8px;margin:20px 0}\n" +
            "    input{flex:1;padding:12px;border:1px solid #ddd;border-radius:8px}\n" +
            "    select{padding:12px;border-radius:8px}\n" +
            "    button{background:#FFEB3B;border:none;padding:12px 24px;border-radius:8px;cursor:pointer;font-weight:bold}\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class=\"container\">\n" +
            "  <h1>🎯 全台娛樂活動搜尋引擎</h1>\n" +
            "  <form class=\"search-box\" action=\"/search\" method=\"get\">\n" +
            "    <input name=\"query\" placeholder=\"輸入關鍵字，例如：台北 音樂 活動\" />\n" +
            "    <select name=\"city\">\n" +
            "      <option value=\"台北\">台北</option>\n" +
            "      <option value=\"新北\">新北</option>\n" +
            "      <option value=\"桃園\">桃園</option>\n" +
            "      <option value=\"台中\">台中</option>\n" +
            "      <option value=\"台南\">台南</option>\n" +
            "      <option value=\"高雄\">高雄</option>\n" +
            "    </select>\n" +
            "    <button type=\"submit\">搜尋</button>\n" +
            "  </form>\n" +
            "</div>\n" +
            "</body></html>";
        sendHtml(ex, html);
    }

    private static void handleSearch(HttpExchange ex) throws IOException {
        String query = getQueryParam(ex.getRequestURI(), "query");
        String city = getQueryParam(ex.getRequestURI(), "city");
        if (city == null) city = "台北";
        if (query == null || query.trim().isEmpty()) query = "台北 音樂 活動";

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
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>搜尋結果</title>");
        sb.append("<style>body{font-family:sans-serif;background:#fffbe6;padding:24px}.container{max-width:900px;margin:0 auto}.card{background:white;padding:12px;margin:8px 0;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.1)}.score{background:#FFEB3B;padding:4px 8px;border-radius:4px;font-weight:bold;float:right}.meta{color:#666;font-size:13px}.url{color:#0a6;font-size:12px;word-break:break-all}a{color:#1a0dab;text-decoration:none}a:hover{text-decoration:underline}.nav{margin:20px 0}.btn{background:#FFEB3B;border:none;padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#333}</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>搜尋結果：" + escapeHtml(query) + "</h2>");
        sb.append("<div class=\"nav\"><a href=\"/\" class=\"btn\">← 返回</a> <a href=\"/tree\" class=\"btn\">📊 樹狀結構</a> <a href=\"/semantic\" class=\"btn\">🧠 語意分析</a></div>");

        if (results.isEmpty()) {
            sb.append("<p>沒有找到結果</p>");
        } else {
            int rank = 1;
            for (PageNode p : results) {
                sb.append("<div class=\"card\">");
                sb.append("<span class=\"score\">" + String.format("%.2f", p.getScore()) + "</span>");
                sb.append("<div><strong>#" + rank++ + "</strong> <a href=\"" + escapeHtml(p.getUrl()) + "\" target=\"_blank\">" + escapeHtml(p.getTitle()) + "</a></div>");
                sb.append("<div class=\"meta\">");
                if (p.getCity() != null && !p.getCity().isEmpty()) sb.append("📍 " + escapeHtml(p.getCity()) + " ");
                if (p.getEventDate() != null) sb.append("📅 " + p.getEventDate() + " ");
                sb.append("🌐 " + escapeHtml(p.getDomain()));
                sb.append("</div>");
                sb.append("<div class=\"url\">" + escapeHtml(p.getUrl()) + "</div>");
                sb.append("</div>");
            }
        }
        sb.append("</div></body></html>");
        sendHtml(ex, sb.toString());
    }

    private static void handleTree(HttpExchange ex) throws IOException {
        Tree tree = SearchEngine.getLastSearchTree();
        
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>樹狀結構</title>");
        sb.append("<style>body{font-family:monospace;background:#fffbe6;padding:24px}.container{max-width:900px;margin:0 auto}pre{background:white;padding:16px;border-radius:8px;overflow-x:auto}.btn{background:#FFEB3B;border:none;padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#333}</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>📊 網站樹狀結構 (Stage 2)</h2>");
        sb.append("<div style=\"margin:20px 0\"><a href=\"/\" class=\"btn\">← 返回搜尋</a></div>");
        
        if (tree == null) {
            sb.append("<p>請先進行搜尋</p>");
        } else {
            sb.append("<pre>" + escapeHtml(tree.getTreeDisplay()) + "</pre>");
        }
        
        sb.append("</div></body></html>");
        sendHtml(ex, sb.toString());
    }

    private static void handleSemantic(HttpExchange ex) throws IOException {
        Tree tree = SearchEngine.getLastSearchTree();
        
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>語意分析</title>");
        sb.append("<style>body{font-family:sans-serif;background:#fffbe6;padding:24px}.container{max-width:900px;margin:0 auto}pre{background:white;padding:16px;border-radius:8px;overflow-x:auto}.btn{background:#FFEB3B;border:none;padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#333}</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>🧠 語意分析 (Stage 4)</h2>");
        sb.append("<div style=\"margin:20px 0\"><a href=\"/\" class=\"btn\">← 返回搜尋</a></div>");
        
        if (tree == null) {
            sb.append("<p>請先進行搜尋</p>");
        } else {
            List<PageNode> pages = tree.getAllPagesSorted();
            String report = SemanticAnalyzer.getAnalysisReport(pages, "上次搜尋");
            sb.append("<pre>" + escapeHtml(report) + "</pre>");
        }
        
        sb.append("</div></body></html>");
        sendHtml(ex, sb.toString());
    }

    private static String getQueryParam(URI uri, String name) {
        String raw = uri.getRawQuery();
        if (raw == null) return null;
        for (String p : raw.split("&")) {
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
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static void handleStatic(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(404, -1);
    }
}