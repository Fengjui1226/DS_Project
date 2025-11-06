package app.bl;

import java.util.*;

public class RankCalculator {

    // 你可以視需要把 alpha 調整得更大更小，代表偏好對權重的影響力
    private static final double ALPHA = 0.2;

    public static void rank(List<PageNode> pages, UserProfile user) {
        if (pages == null) return;

        for (PageNode p : pages) {
            double score = 0.0;

            // 基礎分：用 tf * (使用者調整後的 keyword 權重)
            for (Map.Entry<Keyword, Integer> e : p.tf().entrySet()) {
                Keyword k = e.getKey();
                int tf = e.getValue() == null ? 0 : e.getValue();
                score += user.adjustedWeight(k, ALPHA) * tf;
            }

            // ---- Domain / City Bonus（需求 4）----
            double bonus = 0.0;

            String d = p.getDomain() == null ? "" : p.getDomain();
            if (d.endsWith(".gov.tw") || d.endsWith(".edu.tw") || d.endsWith(".org")) {
                bonus += 3.0;
            }

            String city = p.getCity();
            if (city != null && !city.isBlank() && city.equals(user.getUserCity())) {
                bonus += 2.0;
            }

            p.setScore(score + bonus);
        }

        // 依分數由高到低排序
        pages.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    }
}