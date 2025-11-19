package app.bl;

import java.util.*;

/**
 * RankCalculator - 計算頁面排名
 * 
 * Formula: EventScore = (∑ count × finalWeight × proximityBonus × regionBoost × habitBoost × domainBoost) × freshness
 */
public class RankCalculator {

    private static final double ALPHA = 0.2;
    private static final double GOV_EDU_ORG_BOOST = 1.1;
    private static final double AD_PENALTY = 0.9;
    
    private static final Set<String> AD_DOMAINS = Set.of(
        "shopee", "momo", "pchome", "yahoo", "ruten"
    );

    public static void rank(List<PageNode> pages, UserProfile user) {
        if (pages == null || pages.isEmpty()) return;

        List<String> queryTokens = new ArrayList<>();
        for (PageNode p : pages) {
            queryTokens.addAll(p.getTokens());
            break;
        }

        for (PageNode p : pages) {
            double score = calculateScore(p, user, queryTokens);
            p.setScore(score);
        }

        pages.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    }

    private static double calculateScore(PageNode p, UserProfile user, List<String> queryTokens) {
        // 1. Base score from keyword TF
        double baseScore = 0.0;
        for (Map.Entry<Keyword, Integer> e : p.tf().entrySet()) {
            Keyword k = e.getKey();
            int tf = e.getValue() == null ? 0 : e.getValue();
            double adjustedWeight = user.adjustedWeight(k, ALPHA);
            baseScore += adjustedWeight * tf;
        }

        // 2. Proximity bonus
        double proximityBonus = p.calculateProximityBonus(queryTokens);

        // 3. Region boost
        double regionBoost = p.calculateRegionBoost(user.getUserCity());

        // 4. Habit boost
        double habitBoost = 1.0 + user.getTotalHabitBoost(p.getTokens());

        // 5. Domain credibility boost
        double domainBoost = calculateDomainBoost(p.getDomain());

        // 6. Freshness score
        double freshness = p.calculateFreshness();

        // Combined score
        double combinedScore = baseScore * proximityBonus * regionBoost * habitBoost * domainBoost;
        return combinedScore * freshness;
    }

    private static double calculateDomainBoost(String domain) {
        if (domain == null || domain.isEmpty()) return 1.0;
        
        String d = domain.toLowerCase(Locale.ROOT);
        
        if (d.endsWith(".gov.tw") || d.endsWith(".edu.tw") || d.endsWith(".org")) {
            return GOV_EDU_ORG_BOOST;
        }
        
        if (d.contains("culture") || d.contains("taipei") || d.contains("gov")) {
            return GOV_EDU_ORG_BOOST;
        }
        
        for (String ad : AD_DOMAINS) {
            if (d.contains(ad)) return AD_PENALTY;
        }
        
        return 1.0;
    }
}