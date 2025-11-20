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
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>EventFinder 台灣活動搜尋</title>\n" +
            "  <link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+TC:wght@400;500;700&display=swap\" rel=\"stylesheet\">\n" +
            "  <style>\n" +
            "    *{box-sizing:border-box;margin:0;padding:0}\n" +
            "    body{font-family:'Noto Sans TC',sans-serif;background:#0f0f0f;color:#fff;min-height:100vh;overflow-x:hidden}\n" +
            "    .bg{position:fixed;top:0;left:0;width:100%;height:100%;z-index:-1}\n" +
            "    .bg::before{content:'';position:absolute;top:-50%;left:-50%;width:200%;height:200%;background:radial-gradient(circle at 30% 70%,rgba(255,107,107,0.08) 0%,transparent 50%),radial-gradient(circle at 70% 30%,rgba(78,205,196,0.08) 0%,transparent 50%)}\n" +
            "    .container{max-width:700px;width:90%;margin:0 auto;padding:60px 0;min-height:100vh;display:flex;flex-direction:column;justify-content:center}\n" +
            "    .brand{text-align:center;margin-bottom:48px}\n" +
            "    .logo{font-size:64px;margin-bottom:16px;filter:drop-shadow(0 0 30px rgba(255,107,107,0.3))}\n" +
            "    h1{font-size:42px;font-weight:700;background:linear-gradient(135deg,#fff 0%,#a0a0a0 100%);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;letter-spacing:-1px}\n" +
            "    .tagline{color:#666;font-size:15px;margin-top:8px;letter-spacing:2px}\n" +
            "    .search-card{background:rgba(255,255,255,0.03);backdrop-filter:blur(20px);border:1px solid rgba(255,255,255,0.06);border-radius:24px;padding:32px;box-shadow:0 20px 60px rgba(0,0,0,0.3)}\n" +
            "    .categories{display:flex;gap:10px;flex-wrap:wrap;justify-content:center;margin-bottom:24px}\n" +
            "    .cat{background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);padding:8px 16px;border-radius:20px;font-size:13px;color:#888;cursor:pointer;transition:all 0.3s ease}\n" +
            "    .cat:hover{background:rgba(255,107,107,0.15);border-color:rgba(255,107,107,0.3);color:#ff6b6b;transform:translateY(-2px)}\n" +
            "    .input-row{display:flex;gap:12px;margin-bottom:20px}\n" +
            "    input{flex:1;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);padding:16px 20px;border-radius:12px;font-size:16px;color:#fff;transition:all 0.3s ease}\n" +
            "    input::placeholder{color:#555}\n" +
            "    input:focus{outline:none;border-color:rgba(255,107,107,0.5);box-shadow:0 0 20px rgba(255,107,107,0.1)}\n" +
            "    select{background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);padding:16px;border-radius:12px;font-size:14px;color:#fff;cursor:pointer;min-width:100px}\n" +
            "    select option{background:#1a1a1a;color:#fff}\n" +
            "    .btn-row{display:flex;gap:12px}\n" +
            "    button{flex:1;background:linear-gradient(135deg,#ff6b6b 0%,#ee5a5a 100%);border:none;padding:16px;border-radius:12px;font-size:16px;font-weight:600;color:#fff;cursor:pointer;transition:all 0.3s ease;box-shadow:0 4px 20px rgba(255,107,107,0.3)}\n" +
            "    button:hover{transform:translateY(-2px);box-shadow:0 8px 30px rgba(255,107,107,0.4)}\n" +
            "    .btn-locate{flex:0 0 auto;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);padding:16px 20px;border-radius:12px;font-size:16px;color:#888;cursor:pointer;transition:all 0.3s ease}\n" +
            "    .btn-locate:hover{background:rgba(78,205,196,0.15);border-color:rgba(78,205,196,0.3);color:#4ecdc4}\n" +
            "    .btn-locate.active{background:rgba(78,205,196,0.2);border-color:rgba(78,205,196,0.5);color:#4ecdc4}\n" +
            "    .features{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-top:48px}\n" +
            "    .feat{text-align:center;padding:20px}\n" +
            "    .feat-icon{font-size:28px;margin-bottom:12px}\n" +
            "    .feat-title{font-size:13px;font-weight:500;color:#fff;margin-bottom:4px}\n" +
            "    .feat-desc{font-size:11px;color:#555}\n" +
            "    .footer{text-align:center;margin-top:48px;color:#333;font-size:12px}\n" +
            "    .location-status{text-align:center;font-size:12px;color:#4ecdc4;margin-top:12px;min-height:18px}\n" +
            "    @media(max-width:600px){.input-row{flex-direction:column}select{width:100%}.features{grid-template-columns:1fr}h1{font-size:32px}.btn-row{flex-direction:column}}\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class=\"bg\"></div>\n" +
            "<div class=\"container\">\n" +
            "  <div class=\"brand\">\n" +
            "    <div class=\"logo\">🎪</div>\n" +
            "    <h1>EventFinder</h1>\n" +
            "    <p class=\"tagline\">DISCOVER EVENTS IN TAIWAN</p>\n" +
            "  </div>\n" +
            "  <div class=\"search-card\">\n" +
            "    <form action=\"/search\" method=\"get\">\n" +
            "      <div class=\"categories\">\n" +
            "        <span class=\"cat\" onclick=\"setQuery('音樂 演唱會')\">🎵 音樂</span>\n" +
            "        <span class=\"cat\" onclick=\"setQuery('展覽 藝術')\">🎨 展覽</span>\n" +
            "        <span class=\"cat\" onclick=\"setQuery('市集 文創')\">🛍️ 市集</span>\n" +
            "        <span class=\"cat\" onclick=\"setQuery('戶外 露營')\">⛺ 戶外</span>\n" +
            "        <span class=\"cat\" onclick=\"setQuery('親子 兒童')\">👶 親子</span>\n" +
            "      </div>\n" +
            "      <div class=\"input-row\">\n" +
            "        <input id=\"q\" name=\"query\" placeholder=\"搜尋音樂會、展覽、市集...\" autocomplete=\"off\" />\n" +
            "        <select id=\"city\" name=\"city\">\n" +
            "          <option value=\"台北\">台北</option>\n" +
            "          <option value=\"新北\">新北</option>\n" +
            "          <option value=\"桃園\">桃園</option>\n" +
            "          <option value=\"台中\">台中</option>\n" +
            "          <option value=\"台南\">台南</option>\n" +
            "          <option value=\"高雄\">高雄</option>\n" +
            "          <option value=\"基隆\">基隆</option>\n" +
            "          <option value=\"新竹\">新竹</option>\n" +
            "          <option value=\"苗栗\">苗栗</option>\n" +
            "          <option value=\"彰化\">彰化</option>\n" +
            "          <option value=\"南投\">南投</option>\n" +
            "          <option value=\"雲林\">雲林</option>\n" +
            "          <option value=\"嘉義\">嘉義</option>\n" +
            "          <option value=\"屏東\">屏東</option>\n" +
            "          <option value=\"宜蘭\">宜蘭</option>\n" +
            "          <option value=\"花蓮\">花蓮</option>\n" +
            "          <option value=\"台東\">台東</option>\n" +
            "        </select>\n" +
            "      </div>\n" +
            "      <div class=\"btn-row\">\n" +
            "        <button type=\"button\" class=\"btn-locate\" id=\"locateBtn\" onclick=\"getLocation()\">📍</button>\n" +
            "        <button type=\"submit\">探索活動</button>\n" +
            "      </div>\n" +
            "      <div class=\"location-status\" id=\"locStatus\"></div>\n" +
            "    </form>\n" +
            "  </div>\n" +
            "  <div class=\"features\">\n" +
            "    <div class=\"feat\">\n" +
            "      <div class=\"feat-icon\">⚡</div>\n" +
            "      <div class=\"feat-title\">即時更新</div>\n" +
            "      <div class=\"feat-desc\">自動過濾過期活動</div>\n" +
            "    </div>\n" +
            "    <div class=\"feat\">\n" +
            "      <div class=\"feat-icon\">🎯</div>\n" +
            "      <div class=\"feat-title\">精準排序</div>\n" +
            "      <div class=\"feat-desc\">AI 智慧權重計算</div>\n" +
            "    </div>\n" +
            "    <div class=\"feat\">\n" +
            "      <div class=\"feat-icon\">📍</div>\n" +
            "      <div class=\"feat-title\">在地優先</div>\n" +
            "      <div class=\"feat-desc\">依你的位置排序</div>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "  <div class=\"footer\">Built with ❤️ for Taiwan</div>\n" +
            "</div>\n" +
            "<script>\n" +
            "function setQuery(t){document.getElementById('q').value=t}\n" +
            "function getLocation(){\n" +
            "  var btn=document.getElementById('locateBtn');\n" +
            "  var status=document.getElementById('locStatus');\n" +
            "  if(!navigator.geolocation){status.textContent='瀏覽器不支援定位';return;}\n" +
            "  status.textContent='定位中...';\n" +
            "  btn.classList.add('active');\n" +
            "  navigator.geolocation.getCurrentPosition(function(pos){\n" +
            "    var lat=pos.coords.latitude;\n" +
            "    var lng=pos.coords.longitude;\n" +
            "    var city=detectCity(lat,lng);\n" +
            "    document.getElementById('city').value=city;\n" +
            "    status.textContent='已定位：'+city;\n" +
            "  },function(err){\n" +
            "    status.textContent='定位失敗：'+err.message;\n" +
            "    btn.classList.remove('active');\n" +
            "  });\n" +
            "}\n" +
            "function detectCity(lat,lng){\n" +
            "  if(lat>25.0&&lng>121.4&&lng<121.7) return '台北';\n" +
            "  if(lat>24.9&&lat<25.3&&lng>121.3&&lng<121.5) return '新北';\n" +
            "  if(lat>24.9&&lat<25.1&&lng>121.2&&lng<121.4) return '桃園';\n" +
            "  if(lat>24.0&&lat<24.3&&lng>120.5&&lng<120.8) return '台中';\n" +
            "  if(lat>22.9&&lat<23.1&&lng>120.1&&lng<120.3) return '台南';\n" +
            "  if(lat>22.5&&lat<22.8&&lng>120.2&&lng<120.4) return '高雄';\n" +
            "  if(lat>25.1&&lng>121.7) return '基隆';\n" +
            "  if(lat>24.7&&lat<24.9&&lng>120.9&&lng<121.1) return '新竹';\n" +
            "  if(lat>24.3&&lat<24.7&&lng>120.7&&lng<121.0) return '苗栗';\n" +
            "  if(lat>23.8&&lat<24.2&&lng>120.4&&lng<120.7) return '彰化';\n" +
            "  if(lat>23.8&&lat<24.1&&lng>120.6&&lng<121.0) return '南投';\n" +
            "  if(lat>23.5&&lat<23.9&&lng>120.1&&lng<120.6) return '雲林';\n" +
            "  if(lat>23.4&&lat<23.6&&lng>120.3&&lng<120.5) return '嘉義';\n" +
            "  if(lat>22.0&&lat<22.8&&lng>120.4&&lng<120.9) return '屏東';\n" +
            "  if(lat>24.4&&lat<24.8&&lng>121.5&&lng<121.9) return '宜蘭';\n" +
            "  if(lat>23.5&&lat<24.3&&lng>121.3&&lng<121.7) return '花蓮';\n" +
            "  if(lat>22.3&&lat<23.5&&lng>120.8&&lng<121.5) return '台東';\n" +
            "  return '台北';\n" +
            "}\n" +
            "</script>\n" +
            "</body></html>";
        sendHtml(ex, html);
    }

    private static void handleSearch(HttpExchange ex) throws IOException {
        String query = getQueryParam(ex.getRequestURI(), "query");
        String city = getQueryParam(ex.getRequestURI(), "city");
        if (city == null) city = "台北";
        if (query == null || query.trim().isEmpty()) query = "音樂 活動";

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
        sb.append("body{font-family:'Noto Sans TC',sans-serif;background:#0f0f0f;color:#fff;padding:24px;min-height:100vh}");
        sb.append(".container{max-width:1200px;margin:0 auto}");
        sb.append(".main{display:flex;gap:20px}");
        sb.append(".results{flex:2}");
        sb.append(".sidebar{flex:1}");
        sb.append(".card{background:rgba(255,255,255,0.05);padding:16px;margin:12px 0;border-radius:12px;border:1px solid rgba(255,255,255,0.1)}");
        sb.append(".score{background:#ff6b6b;padding:4px 10px;border-radius:6px;font-weight:bold;float:right;font-size:14px}");
        sb.append(".meta{color:#888;font-size:13px;margin-top:8px}");
        sb.append(".url{color:#4ecdc4;font-size:12px;word-break:break-all;margin-top:4px}");
        sb.append("a{color:#fff;text-decoration:none}");
        sb.append("a:hover{color:#ff6b6b}");
        sb.append(".nav{margin:20px 0}");
        sb.append(".btn{background:rgba(255,255,255,0.1);border:1px solid rgba(255,255,255,0.2);padding:10px 20px;border-radius:8px;cursor:pointer;text-decoration:none;color:#fff;margin-right:8px;transition:all 0.3s}");
        sb.append(".btn:hover{background:rgba(255,107,107,0.2);border-color:rgba(255,107,107,0.3)}");
        sb.append(".panel{background:rgba(255,255,255,0.03);padding:16px;border-radius:12px;margin-bottom:16px;border:1px solid rgba(255,255,255,0.06)}");
        sb.append(".panel h3{margin:0 0 12px 0;font-size:14px;color:#ff6b6b}");
        sb.append(".panel ul{margin:0;padding-left:20px;font-size:13px;color:#888}");
        sb.append(".panel li{margin:4px 0}");
        sb.append(".tree-item{font-family:monospace;font-size:12px;margin:4px 0;color:#888}");
        sb.append("h2{color:#fff;font-size:24px;margin-bottom:8px}");
        sb.append(".query-info{color:#888;font-size:14px;margin-bottom:20px}");
        sb.append("@media(max-width:900px){.main{flex-direction:column}.sidebar{order:-1}}");
        sb.append("</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>搜尋結果</h2>");
        sb.append("<div class=\"query-info\">關鍵字：" + escapeHtml(query) + " | 城市：" + escapeHtml(city) + "</div>");
        sb.append("<div class=\"nav\"><a href=\"/\" class=\"btn\">← 返回首頁</a></div>");

        sb.append("<div class=\"main\">");
        
        // 左側：搜尋結果
        sb.append("<div class=\"results\">");
        if (results.isEmpty()) {
            sb.append("<p style=\"color:#888\">沒有找到結果</p>");
        } else {
            int rank = 1;
            for (PageNode p : results) {
                sb.append("<div class=\"card\">");
                sb.append("<span class=\"score\">" + String.format("%.1f", p.getScore()) + "</span>");
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
        
        // 右側：側邊欄
        sb.append("<div class=\"sidebar\">");
        
        Tree tree = SearchEngine.getLastSearchTree();
        if (tree != null) {
            sb.append("<div class=\"panel\">");
            sb.append("<h3>📊 網站結構</h3>");
            List<PageNode> pages = tree.getAllPagesSorted();
            Map<String, List<PageNode>> byDomain = new LinkedHashMap<>();
            for (PageNode p : pages) {
                byDomain.computeIfAbsent(p.getDomain(), k -> new ArrayList<>()).add(p);
            }
            for (Map.Entry<String, List<PageNode>> entry : byDomain.entrySet()) {
                double total = entry.getValue().stream().mapToDouble(PageNode::getScore).sum();
                sb.append("<div class=\"tree-item\"><strong style=\"color:#fff\">" + escapeHtml(entry.getKey()) + "</strong> (" + String.format("%.1f", total) + ")</div>");
            }
            sb.append("</div>");
            
            sb.append("<div class=\"panel\">");
            sb.append("<h3>🧠 語意分析</h3>");
            List<String> extracted = SemanticAnalyzer.extractRelatedKeywords(pages);
            sb.append("<div style=\"font-size:12px;margin-bottom:8px;color:#888\">提取的關鍵字:</div>");
            sb.append("<ul>");
            for (String kw : extracted) {
                sb.append("<li>" + escapeHtml(kw) + "</li>");
            }
            sb.append("</ul>");
            
            List<String> suggested = SemanticAnalyzer.suggestNewKeywords(extracted);
            if (!suggested.isEmpty()) {
                sb.append("<div style=\"font-size:12px;margin:8px 0;color:#888\">建議關鍵字:</div>");
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
        
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div></body></html>");
        sendHtml(ex, sb.toString());
    }

    private static void handleTree(HttpExchange ex) throws IOException {
        Tree tree = SearchEngine.getLastSearchTree();
        
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>樹狀結構</title>");
        sb.append("<style>body{font-family:monospace;background:#0f0f0f;color:#fff;padding:24px}.container{max-width:900px;margin:0 auto}pre{background:rgba(255,255,255,0.05);padding:16px;border-radius:8px;overflow-x:auto}.btn{background:rgba(255,255,255,0.1);border:1px solid rgba(255,255,255,0.2);padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#fff}</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>📊 網站樹狀結構</h2>");
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
        sb.append("<style>body{font-family:sans-serif;background:#0f0f0f;color:#fff;padding:24px}.container{max-width:900px;margin:0 auto}pre{background:rgba(255,255,255,0.05);padding:16px;border-radius:8px;overflow-x:auto}.btn{background:rgba(255,255,255,0.1);border:1px solid rgba(255,255,255,0.2);padding:8px 16px;border-radius:4px;cursor:pointer;text-decoration:none;color:#fff}</style>");
        sb.append("</head><body><div class=\"container\">");
        sb.append("<h2>🧠 語意分析</h2>");
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