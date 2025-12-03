package app.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import app.bl.PageNode;
import app.bl.SearchEngine;
import app.bl.SemanticAnalyzer;
import app.bl.Tree;
import app.bl.UserProfile;

/**
 * Enhanced REST API Server for EventFinder
 * 提供 JSON API 給 React 前端使用
 */
public class SimpleServer {
    
    private static final String FRONTEND_DIR = "frontend/dist";
    
    // 搜尋建議關鍵字庫
    private static final List<String> SUGGESTION_KEYWORDS = Arrays.asList(
        "音樂 演唱會", "音樂節", "音樂會 古典", "音樂 爵士",
        "展覽 藝術", "展覽 當代", "展覽 攝影", "展覽 免費",
        "市集 文創", "市集 假日", "市集 農夫", "市集 手作",
        "戶外 露營", "戶外 健行", "戶外 野餐", "戶外 登山",
        "親子 兒童", "親子 DIY", "親子 免費", "親子 室內",
        "運動 路跑", "運動 籃球", "運動 瑜伽", "運動 健身",
        "美食 餐廳", "美食節", "美食 夜市", "美食 甜點",
        "科技 展覽", "科技 講座", "科技 AI", "科技 創業",
        "電影 首映", "電影 戶外", "電影 影展",
        "講座 免費", "講座 職涯", "講座 理財",
        "派對 音樂", "派對 聖誕", "派對 跨年",
        "工作坊 手作", "工作坊 烘焙", "工作坊 繪畫",
        "台北 活動", "台中 活動", "高雄 活動", "台南 活動",
        "免費 活動", "週末 活動", "今日 活動"
    );
    
    // 儲存最近搜尋（簡易版，實際應用應該用資料庫）
    private static final List<String> recentSearches = Collections.synchronizedList(new ArrayList<>());
    
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // API 路由
        server.createContext("/api/search", SimpleServer::handleSearchAPI);
        server.createContext("/api/tree", SimpleServer::handleTreeAPI);
        server.createContext("/api/semantic", SimpleServer::handleSemanticAPI);
        server.createContext("/api/categories", SimpleServer::handleCategoriesAPI);
        server.createContext("/api/suggestions", SimpleServer::handleSuggestionsAPI);
        server.createContext("/api/subpages", SimpleServer::handleSubpagesAPI);
        server.createContext("/api/history", SimpleServer::handleHistoryAPI);
        
        // 靜態檔案服務
        server.createContext("/", SimpleServer::handleStatic);
        
        server.setExecutor(null);
        server.start();
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║   🎪 EventFinder API Server Started!       ║");
        System.out.println("║   Backend API: http://localhost:" + port + "        ║");
        System.out.println("║   React Dev:   http://localhost:3000       ║");
        System.out.println("╚════════════════════════════════════════════╝");
        
        final Object lock = new Object();
        synchronized (lock) {
            try { lock.wait(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== 搜尋 API ====================
    private static void handleSearchAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String query = getQueryParam(ex.getRequestURI(), "query");
        String city = getQueryParam(ex.getRequestURI(), "city");
        String pageStr = getQueryParam(ex.getRequestURI(), "page");
        int page = 1;
        try { page = Integer.parseInt(pageStr); } catch (Exception e) {}
        int pageSize = 10;
        
        if (city == null || city.isEmpty()) city = "台北";
        if (query == null || query.trim().isEmpty()) query = "音樂 活動";

        // 記錄搜尋歷史
        addToHistory(query);

        UserProfile user = new UserProfile();
        user.setUserCity(city);

        try {
            List<PageNode> allResults = SearchEngine.search(query, user);
            
            // 分頁處理
            int totalResults = allResults.size();
            int totalPages = (int) Math.ceil((double) totalResults / pageSize);
            int startIdx = (page - 1) * pageSize;
            int endIdx = Math.min(startIdx + pageSize, totalResults);
            
            List<PageNode> pagedResults = startIdx < totalResults 
                ? allResults.subList(startIdx, endIdx) 
                : Collections.emptyList();
            
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\":true,");
            json.append("\"query\":\"").append(escapeJson(query)).append("\",");
            json.append("\"city\":\"").append(escapeJson(city)).append("\",");
            json.append("\"totalCount\":").append(totalResults).append(",");
            json.append("\"page\":").append(page).append(",");
            json.append("\"totalPages\":").append(totalPages).append(",");
            json.append("\"pageSize\":").append(pageSize).append(",");
            json.append("\"results\":[");
            
            for (int i = 0; i < pagedResults.size(); i++) {
                PageNode p = pagedResults.get(i);
                int actualRank = startIdx + i + 1;
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"rank\":").append(actualRank).append(",");
                json.append("\"title\":\"").append(escapeJson(p.getTitle())).append("\",");
                json.append("\"url\":\"").append(escapeJson(p.getUrl())).append("\",");
                json.append("\"score\":").append(String.format("%.2f", p.getScore())).append(",");
                json.append("\"city\":\"").append(escapeJson(p.getCity() != null ? p.getCity() : "")).append("\",");
                json.append("\"domain\":\"").append(escapeJson(p.getDomain())).append("\",");
                json.append("\"eventDate\":").append(p.getEventDate() != null ? "\"" + p.getEventDate() + "\"" : "null").append(",");
                json.append("\"snippet\":\"").append(escapeJson(generateSnippet(p))).append("\"");
                json.append("}");
            }
            
            json.append("]}");
            sendJson(ex, json.toString());
            
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String errorMsg = sw.toString();
            if (errorMsg.length() > 500) errorMsg = errorMsg.substring(0, 500);
            String error = "{\"success\":false,\"error\":\"" + escapeJson(errorMsg) + "\"}";
            sendJson(ex, error, 500);
        }
    }

