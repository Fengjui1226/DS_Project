package app.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import app.bl.*;

/**
 * SimpleServer v3.0 - 支援爬蟲 + 關鍵字推薦
 */
public class SimpleServer {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;

    // 限流設定
    private static final int RATE_LIMIT_REQUESTS = 10;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 10;
    private static final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    // 快取設定
    private static final int CACHE_TTL_SECONDS = 300;
    private static final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();

    private static UserProfile userProfile = new UserProfile();
    private static List<PageNode> lastResults = new ArrayList<>();
    private static String lastQuery = "";
    private static String lastCity = "台北";

    static class RateLimitInfo {
        int count = 0;
        long windowStart = System.currentTimeMillis();
    }

    static class CacheEntry {
        String data;
        long timestamp;

        CacheEntry(String data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_SECONDS * 1000;
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        server.setExecutor(executor);

        // API 路由
        server.createContext("/api/search", SimpleServer::handleSearch);
        server.createContext("/api/categories", SimpleServer::handleCategories);
        server.createContext("/api/suggestions", SimpleServer::handleSuggestions);
        server.createContext("/api/subpages", SimpleServer::handleSubpages);
        server.createContext("/api/health", SimpleServer::handleHealth);

        server.start();

        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║     🎪 EventFinder API Server                     ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║  Status:  ✅ Running                              ║");
        System.out.println("║  Port:    " + PORT + "                                    ║");
        System.out.println("║  Crawler: ✅ Enabled                              ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║  APIs:                                            ║");
        System.out.println("║  • /api/search       搜尋活動                     ║");
        System.out.println("║  • /api/suggestions  關鍵字推薦 (Google 風格)     ║");
        System.out.println("║  • /api/subpages     子網頁詳情                   ║");
        System.out.println("║  • /api/categories   分類列表                     ║");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  ⠀⠀  ⡠⠒⢄  ⠀⠀   ᨘ⡴⠒⢦⣀⠔⠒⢄");
        System.out.println("  ⠀⠀ ⡏  ⠀ ⠉⠉⠉⣽⠀⢴⣷⠛⢲⠶⠚⣄");
        System.out.println("  ⠀⠀ ⢸ ⠀⠀⠀  ⠀⠀⠓⠚⠛⠤⡞⠛⠀⡞");
        System.out.println("   ⠀⠀⢸ ⠀⠀⠀⠀⠀⠀⠀ ⠀⠀⠀ᱸ⠉⢉⣇⣀⣀");
        System.out.println("    ⠉⠉⣇⡀   ⣶⠀⠀  ⣀⠀⠀  ⣶⠀⠀⣾⠤⠤");
        System.out.println("   ⢎ ⠡⠨ ⣃⡀ ⠀⠀⠀⠉⠀⠀⠀      ⡸⠒⠒");
        System.out.println("     ⢸⢴⠉⠂⣘ᱸ⠖⢶⠒⠒⡶⢲⠒⡞⢣");
        System.out.println("  ⠀ ᱸ⠢⣉⣁⠜⠒⢄  ⠉⠉⠀⡠⠋⠉⠉");
        System.out.println("  ⠀⠀               ⠑⠒⠓⠒ᱸ");
        System.out.println();
        System.out.println("⊹");
        System.out.println("⢠⡏⠉⠑⢄⠀ ⠀  ⡠⠋⠉⢱⡀");
        System.out.println("⡇⠙⠒⠒⠬⡗⢒⢮⠄⠒⠒⠁⢣");
        System.out.println("⠇⠀⠈⠁⢁⡷⠤⢮⠈⠁⠀⠀⡌");
        System.out.println("⠘⢄⣀⡰⢻⠁⠀⠘⡕⢄⣀⡰⠁⠀⊹ ");
        System.out.println("⠀⡎⠘⢀⠇⠀⠀⠀⢱⠈⠂⠡⠀");
        System.out.println("⠀⠑⢄⡜⠢⡀⠀⢀⠔⠇⡴⠃⠀");
        System.out.println("⠀⠀⠀⠑⠠⠚⠀⠓⠔⠋⠀⠀");
        System.out.println("⊹");

    }

