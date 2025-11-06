package app.bl;

import app.da.GoogleConnector;
import java.util.*;

public class SearchEngine {

    public static List<PageNode> search(String query, UserProfile user) {
        List<PageNode> pages = new ArrayList<>();

        try {
            // 1) 用你寫好的 GoogleConnector 直接打 CSE 拿結果
            List<GoogleConnector.Result> results = GoogleConnector.search(query, 10);

            // 2) 先用簡單規則把結果轉成 PageNode（之後可改成真的 HTML 解析）
            for (GoogleConnector.Result r : results) {
                // 先用 title 當作 token 來源，做非常粗的詞頻（之後可換 HTMLHandler 抽詞）
                Map<Keyword, Integer> tf = new HashMap<>();
                List<Keyword> dict = List.of(
                        new Keyword("活動", 6),
                        new Keyword("展覽", 5),
                        new Keyword("音樂", 4),
                        new Keyword("演唱會", 4),
                        new Keyword("市集", 3)
                );
                String title = (r.title == null ? "" : r.title);
                for (Keyword k : dict) {
                    int c = 0;
                    if (!title.isBlank() && title.contains(k.name())) c++;
                    if (c > 0) tf.put(k, c);
                }

                // 先猜城市（之後可換 LocationRecognizer + HTMLHandler）
                String city = guessCity(title);

                pages.add(PageNode.of(
                        r.link,
                        title.isBlank() ? "未命名活動" : title,
                        tf,
                        null,                        // eventDate 之後從 HTML 抓
                        city,                        // 先用猜的
                        extractDomain(r.link),
                        Arrays.asList(query.split("\\s+"))
                ));
            }
        } catch (Exception e) {
            System.err.println("[SearchEngine] GoogleConnector.search 失敗: " + e.getMessage());
        }

        // 3) 排名
        RankCalculator.rank(pages, user);
        return pages;
    }

    private static String extractDomain(String url) {
        try {
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "example.com";
        }
    }

    // 很粗的城市猜測（之後可換 LocationRecognizer）
    private static String guessCity(String text) {
        if (text == null) return "";
        if (text.contains("台北")) return "台北";
        if (text.contains("新北")) return "新北";
        if (text.contains("桃園")) return "桃園";
        if (text.contains("台中")) return "台中";
        if (text.contains("台南")) return "台南";
        if (text.contains("高雄")) return "高雄";
        return "";
    }
}