package app.da;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/**
 * Google Custom Search 封裝
 * - 每次 API 只能拿 num <= 10
 * - 這版會自動分頁，多次呼叫 API，最多拿到 num 筆（上限 100）
 */
public class GoogleConnector {

    public static class Result {
        public final String title, link;
        public Result(String t, String l){ this.title = t; this.link = l; }
        @Override public String toString(){ return title + " -> " + link; }
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern LINK  = Pattern.compile("\"link\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

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
        if (apiKey == null || cx == null) {
            throw new IllegalStateException("Missing google.cse.apiKey / google.cse.cx");
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

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .GET()
                    .build();

            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                // 若第一頁就錯，直接丟出錯誤；如果是後面的頁數錯，就先用目前拿到的結果
                if (all.isEmpty()) {
                    throw new RuntimeException("CSE HTTP " + resp.statusCode() + ": " + resp.body());
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

    // 極簡 JSON 解析：依序配對 title/link
    private static List<Result> parse(String json){
        List<Result> out = new ArrayList<>();
        Matcher mt = TITLE.matcher(json);
        Matcher ml = LINK.matcher(json);
        while (mt.find() && ml.find()) {
            String t = unescape(mt.group(1));
            String l = unescape(ml.group(1));
            if (!t.isBlank() && !l.isBlank()) {
                out.add(new Result(t, l));
            }
        }
        return out;
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