    // ==================== 搜尋建議 API ====================
    private static void handleSuggestionsAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String query = getQueryParam(ex.getRequestURI(), "q");
        if (query == null) query = "";
        
        final String searchTerm = query.toLowerCase();
        
        // 過濾匹配的建議
        List<String> suggestions = SUGGESTION_KEYWORDS.stream()
            .filter(kw -> kw.toLowerCase().contains(searchTerm))
            .limit(8)
            .collect(Collectors.toList());
        
        // 加入最近搜尋
        List<String> recentMatches = recentSearches.stream()
            .filter(s -> s.toLowerCase().contains(searchTerm))
            .distinct()
            .limit(3)
            .collect(Collectors.toList());
        
        StringBuilder json = new StringBuilder();
        json.append("{\"suggestions\":[");
        
        int count = 0;
        // 先顯示最近搜尋
        for (String recent : recentMatches) {
            if (count > 0) json.append(",");
            json.append("{\"text\":\"").append(escapeJson(recent)).append("\",\"type\":\"history\"}");
            count++;
        }
        
        // 再顯示建議關鍵字
        for (String suggestion : suggestions) {
            if (count >= 10) break;
            if (recentMatches.contains(suggestion)) continue;
            if (count > 0) json.append(",");
            json.append("{\"text\":\"").append(escapeJson(suggestion)).append("\",\"type\":\"suggestion\"}");
            count++;
        }
        
