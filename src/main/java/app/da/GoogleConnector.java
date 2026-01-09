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

        // 驗證格式（輸出前3和後3字符用於驗證）
        System.out.println("[GoogleConnector] API Key 前3字: " + apiKey.substring(0, Math.min(3, apiKey.length())));
        System.out.println("[GoogleConnector] API Key 長度: " + apiKey.length());
        System.out.println("[GoogleConnector] CX 值: " + cx);
        System.out.println("[GoogleConnector] CX 長度: " + cx.length());

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

        List<Result> all = new ArrayList<>();

        while (remaining > 0 && startIndex <= 100) {
            int pageSize = Math.min(remaining, 10);  // 單頁最多 10

            // 正確編碼查詢參數（URLEncoder 會把空格編碼為 +，需要再轉換為 %20）
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
            String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String encodedCx = URLEncoder.encode(cx, StandardCharsets.UTF_8);

            String url = "https://www.googleapis.com/customsearch/v1"
                    + "?key=" + encodedApiKey
                    + "&cx=" + encodedCx
                    + "&num=" + pageSize
                    + "&start=" + startIndex
                    + "&q=" + encodedQuery;

            // 調試：輸出請求信息（隱藏完整 API Key）
            String maskedUrl = url.replaceAll("key=[^&]+", "key=***");
            System.out.println("[GoogleConnector] 準備請求 Google API...");
            System.out.println("[GoogleConnector] 請求 URL: " + maskedUrl);
            System.out.println("[GoogleConnector] 原始 Timeout: " + timeoutMillis + "ms");

            // 使用更長的 timeout（至少 10 秒）
            int actualTimeout = Math.max(timeoutMillis, 10000);
            System.out.println("[GoogleConnector] 實際 Timeout: " + actualTimeout + "ms");

            System.out.println("[GoogleConnector] 正在建立 HTTP 請求...");
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(actualTimeout))
                    .header("User-Agent", "EventFinder/1.0")
                    .GET()
                    .build();

            System.out.println("[GoogleConnector] 正在發送請求到 Google API...");
            long startTime = System.currentTimeMillis();

            HttpResponse<String> resp;
            try {
                resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("[GoogleConnector] 請求完成，耗時: " + elapsed + "ms");
            } catch (java.net.http.HttpConnectTimeoutException e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = "[GoogleConnector] ⚠️ HTTP 連線超時";
                System.out.println(errorMsg);
                System.out.println("[GoogleConnector] 超時詳情: timeout=" + actualTimeout + "ms, 實際耗時=" + elapsed + "ms");
                System.out.println("[GoogleConnector] 目標主機: www.googleapis.com");
                System.out.println("[GoogleConnector] 異常類型: " + e.getClass().getName());
                System.out.println("[GoogleConnector] 異常訊息: " + e.getMessage());

                if (all.isEmpty()) {
                    String diagnosis = "\n可能原因:\n" +
                                     "1. 服務器無法訪問 Google API (防火牆/網路限制)\n" +
                                     "2. API Key 無效: " + apiKey.substring(0, 3) + "..." + "\n" +
                                     "3. CX 無效: " + cx;
                    throw new RuntimeException(errorMsg + diagnosis, e);
                } else {
                    System.out.println("[GoogleConnector] 先用目前已取得的 " + all.size() + " 筆");
                    break;
                }
            } catch (java.net.UnknownHostException e) {
                String errorMsg = "[GoogleConnector] ⚠️ 無法解析主機名稱 (DNS 錯誤)";
                System.out.println(errorMsg);
                System.out.println("[GoogleConnector] 主機: www.googleapis.com");
                throw new RuntimeException(errorMsg + " - 服務器可能無法訪問外部網路", e);
            } catch (java.io.IOException e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = "[GoogleConnector] ⚠️ IO 錯誤";
                System.out.println(errorMsg);
                System.out.println("[GoogleConnector] 耗時: " + elapsed + "ms");
                System.out.println("[GoogleConnector] 異常: " + e.getClass().getName() + ": " + e.getMessage());

                if (all.isEmpty()) {
                    throw new RuntimeException(errorMsg + ": " + e.getMessage(), e);
                } else {
                    System.out.println("[GoogleConnector] 先用目前已取得的 " + all.size() + " 筆");
                    break;
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                String errorMsg = "[GoogleConnector] ⚠️ 請求失敗";
                System.out.println(errorMsg);
                System.out.println("[GoogleConnector] 耗時: " + elapsed + "ms");
                System.out.println("[GoogleConnector] 異常類型: " + e.getClass().getName());
                System.out.println("[GoogleConnector] 異常訊息: " + e.getMessage());
                e.printStackTrace();

                if (all.isEmpty()) {
                    throw new RuntimeException(errorMsg + ": " + e.getMessage(), e);
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