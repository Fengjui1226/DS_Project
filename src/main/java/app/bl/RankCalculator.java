package app.bl;
import java.util.*;

public class RankCalculator {
    public static void rank(List<PageNode> pages, UserProfile up){
        for (PageNode p : pages){
            double s = 0.0;
            for (Map.Entry<Keyword,Integer> e : p.tf().entrySet()){
                double w = up.adjustedWeight(e.getKey(), 0.2); // 個人化
                s += e.getValue() * w;
            }
            // TODO: freshness/domain/location bonus 可在此加入
            p.setScore(s);
        }
        pages.sort(Comparator.comparingDouble(PageNode::getScore).reversed());
    }
}
