package app.da;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/**
 * Google Custom Search 封裝 v2.0
 * - 每次 API 只能拿 num <= 10
 * - 這版會自動分頁，多次呼叫 API，最多拿到 num 筆（上限 100）
 * - ★ 新增：解析 snippet（搜尋摘要）
 */
public class GoogleConnector {

    public static class Result {
        public final String title, link, snippet;
        public Result(String t, String l, String s){ 
            this.title = t; 
            this.link = l; 
            this.snippet = s != null ? s : "";
        }
        @Override public String toString(){ return title + " -> " + link; }
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern LINK  = Pattern.compile("\"link\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern SNIPPET = Pattern.compile("\"snippet\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    // 方便呼叫：預設 timeout 5s（每一頁各自 5s）
    public static List<Result> search(String query, int num) throws Exception {
        return search(query, num, 5000);
    }

    /**
     * 多頁版搜尋：
     * - num：希望總共拿幾筆（例如 20、30）
     * - timeoutMillis：單次 HTTP request 的 timeout
     */
    public static List<Result> search(String query, int num, int timeoutMillis) throws Exception {
        if (!Boolean.parseBoolean(Config.get("google.cse.enabled", "true"))) {
            return List.of();
        }

        String apiKey = Config.get("google.cse.apiKey", null);
        String cx     = Config.get("google.cse.cx", null);

        // 調試：輸出環境變數狀態
        System.out.println("[GoogleConnector] API Key 狀態: " + (apiKey != null ? "已設定" : "未設定"));
        System.out.println("[GoogleConnector] CX 狀態: " + (cx != null ? "已設定" : "未設定"));

        if (apiKey == null || cx == null) {
            throw new IllegalStateException("Missing google.cse.apiKey / google.cse.cx");
        }

        // 驗證格式
        if (apiKey.length() < 20) {
            System.out.println("[GoogleConnector] 警告: API Key 長度異常 (< 20)");
        }
        if (cx.length() < 5) {
            System.out.println("[GoogleConnector] 警告: CX 長度異常 (< 5)");
        }

        // CSE 規則：num 每頁最多 10，start 1-based，最大到 100
        int targetTotal = Math.min(num, 100);
        int remaining   = targetTotal;
        int startIndex  = 1;

        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<Result> all = new ArrayList<>();

        while (remaining > 0 && startIndex <= 100) {
            int pageSize = Math.min(remaining, 10);  // 單頁最多 10

            String url = "https://www.googleapis.com/customsearch/v1"
                    + "?key=" + apiKey
                    + "&cx=" + cx
                    + "&num=" + pageSize
                    + "&start=" + startIndex
                    + "&q=" + q;

            // 調試：輸出請求信息（隱藏完整 API Key）
            String maskedUrl = url.replaceAll("key=[^&]+", "key=***");
            System.out.println("[GoogleConnector] 請求 URL: " + maskedUrl);
            System.out.println("[GoogleConnector] Timeout: " + timeoutMillis + "ms");

            // 使用更長的 timeout（至少 10 秒）
            int actualTimeout = Math.max(timeoutMillis, 10000);

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(actualTimeout))
                    .GET()
                    .build();

            HttpResponse<String> resp;
            try {
                resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpConnectTimeoutException e) {
                String errorMsg = "[GoogleConnector] 連線超時 (timeout=" + actualTimeout + "ms)";
                System.out.println(errorMsg);
                if (all.isEmpty()) {
                    throw new RuntimeException(errorMsg + " - 請檢查: 1) API Key 是否正確 2) CX 是否正確 3) 網路連線", e);
                } else {
                    System.out.println("[GoogleConnector] 先用目前已取得的 " + all.size() + " 筆");
                    break;
                }
            } catch (Exception e) {
                String errorMsg = "[GoogleConnector] 請求失敗: " + e.getMessage();
                System.out.println(errorMsg);
                if (all.isEmpty()) {
                    throw new RuntimeException(errorMsg, e);
                } else {
                    break;
                }
            }

            if (resp.statusCode() != 200) {
                String errorBody = resp.body();
                System.out.println("[GoogleConnector] HTTP " + resp.statusCode() + " 錯誤回應: " +
                        (errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody));

                // 若第一頁就錯，直接丟出錯誤；如果是後面的頁數錯，就先用目前拿到的結果
                if (all.isEmpty()) {
                    throw new RuntimeException("CSE HTTP " + resp.statusCode() + ": " + errorBody);
                } else {
                    System.out.println("[GoogleConnector] 後續頁面 HTTP " + resp.statusCode() +
                            "，先用目前已取得的 " + all.size() + " 筆");
                    break;
                }
            }

            List<Result> pageResults = parse(resp.body());
            if (pageResults.isEmpty()) {
                // 這一頁沒東西，代表已經沒有更多結果
                break;
            }

            all.addAll(pageResults);

            // 更新剩餘 & 下一頁 startIndex
            remaining = targetTotal - all.size();
            startIndex += pageResults.size();

            // 安全保護：避免超出 100
            if (startIndex > 100) break;
        }

        // 如果超過目標數量，就切掉多的
        if (all.size() > targetTotal) {
            return new ArrayList<>(all.subList(0, targetTotal));
        }
        return all;
    }

    /**
     * 解析 Google CSE JSON 回應
     * ★ 改進：同時解析 title, link, snippet
     */
    private static List<Result> parse(String json){
        List<Result> out = new ArrayList<>();
        
        // Google CSE 回傳的 JSON 格式是 "items": [{item1}, {item2}, ...]
        // 我們需要找到每個 item 區塊，然後從中提取 title, link, snippet
        
        // 找到 items 陣列的開始位置
        int itemsStart = json.indexOf("\"items\"");
        if (itemsStart == -1) return out;
        
        // 從 items 開始找每個結果
        // 每個結果都有 "title", "link", "snippet" 欄位
        
        // 使用更精確的方式：找到每個 "kind": "customsearch#result" 區塊
        String[] items = json.split("\"kind\"\\s*:\\s*\"customsearch#result\"");
        
        for (int i = 1; i < items.length; i++) {  // 從 1 開始，因為第 0 個是 items 之前的內容
            String item = items[i];
            
            // 限制範圍到下一個 item 或結束
            int endIdx = item.indexOf("\"kind\"");
            if (endIdx == -1) endIdx = item.length();
            String itemContent = item.substring(0, endIdx);
            
            String title = extractField(itemContent, TITLE);
            String link = extractField(itemContent, LINK);
            String snippet = extractField(itemContent, SNIPPET);
            
            if (!title.isEmpty() && !link.isEmpty()) {
                out.add(new Result(title, link, snippet));
            }
        }
        
        // 備用方案：如果上面沒解析到，用簡單的順序配對
        if (out.isEmpty()) {
            Matcher mt = TITLE.matcher(json);
            Matcher ml = LINK.matcher(json);
            Matcher ms = SNIPPET.matcher(json);
            
            while (mt.find() && ml.find()) {
                String t = unescape(mt.group(1));
                String l = unescape(ml.group(1));
                String s = ms.find() ? unescape(ms.group(1)) : "";
                if (!t.isBlank() && !l.isBlank()) {
                    out.add(new Result(t, l, s));
                }
            }
        }
        
        return out;
    }
    
    private static String extractField(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return unescape(m.group(1));
        }
        return "";
    }

    private static String unescape(String s){
        return s.replace("\\n"," ")
                .replace("\\r"," ")
                .replace("\\t"," ")
                .replace("\\/","/")
                .replace("\\\"", "\"")
                .replace("\\\\","\\");
    }
}