    private static boolean checkRateLimit(HttpExchange ex) {
        String clientIP = ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();

        RateLimitInfo info = rateLimitMap.computeIfAbsent(clientIP, k -> new RateLimitInfo());

        synchronized (info) {
            if (now - info.windowStart > RATE_LIMIT_WINDOW_SECONDS * 1000) {
                info.count = 0;
                info.windowStart = now;
            }
            info.count++;
            return info.count <= RATE_LIMIT_REQUESTS;
        }
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        String json = String.format("{\"success\":false,\"error\":\"%s\"}", escapeJson(message));
        sendJson(ex, status, json);
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty())
            return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception e) {
                    params.put(kv[0], kv[1]);
                }
            }
        }
        return params;
    }

    // ============ 搜尋 API ============
    private static void handleSearch(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{}");
            return;
        }

        if (!checkRateLimit(ex)) {
            sendError(ex, 429, "請求太頻繁，請稍後再試");
            return;
        }

        long startTime = System.currentTimeMillis(); // ★ 計時開始

        try {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            String query = params.getOrDefault("query", "").trim();
            String city = params.getOrDefault("city", "台北").trim();
            int page = 1;
            try {
                page = Integer.parseInt(params.getOrDefault("page", "1"));
            } catch (Exception e) {
            }

            if (query.isEmpty() || query.length() < 2) {
                sendError(ex, 400, "請輸入至少 2 個字的關鍵字");
                return;
            }

            userProfile.setUserCity(city);
            lastQuery = query;
            lastCity = city;

            // 搜尋
            List<PageNode> results = SearchEngine.search(query, userProfile);
            lastResults = results;

            // 分頁
            int pageSize = 10;
            int totalCount = results.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, totalCount);

            List<PageNode> pageResults = (start < totalCount)
                    ? results.subList(start, end)
                    : new ArrayList<>();

            long duration = System.currentTimeMillis() - startTime; // ★ 計時結束

            // 建構回應
            StringBuilder json = new StringBuilder();
            json.append("{\"success\":true,");
            json.append("\"query\":\"").append(escapeJson(query)).append("\",");
            json.append("\"city\":\"").append(escapeJson(city)).append("\",");
            json.append("\"page\":").append(page).append(",");
            json.append("\"totalCount\":").append(totalCount).append(",");
            json.append("\"totalPages\":").append(totalPages).append(",");
            json.append("\"responseTime\":").append(duration).append(","); // ★ 新增回應時間
            json.append("\"results\":[");

            int rank = start + 1;
            for (int i = 0; i < pageResults.size(); i++) {
                PageNode p = pageResults.get(i);
                if (i > 0)
                    json.append(",");
                json.append("{");
                json.append("\"rank\":").append(rank++).append(",");
                json.append("\"title\":\"").append(escapeJson(p.getTitle())).append("\",");
                json.append("\"url\":\"").append(escapeJson(p.getUrl())).append("\",");
                json.append("\"domain\":\"").append(escapeJson(p.getDomain())).append("\",");
                json.append("\"score\":").append(String.format("%.1f", p.getTotalScore())).append(",");
                json.append("\"subPageCount\":").append(p.getSubPageCount()).append(",");
                json.append("\"city\":\"").append(escapeJson(p.getCity() != null ? p.getCity() : "")).append("\",");
                json.append("\"eventDate\":\"").append(p.getEventDate() != null ? p.getEventDate().toString() : "")
                        .append("\",");
                json.append("\"crawled\":").append(p.isCrawled());
                json.append("}");
            }

            json.append("]}");
            sendJson(ex, 200, json.toString());

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            sendError(ex, 500, "搜尋發生錯誤");
        }
    }

    // ============ ★ 關鍵字推薦 API（Google 風格）============
    private static void handleSuggestions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{}");
            return;
        }

        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        String query = params.getOrDefault("query", lastQuery).trim();
        String city = params.getOrDefault("city", lastCity).trim();

        // 取得推薦
        List<String> suggestions = KeywordSuggester.suggest(query, city);

        // 建構 JSON
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,");
        json.append("\"query\":\"").append(escapeJson(query)).append("\",");
        json.append("\"suggestions\":[");

        for (int i = 0; i < suggestions.size(); i++) {
            if (i > 0)
                json.append(",");
            String s = suggestions.get(i);
            json.append("{");
            json.append("\"text\":\"").append(escapeJson(s)).append("\",");
            json.append("\"searchUrl\":\"/api/search?query=").append(escapeJson(s)).append("&city=")
                    .append(escapeJson(city)).append("\"");
            json.append("}");
        }

        json.append("]}");
        sendJson(ex, 200, json.toString());
    }

    // ============ 子網頁 API ============
    private static void handleSubpages(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{}");
            return;
        }

        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        String domain = params.getOrDefault("domain", "").trim();

        if (domain.isEmpty()) {
            sendError(ex, 400, "請提供 domain");
            return;
        }

        PageNode targetPage = null;
        for (PageNode p : lastResults) {
            if (domain.equals(p.getDomain())) {
                targetPage = p;
                break;
            }
        }

        if (targetPage == null) {
            sendJson(ex, 200,
                    "{\"success\":true,\"domain\":\"" + escapeJson(domain) + "\",\"count\":0,\"subpages\":[]}");
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,");
        json.append("\"domain\":\"").append(escapeJson(domain)).append("\",");
        json.append("\"mainPage\":{");
        json.append("\"title\":\"").append(escapeJson(targetPage.getTitle())).append("\",");
        json.append("\"url\":\"").append(escapeJson(targetPage.getUrl())).append("\",");
        json.append("\"score\":").append(targetPage.getScore());
        json.append("},");
        json.append("\"count\":").append(targetPage.getSubPageCount()).append(",");
        json.append("\"subpages\":[");

        List<SubPageNode> subPages = targetPage.getSubPages();
        for (int i = 0; i < subPages.size(); i++) {
            SubPageNode sub = subPages.get(i);
            if (i > 0)
                json.append(",");
            json.append("{");
            json.append("\"title\":\"").append(escapeJson(sub.getTitle())).append("\",");
            json.append("\"url\":\"").append(escapeJson(sub.getUrl())).append("\",");
            json.append("\"score\":").append(sub.getScore()).append(",");
            json.append("\"path\":\"").append(escapeJson(extractPath(sub.getUrl()))).append("\"");
            json.append("}");
        }

        json.append("]}");
        sendJson(ex, 200, json.toString());
    }

    private static String extractPath(String url) {
        if (url == null)
            return "/";
        int idx = url.indexOf("://");
        if (idx >= 0)
            url = url.substring(idx + 3);
        idx = url.indexOf('/');
        return idx >= 0 ? url.substring(idx) : "/";
    }

    // ============ 分類 API ============
    private static void handleCategories(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{}");
            return;
        }

        String json = "{\"success\":true,\"categories\":["
                + "{\"id\":\"music\",\"name\":\"音樂\",\"icon\":\"🎵\",\"query\":\"演唱會 音樂會\",\"color\":\"255,107,107\"},"
                + "{\"id\":\"art\",\"name\":\"展覽\",\"icon\":\"🎨\",\"query\":\"展覽 藝術\",\"color\":\"78,205,196\"},"
                + "{\"id\":\"market\",\"name\":\"市集\",\"icon\":\"🛍️\",\"query\":\"市集 文創\",\"color\":\"255,230,109\"},"
                + "{\"id\":\"outdoor\",\"name\":\"戶外\",\"icon\":\"⛰️\",\"query\":\"戶外 野餐\",\"color\":\"129,199,132\"},"
                + "{\"id\":\"family\",\"name\":\"親子\",\"icon\":\"👨‍👩‍👧\",\"query\":\"親子 兒童\",\"color\":\"186,104,200\"},"
                + "{\"id\":\"sports\",\"name\":\"運動\",\"icon\":\"🏃\",\"query\":\"路跑 馬拉松\",\"color\":\"79,195,247\"}"
                + "]}";
        sendJson(ex, 200, json);
    }

    // ============ 健康檢查 ============
    private static void handleHealth(HttpExchange ex) throws IOException {
        String json = "{\"success\":true,\"status\":\"healthy\",\"version\":\"3.0\",\"crawler\":\"enabled\"}";
        sendJson(ex, 200, json);
    }
}