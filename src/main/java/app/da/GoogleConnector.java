package app.da;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class GoogleConnector {

    public static class Result {
        public final String title, link;
        public Result(String t, String l){ this.title=t; this.link=l; }
        @Override public String toString(){ return title + " -> " + link; }
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern LINK  = Pattern.compile("\"link\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    public static List<Result> search(String query, int num) throws Exception {
        if (!Boolean.parseBoolean(Config.get("google.cse.enabled","true")))
            return List.of();

        String apiKey = Config.get("google.cse.apiKey", null);
        String cx     = Config.get("google.cse.cx", null);
        if (apiKey == null || cx == null) {
            throw new IllegalStateException("Missing google.cse.apiKey / google.cse.cx");
        }

        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey +
                     "&cx=" + cx + "&num=" + Math.min(num, 10) + "&q=" + q;

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("CSE HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return parse(resp.body());
    }

    // 極簡 JSON 解析：依序配對 title/link
    private static List<Result> parse(String json){
        List<Result> out = new ArrayList<>();
        Matcher mt = TITLE.matcher(json);
        Matcher ml = LINK.matcher(json);
        while (mt.find() && ml.find()) {
            String t = unescape(mt.group(1));
            String l = unescape(ml.group(1));
            if (!t.isBlank() && !l.isBlank()) out.add(new Result(t, l));
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