package app.bl;

import app.da.GoogleConnector;
import java.util.*;

public class SearchEngine {

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        // 1) 打 Google CSE 拿結果
        List<GoogleConnector.Result> results = GoogleConnector.search(query, 10);

        // 2) 去重 & 轉 PageNode
        Set<String> seenLinks = new HashSet<>();
        List<PageNode> pages = new ArrayList<>();

        List<String> qTokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            if (!t.isBlank()) qTokens.add(t.trim());
        }

        for (GoogleConnector.Result r : results) {
            if (r == null || r.title == null || r.title.isBlank() || r.link == null || r.link.isBlank()) continue;
            String linkKey = r.link.trim();
            if (!seenLinks.add(linkKey)) continue; // 去重

            // 簡易 tf：用 query 的詞當成 tf 來源（後面你可改為 HTML 解析）
            Map<Keyword, Integer> tf = new HashMap<>();
            for (String t : qTokens) {
                Keyword k = new Keyword(t, 1); // 先給 baseline 權重 1
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }

            PageNode p = PageNode.of(
                    r.link,
                    r.title,
                    tf,
                    null,                    // eventDate 暫空
                    "",                      // city 暫時未知（之後可加 LocationRecognizer）
                    extractDomain(r.link),   // domain
                    qTokens                  // tokens 用 query（或可加上 title 斷詞）
            );
            pages.add(p);
        }

        // 3) 排名（會附帶 domain/city bonus）
        RankCalculator.rank(pages, user);
        return pages;
    }

    private static String extractDomain(String url) {
        try {
            String u = url.toLowerCase(Locale.ROOT);
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "example.com";
        }
    }
}