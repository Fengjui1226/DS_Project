package app.bl;

import app.da.*;
import java.util.*;

public class SearchEngine {

    public static List<PageNode> search(String query, UserProfile user) {
        // 1) 從 Google 模擬拿 URL 清單
        List<String> urls = GoogleConnector.getSearchResults(query);

        // 2) 解析每個 HTML，建立 PageNode（先用假資料）
        List<PageNode> pages = new ArrayList<>();
        for (String url : urls) {
            Map<String, String> html = HTMLHandler.parseHTML("<html>dummy</html>");
            String city = LocationRecognizer.normalize(html.getOrDefault("city", ""));

            // ★ 重點：這裡的值是 Integer（詞頻），不是 Keyword
            Map<Keyword, Integer> tf = new HashMap<>();
            tf.put(new Keyword("活動", 6), 2); // 「活動」出現 2 次
            tf.put(new Keyword("音樂", 4), 1); // 「音樂」出現 1 次

            pages.add(PageNode.of(
                url,
                html.getOrDefault("title", "未命名活動"),
                tf,
                null,                       // 這裡暫時沒有解析到日期
                city,
                extractDomain(url),
                Arrays.asList(query.split("\\s+"))
            ));
        }

        // 3) 排名
        RankCalculator.rank(pages, user);
        return pages;
    }

    private static String extractDomain(String url){
        try{
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        }catch(Exception e){
            return "example.com";
        }
    }
}