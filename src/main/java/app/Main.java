package app;

import app.bl.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // 假資料跑一圈看看
        Keyword k1 = new Keyword("活動", 6);
        Keyword k2 = new Keyword("展覽", 5);
        Keyword k3 = new Keyword("音樂", 4);

        PageNode p1 = PageNode.of("https://ex1", "台北音樂節", Map.of(k1,2, k3,3));
        PageNode p2 = PageNode.of("https://ex2", "台中創作展", Map.of(k1,1, k2,4));

        UserProfile up = new UserProfile();
        up.bump("音樂"); up.bump("音樂"); // 偏好音樂

        List<PageNode> pages = new ArrayList<>(List.of(p1, p2));
        RankCalculator.rank(pages, up);

        pages.forEach(p ->
            System.out.printf("%.2f  %s  %s%n", p.getScore(), p.getTitle(), p.getUrl()));
    }
}