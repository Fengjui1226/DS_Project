package app.web;

import app.bl.Keyword;
import app.bl.PageNode;
import app.bl.SearchEngine;
import app.bl.SemanticAnalyzer;
import app.bl.Tree;
import app.bl.UserProfile;
import java.util.LinkedHashMap;
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
        sb.append("<style>");
        sb.append("body{font-family:sans-serif;background:#fffbe6;padding:24px}");
        sb.append(".container{max-width:1200px;margin:0 auto}");
        sb.append(".main{display:flex;gap:20px}");
        sb.append(".results{flex:2}");
        sb.append(".sidebar{flex:1}");
        sb.append(".card{background:white;padding:12px;margin:8px 0;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.1)}");
        sb.append(".score{background:#FFEB3B;padding:4px 8px;border-radius:4px;font-weight:bold;float:right}");
        sb.append(".meta{color:#666;font-size:13px}");
        sb.append(".url{color:#0a6;font-size:12px;word-break:break-all}");
        sb.append("a{color:#1a0dab;text-decoration:none}");
        sb.append("a:hover{text-decoration:underline}");
        sb.append(".nav{margin:20px 0}");
        sb.append(".btn{background:#FFEB3B;border:none;padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#333}");
        sb.append(".panel{background:white;padding:16px;border-radius:8px;margin-bottom:16px;box-shadow:0 2px 8px rgba(0,0,0,0.1)}");
        sb.append(".panel h3{margin:0 0 12px 0;font-size:14px}");
        sb.append(".panel ul{margin:0;padding-left:20px;font-size:13px}");
        sb.append(".panel li{margin:4px 0}");
        sb.append(".tree-item{font-family:monospace;font-size:12px;margin:4px 0}");
        sb.append("@media(max-width:900px){.main{flex-direction:column}.sidebar{order:-1}}");
        sb.append("</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>搜尋結果：" + escapeHtml(query) + "</h2>");
        sb.append("<div class=\"nav\"><a href=\"/\" class=\"btn\">← 返回</a></div>");

        sb.append("<div class=\"main\">");
        
        // 左側：搜尋結果
        sb.append("<div class=\"results\">");
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
        sb.append("</div>");
        
        // 右側：側邊欄（樹狀結構 + 語意分析）
        sb.append("<div class=\"sidebar\">");
        
        Tree tree = SearchEngine.getLastSearchTree();
        if (tree != null) {
            // 樹狀結構面板
            sb.append("<div class=\"panel\">");
            sb.append("<h3>📊 網站結構 (Stage 2)</h3>");
            List<PageNode> pages = tree.getAllPagesSorted();
            Map<String, List<PageNode>> byDomain = new LinkedHashMap<>();
            for (PageNode p : pages) {
                byDomain.computeIfAbsent(p.getDomain(), k -> new ArrayList<>()).add(p);
            }
            for (Map.Entry<String, List<PageNode>> entry : byDomain.entrySet()) {
                double total = entry.getValue().stream().mapToDouble(PageNode::getScore).sum();
                sb.append("<div class=\"tree-item\"><strong>" + escapeHtml(entry.getKey()) + "</strong> (" + String.format("%.1f", total) + ")</div>");
                // 顯示關鍵字
                Map<String, Integer> kwCount = new HashMap<>();
                for (PageNode p : entry.getValue()) {
                    for (Map.Entry<Keyword, Integer> kw : p.tf().entrySet()) {
                        kwCount.put(kw.getKey().name(), kwCount.getOrDefault(kw.getKey().name(), 0) + kw.getValue());
                    }
                }
                if (!kwCount.isEmpty()) {
                    sb.append("<div style=\"font-size:11px;color:#666;margin-left:12px\">");
                    int count = 0;
                    for (Map.Entry<String, Integer> kw : kwCount.entrySet()) {
                        if (count++ > 0) sb.append(", ");
                        sb.append(kw.getKey() + "(" + kw.getValue() + ")");
                        if (count >= 3) break;
                    }
                    sb.append("</div>");
                }
            }
            sb.append("</div>");
            
            // 語意分析面板
            sb.append("<div class=\"panel\">");
            sb.append("<h3>🧠 語意分析 (Stage 4)</h3>");
            List<String> extracted = SemanticAnalyzer.extractRelatedKeywords(pages);
            sb.append("<div style=\"font-size:12px;margin-bottom:8px\"><strong>提取的關鍵字:</strong></div>");
            sb.append("<ul>");
            for (String kw : extracted) {
                sb.append("<li>" + escapeHtml(kw) + "</li>");
            }
            sb.append("</ul>");
            
            List<String> suggested = SemanticAnalyzer.suggestNewKeywords(extracted);
            if (!suggested.isEmpty()) {
                sb.append("<div style=\"font-size:12px;margin:8px 0\"><strong>建議關鍵字:</strong></div>");
                sb.append("<ul>");
                int count = 0;
                for (String kw : suggested) {
                    sb.append("<li>" + escapeHtml(kw) + "</li>");
                    if (++count >= 5) break;
                }
                sb.append("</ul>");
            }
            sb.append("</div>");
        }
        
        sb.append("</div>"); // sidebar
        sb.append("</div>"); // main
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