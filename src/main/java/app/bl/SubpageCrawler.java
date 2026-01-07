package app.bl;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * SubpageCrawler - 即時抓取某個 URL 的子頁面連結（不依賴 lastResults）
 *
 * 設計目標：
 * - 前端點 subpages 時，就算後端沒有 lastResults 也能回資料
 * - 只抓同網域連結，避免亂跳
 * - 控制最大數量，避免太慢
 */
public final class SubpageCrawler {

    private SubpageCrawler() {}

    public static final class SubpageInfo {
        public final String url;
        public final String title;
        public final double score; // 目前先給 0.0（之後你要做排序再加）

        public SubpageInfo(String url, String title, double score) {
            this.url = url;
            this.title = title;
            this.score = score;
        }
    }

    /**
     * 即時抓取子頁面（同網域）
     */
    public static List<SubpageInfo> crawl(String pageUrl, int maxLinks, int timeoutMs) {
        List<SubpageInfo> out = new ArrayList<>();
        if (pageUrl == null || pageUrl.isBlank()) return out;

        URI baseUri;
        try {
            baseUri = URI.create(pageUrl);
        } catch (Exception e) {
            return out;
        }

        String baseHost = baseUri.getHost();
        if (baseHost == null || baseHost.isBlank()) return out;

        Set<String> seen = new LinkedHashSet<>();

        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (EventFinderBot)")
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .get();

            Elements links = doc.select("a[href]");
            for (Element a : links) {
                String abs = a.absUrl("href");
                if (abs == null || abs.isBlank()) continue;

                // 過濾常見無效連結
                String lower = abs.toLowerCase();
                if (lower.startsWith("javascript:")) continue;
                if (lower.startsWith("mailto:")) continue;
                if (lower.startsWith("tel:")) continue;
                if (lower.contains("#")) {
                    // 去掉 fragment，避免同頁不同錨點重複
                    abs = abs.substring(0, abs.indexOf('#'));
                }

                // 只留同 host（同網域）
                try {
                    URI u = URI.create(abs);
                    if (u.getHost() == null) continue;
                    if (!u.getHost().equalsIgnoreCase(baseHost)) continue;
                } catch (Exception ignore) {
                    continue;
                }

                // 去尾斜線，避免重複
                abs = normalizeUrl(abs);

                if (abs.equals(normalizeUrl(pageUrl))) continue; // 排除自己
                if (!seen.add(abs)) continue;                    // 去重

                String text = a.text();
                String title = (text != null && !text.isBlank()) ? text.trim() : abs;

                out.add(new SubpageInfo(abs, title, 0.0));

                if (out.size() >= maxLinks) break;
            }
        } catch (Exception e) {
            // 失敗就回空清單，由呼叫端處理
        }

        return out;
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
