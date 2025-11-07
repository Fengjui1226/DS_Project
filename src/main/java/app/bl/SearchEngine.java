package app.bl;

import app.da.GoogleConnector;
import java.util.*;

public class SearchEngine {

    // 活動相關關鍵字（用來擴充 query & 過濾結果）
    private static final List<String> EVENT_TERMS = List.of(
            "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
            "festival", "concert", "exhibition", "event"
    );

    // 常見城市字詞（最簡單的偵測）
    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
            Map.entry("台北", "台北"),
            Map.entry("臺北", "台北"),
            Map.entry("taipei", "台北"),
            Map.entry("新北", "新北"),
            Map.entry("台中", "台中"),
            Map.entry("臺中", "台中"),
            Map.entry("taichung", "台中"),
            Map.entry("台南", "台南"),
            Map.entry("臺南", "台南"),
            Map.entry("高雄", "高雄"),
            Map.entry("kaohsiung", "高雄"),
            Map.entry("桃園", "桃園"),
            Map.entry("taoyuan", "桃園"),
            Map.entry("基隆", "基隆"),
            Map.entry("新竹", "新竹"),
            Map.entry("苗栗", "苗栗"),
            Map.entry("彰化", "彰化"),
            Map.entry("南投", "南投"),
            Map.entry("雲林", "雲林"),
            Map.entry("嘉義", "嘉義"),
            Map.entry("屏東", "屏東"),
            Map.entry("宜蘭", "宜蘭"),
            Map.entry("花蓮", "花蓮"),
            Map.entry("台東", "台東"),
            Map.entry("臺東", "台東")
    );

    public static List<PageNode> search(String query, UserProfile user) throws Exception {
        // 1) 智能補詞：如果看起來是在找城市，加上活動/展覽/音樂等
        String refinedQuery = refineQuery(query);

        // 2) 打 Google CSE 拿結果
        List<GoogleConnector.Result> raw = GoogleConnector.search(refinedQuery, 10);

        // 3) 過濾：只保留標題（或網址）看起來像活動/展覽類
        List<GoogleConnector.Result> results = filterEventLike(raw);

        // 4) 去重 & 轉 PageNode
        Set<String> seenLinks = new HashSet<>();
        List<PageNode> pages = new ArrayList<>();

        // query tokens（保留使用者原本查的詞）
        List<String> qTokens = new ArrayList<>();
        for (String t : query.split("\\s+")) {
            if (!t.isBlank()) qTokens.add(t.trim());
        }

        // 嘗試從 query 判斷城市（之後可以換 LocationRecognizer）
        String city = detectCityFromQuery(query);

        for (GoogleConnector.Result r : results) {
            if (r == null || r.title == null || r.title.isBlank() || r.link == null || r.link.isBlank()) continue;
            String linkKey = r.link.trim();
            if (!seenLinks.add(linkKey)) continue; // 去重

            // 建 tf：先把使用者原本的 query 詞算進來
            Map<Keyword, Integer> tf = new HashMap<>();
            for (String t : qTokens) {
                Keyword k = new Keyword(t, 1); // baseline 權重 1
                tf.put(k, tf.getOrDefault(k, 0) + 1);
            }
            // 額外：若標題含「活動/展覽/音樂...」，就把那些詞也加進 tf
            for (String term : EVENT_TERMS) {
                if (r.title.toLowerCase().contains(term.toLowerCase())) {
                    Keyword k = new Keyword(term, 1);
                    tf.put(k, tf.getOrDefault(k, 0) + 1);
                }
            }

            // tokens：query 的詞 + 活動關鍵字（碰到才加）
            List<String> tokens = new ArrayList<>(qTokens);
            for (String term : EVENT_TERMS) {
                if (r.title.toLowerCase().contains(term.toLowerCase())) {
                    tokens.add(term);
                }
            }

            PageNode p = PageNode.of(
                    r.link,
                    r.title,
                    tf,
                    null,                   // eventDate 暫無（之後可加 HTML 解析）
                    city,                   // 嘗試填入城市
                    extractDomain(r.link),  // domain
                    tokens                  // tokens
            );
            pages.add(p);
        }

        // 5) 排名（你的 RankCalculator 會再加上 domain/city bonus）
        RankCalculator.rank(pages, user);
        return pages;
    }

    // ------- Helpers -------

    private static String refineQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return q;

        String lower = q.toLowerCase(Locale.ROOT);
        boolean looksLikeCity = CITY_ALIASES.keySet().stream()
                .anyMatch(alias -> lower.contains(alias.toLowerCase(Locale.ROOT)));

        if (looksLikeCity) {
            // 幫使用者自動補「活動/展覽/音樂/節慶/市集」
            q += " 活動 OR 展覽 OR 音樂 OR 節慶 OR 市集";
        }
        return q;
    }

    private static List<GoogleConnector.Result> filterEventLike(List<GoogleConnector.Result> input) {
        List<GoogleConnector.Result> out = new ArrayList<>();
        for (GoogleConnector.Result r : input) {
            if (r == null || r.title == null || r.link == null) continue;
            String t = r.title.toLowerCase(Locale.ROOT);
            String link = r.link.toLowerCase(Locale.ROOT);

            boolean hit = false;
            for (String term : EVENT_TERMS) {
                String tt = term.toLowerCase(Locale.ROOT);
                if (t.contains(tt) || link.contains(tt)) {
                    hit = true;
                    break;
                }
            }
            if (hit) out.add(r);
        }
        return out;
    }

    private static String detectCityFromQuery(String query) {
        if (query == null) return "";
        String lower = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return "";
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