        json.append("]}");
        sendJson(ex, json.toString());
    }

    // ==================== 子網頁查詢 API ====================
    private static void handleSubpagesAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String domain = getQueryParam(ex.getRequestURI(), "domain");
        String query = getQueryParam(ex.getRequestURI(), "query");
        
        if (domain == null || domain.isEmpty()) {
            sendJson(ex, "{\"success\":false,\"error\":\"需要提供 domain 參數\"}");
            return;
        }

        Tree tree = SearchEngine.getLastSearchTree();
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        if (tree == null) {
            json.append("\"success\":false,\"error\":\"請先進行搜尋\"");
        } else {
            List<PageNode> allPages = tree.getAllPagesSorted();
            List<PageNode> domainPages = allPages.stream()
                .filter(p -> p.getDomain().equalsIgnoreCase(domain))
                .collect(Collectors.toList());
            
            json.append("\"success\":true,");
            json.append("\"domain\":\"").append(escapeJson(domain)).append("\",");
            json.append("\"count\":").append(domainPages.size()).append(",");
            json.append("\"subpages\":[");
            
            for (int i = 0; i < domainPages.size(); i++) {
                PageNode p = domainPages.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"title\":\"").append(escapeJson(p.getTitle())).append("\",");
                json.append("\"url\":\"").append(escapeJson(p.getUrl())).append("\",");
                json.append("\"score\":").append(String.format("%.2f", p.getScore())).append(",");
                json.append("\"path\":\"").append(escapeJson(extractPath(p.getUrl()))).append("\"");
                json.append("}");
            }
            
            json.append("]");
        }
        
        json.append("}");
        sendJson(ex, json.toString());
    }

    // ==================== 搜尋歷史 API ====================
    private static void handleHistoryAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"history\":[");
        
        List<String> recent = new ArrayList<>(recentSearches);
        Collections.reverse(recent);
        
        for (int i = 0; i < Math.min(10, recent.size()); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(recent.get(i))).append("\"");
        }
        
        json.append("]}");
        sendJson(ex, json.toString());
    }

    // ==================== 樹狀結構 API ====================
    private static void handleTreeAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        Tree tree = SearchEngine.getLastSearchTree();
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        if (tree == null) {
            json.append("\"success\":false,\"error\":\"請先進行搜尋\"");
        } else {
            json.append("\"success\":true,\"domains\":[");
            
            List<PageNode> pages = tree.getAllPagesSorted();
            Map<String, List<PageNode>> byDomain = new LinkedHashMap<>();
            for (PageNode p : pages) {
                byDomain.computeIfAbsent(p.getDomain(), k -> new ArrayList<>()).add(p);
            }
            
            int idx = 0;
            for (Map.Entry<String, List<PageNode>> entry : byDomain.entrySet()) {
                if (idx++ > 0) json.append(",");
                double total = entry.getValue().stream().mapToDouble(PageNode::getScore).sum();
                json.append("{\"domain\":\"").append(escapeJson(entry.getKey())).append("\",");
                json.append("\"totalScore\":").append(String.format("%.2f", total)).append(",");
                json.append("\"pageCount\":").append(entry.getValue().size()).append("}");
            }
            json.append("]");
        }
        
        json.append("}");
        sendJson(ex, json.toString());
    }

    // ==================== 語意分析 API ====================
    private static void handleSemanticAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        Tree tree = SearchEngine.getLastSearchTree();
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        if (tree == null) {
            json.append("\"success\":false,\"error\":\"請先進行搜尋\"");
        } else {
            List<PageNode> pages = tree.getAllPagesSorted();
            List<String> extracted = SemanticAnalyzer.extractRelatedKeywords(pages);
            List<String> suggested = SemanticAnalyzer.suggestNewKeywords(extracted);
            
            json.append("\"success\":true,\"extractedKeywords\":[");
            for (int i = 0; i < extracted.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escapeJson(extracted.get(i))).append("\"");
            }
            json.append("],\"suggestedKeywords\":[");
            int count = 0;
            for (String kw : suggested) {
                if (count > 0) json.append(",");
                json.append("\"").append(escapeJson(kw)).append("\"");
                if (++count >= 10) break;
            }
            json.append("]");
        }
        
        json.append("}");
        sendJson(ex, json.toString());
    }

    // ==================== 分類 API ====================
    private static void handleCategoriesAPI(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String json = "{\"categories\":[" +
            "{\"id\":\"music\",\"name\":\"音樂\",\"icon\":\"🎵\",\"query\":\"音樂 演唱會\",\"color\":\"#ff6b6b\"}," +
            "{\"id\":\"art\",\"name\":\"展覽\",\"icon\":\"🎨\",\"query\":\"展覽 藝術\",\"color\":\"#4ecdc4\"}," +
            "{\"id\":\"market\",\"name\":\"市集\",\"icon\":\"🛍️\",\"query\":\"市集 文創\",\"color\":\"#ffe66d\"}," +
            "{\"id\":\"outdoor\",\"name\":\"戶外\",\"icon\":\"⛺\",\"query\":\"戶外 露營\",\"color\":\"#95e1d3\"}," +
            "{\"id\":\"family\",\"name\":\"親子\",\"icon\":\"👨‍👩‍👧\",\"query\":\"親子 兒童\",\"color\":\"#f38181\"}," +
            "{\"id\":\"sports\",\"name\":\"運動\",\"icon\":\"⚽\",\"query\":\"運動 賽事\",\"color\":\"#aa96da\"}," +
            "{\"id\":\"food\",\"name\":\"美食\",\"icon\":\"🍜\",\"query\":\"美食 節\",\"color\":\"#fcbad3\"}," +
            "{\"id\":\"tech\",\"name\":\"科技\",\"icon\":\"💻\",\"query\":\"科技 展覽\",\"color\":\"#a8d8ea\"}" +
            "]}";
        sendJson(ex, json);
    }

    // ==================== 靜態檔案 ====================
    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        
        Path filePath = Paths.get(FRONTEND_DIR + path);
        if (!Files.exists(filePath)) {
            filePath = Paths.get(FRONTEND_DIR + "/index.html");
        }
        
        if (Files.exists(filePath)) {
            byte[] bytes = Files.readAllBytes(filePath);
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", getContentType(path));
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } else {
            String html = "<!DOCTYPE html><html><head><meta charset='utf-8'></head>" +
                "<body style='font-family:system-ui;padding:40px;background:#0a0a0f;color:#fff;text-align:center'>" +
                "<h1>🎪 EventFinder API</h1>" +
                "<p style='color:#888'>後端已啟動！請啟動 React 前端：</p>" +
                "<pre style='background:#1a1a2e;padding:20px;border-radius:8px;text-align:left;display:inline-block'>" +
                "cd frontend\nnpm install\nnpm run dev</pre>" +
                "<p style='color:#888;margin-top:20px'>然後訪問 <a href='http://localhost:3000' style='color:#ff6b6b'>http://localhost:3000</a></p>" +
                "</body></html>";
            sendHtml(ex, html);
        }
    }

    // ==================== 工具方法 ====================
    
    private static void addToHistory(String query) {
        recentSearches.removeIf(s -> s.equalsIgnoreCase(query));
        recentSearches.add(query);
        while (recentSearches.size() > 50) {
            recentSearches.remove(0);
        }
    }
    
    private static String generateSnippet(PageNode p) {
        String title = p.getTitle() != null ? p.getTitle() : "";
        String city = p.getCity() != null ? p.getCity() : "";
        String domain = p.getDomain() != null ? p.getDomain() : "";
        return String.format("在 %s 舉辦的活動，來源：%s", city.isEmpty() ? "台灣" : city, domain);
    }
    
    private static String extractPath(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            return path != null && !path.isEmpty() ? path : "/";
        } catch (Exception e) {
            return "/";
        }
    }

    private static void setCorsHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
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
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } 
        catch (Exception e) { return s; }
    }

    private static void sendJson(HttpExchange ex, String json) throws IOException {
        sendJson(ex, json, 200);
    }

    private static void sendJson(HttpExchange ex, String json, int code) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "text/plain; charset=utf-8";
    }
}