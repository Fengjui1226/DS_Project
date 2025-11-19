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
        server.createContext("/compare", SimpleServer::handleCompare);
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
        sb.append(".btn{background:#FFEB3B;border:none;padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#333;margin-right:8px}");
        sb.append(".panel{background:white;padding:16px;border-radius:8px;margin-bottom:16px;box-shadow:0 2px 8px rgba(0,0,0,0.1)}");
        sb.append(".panel h3{margin:0 0 12px 0;font-size:14px}");
        sb.append(".panel ul{margin:0;padding-left:20px;font-size:13px}");
        sb.append(".panel li{margin:4px 0}");
        sb.append(".tree-item{font-family:monospace;font-size:12px;margin:4px 0}");
        sb.append("@media(max-width:900px){.main{flex-direction:column}.sidebar{order:-1}}");
        sb.append("</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>搜尋結果：" + escapeHtml(query) + "</h2>");
        sb.append("<div class=\"nav\"><a href=\"/\" class=\"btn\">← 返回</a><a href=\"/compare\" class=\"btn\">🤖 LLM比較</a></div>");

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
            sb.append("<h3>📊 網站結構</h3>");
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
            sb.append("<h3>🧠 語意分析</h3>");
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

    private static void handleCompare(HttpExchange ex) throws IOException {
        Tree tree = SearchEngine.getLastSearchTree();
        
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>LLM 比較</title>");
        sb.append("<style>");
        sb.append("body{font-family:sans-serif;background:#fffbe6;padding:24px}");
        sb.append(".container{max-width:1000px;margin:0 auto}");
        sb.append(".btn{background:#FFEB3B;border:none;padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#333}");
        sb.append(".panel{background:white;padding:16px;border-radius:8px;margin:16px 0;box-shadow:0 2px 8px rgba(0,0,0,0.1)}");
        sb.append("pre{background:#f5f5f5;padding:12px;border-radius:4px;overflow-x:auto;font-size:13px;white-space:pre-wrap}");
        sb.append(".compare-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}");
        sb.append("@media(max-width:800px){.compare-grid{grid-template-columns:1fr}}");
        sb.append("</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>🤖 LLM 比較 (Stage 6)</h2>");
        sb.append("<div style=\"margin:20px 0\"><a href=\"/\" class=\"btn\">← 返回搜尋</a></div>");
        
        if (tree == null) {
            sb.append("<p>請先進行搜尋，再來比較結果</p>");
        } else {
            List<PageNode> pages = tree.getAllPagesSorted();
            
            // 產生 Prompt
            sb.append("<div class=\"panel\">");
            sb.append("<h3>📝 搜尋技巧 Prompt</h3>");
            sb.append("<p>複製以下 prompt 到 ChatGPT / Gemini / DeepSeek：</p>");
            sb.append("<pre>");
            sb.append("請幫我找台灣的娛樂活動，需求如下：\n\n");
            sb.append("1. 只要今天之後的活動（過期的不要）\n");
            sb.append("2. 優先顯示官方網站（.gov.tw, .org）\n");
            sb.append("3. 依照以下關鍵字權重排序：\n");
            sb.append("   - 活動(6分), 展覽(5分), 音樂(4分)\n");
            sb.append("   - 市集(3分), 體驗(3分)\n");
            sb.append("4. 優先顯示在我所在城市的活動\n");
            sb.append("5. 提供活動名稱、日期、地點、網址\n\n");
            sb.append("請搜尋：台北 音樂 活動");
            sb.append("</pre>");
            sb.append("</div>");
            
            // 比較表格
            sb.append("<div class=\"compare-grid\">");
            
            // 我們的結果
            sb.append("<div class=\"panel\">");
            sb.append("<h3>🎯 我們的搜尋引擎結果</h3>");
            sb.append("<ol style=\"font-size:13px\">");
            int count = 0;
            for (PageNode p : pages) {
                if (++count > 5) break;
                sb.append("<li><strong>" + escapeHtml(p.getTitle()) + "</strong><br>");
                sb.append("<span style=\"color:#666\">分數: " + String.format("%.1f", p.getScore()));
                if (p.getCity() != null && !p.getCity().isEmpty()) {
                    sb.append(" | " + escapeHtml(p.getCity()));
                }
                sb.append("</span></li>");
            }
            sb.append("</ol>");
            sb.append("</div>");
            
            // LLM 結果
            sb.append("<div class=\"panel\">");
            sb.append("<h3>🤖 LLM 結果（手動比較）</h3>");
            sb.append("<p style=\"font-size:13px;color:#666\">將 LLM 的回答貼在報告中，比較：</p>");
            sb.append("<ul style=\"font-size:13px\">");
            sb.append("<li>結果相關性</li>");
            sb.append("<li>是否過濾過期活動</li>");
            sb.append("<li>是否優先官方網站</li>");
            sb.append("<li>排序邏輯是否清楚</li>");
            sb.append("</ul>");
            sb.append("</div>");
            
            sb.append("</div>");
            
            // 優勢說明
            sb.append("<div class=\"panel\">");
            sb.append("<h3>💡 我們的優勢</h3>");
            sb.append("<ul style=\"font-size:13px\">");
            sb.append("<li><strong>透明的排名公式</strong> - 使用者可以理解為什麼結果這樣排序</li>");
            sb.append("<li><strong>自動過濾過期活動</strong> - 只顯示今天及未來的活動</li>");
            sb.append("<li><strong>網站可信度加成</strong> - 官方網站 (.gov.tw) 優先</li>");
            sb.append("<li><strong>地區相關性</strong> - 根據使用者城市調整排名</li>");
            sb.append("<li><strong>個人化推薦</strong> - 記錄使用者偏好，調整關鍵字權重</li>");
            sb.append("<li><strong>語意擴展</strong> - 自動建議相關關鍵字</li>");
            sb.append("</ul>");
            sb.append("</div>");
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