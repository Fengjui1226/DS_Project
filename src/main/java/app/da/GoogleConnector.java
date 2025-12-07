package app.da;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.time.Duration;

public class GoogleConnector {

    public static class Result {
        public final String title, link;
        public Result(String t, String l){ this.title=t; this.link=l; }
        @Override public String toString(){ return title + " -> " + link; }
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern LINK  = Pattern.compile("\"link\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    // 對外舊介面：保留相容
    public static List<Result> search(String query, int num) throws Exception {
        return search(query, num, 5000); // default timeout 5s
    }

    /**
     * 改良版：
     * - 支援分頁（start）
     * - 一次最多回傳 40 筆（可以自己調）
     * - 單頁仍遵守 Google CSE 的 num<=10 限制
     */
    public static List<Result> search(String query, int num, int timeoutMillis) throws Exception {
        if (!Boolean.parseBoolean(Config.get("google.cse.enabled", "true"))) {
            return List.of();
        }

        String apiKey = Config.get("google.cse.apiKey", null);
        String cx = Config.get("google.cse.cx", null);
        if (apiKey == null || cx == null) {
            throw new IllegalStateException("Missing google.cse.apiKey / google.cse.cx");
        }

        // 想要的總數量，上限自己決定（這邊先設 40）
        int target = Math.max(1, Math.min(num, 40));

        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String baseUrl = "https://www.googleapis.com/customsearch/v1"
                + "?key=" + apiKey
                + "&cx=" + cx
                + "&q=" + q;

        List<Result> all = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();

        int startIndex = 1;          // Google CSE start 從 1 開始
        while (all.size() < target) {
            int remain = target - all.size();
            int pageSize = Math.min(10, remain);  // 單頁最多 10

            String url = baseUrl + "&num=" + pageSize + "&start=" + startIndex;

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .GET()
                    .build();

            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                // 如果某一頁失敗，就先用目前已拿到的結果，不整個炸掉
                System.out.println("[GoogleConnector] HTTP " + resp.statusCode()
                        + " when fetching page start=" + startIndex);
                break;
            }

            List<Result> page = parse(resp.body());
            if (page.isEmpty()) {
                // 沒有更多結果了
                break;
            }

            // 加入去重後的結果
            for (Result r : page) {
                if (!seenLinks.contains(r.link)) {
                    seenLinks.add(r.link);
                    all.add(r);
                    if (all.size() >= target) break;
                }
            }

            // 下一頁的起始位置
            startIndex += page.size();

            // 如果這一頁回來的數量比我們要的少，代表已經沒有下一頁了
            if (page.size() < pageSize) {
                break;
            }
        }

        System.out.println("[GoogleConnector] query=\"" + query + "\" -> "
                + all.size() + " results (target=" + target + ")");
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
