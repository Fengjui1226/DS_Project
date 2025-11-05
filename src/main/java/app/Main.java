package app;

import app.bl.*;
import java.time.LocalDate;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // 關鍵字（基礎權重）
        Keyword k1 = new Keyword("活動", 6);
        Keyword k2 = new Keyword("展覽", 5);
        Keyword k3 = new Keyword("音樂", 4);

        // 範例頁面 1：台北、近五天、gov 網域、含斷詞
        PageNode p1 = PageNode.of(
            "https://ex1", "台北音樂節",
            Map.of(k1, 2, k3, 3),
            LocalDate.now().plusDays(5),
            "台北",
            "culture.gov.tw",
            List.of("台北", "音樂節", "活動", "時間", "周末", "地點", "信義區")
        );

        // 範例頁面 2：台中、20 天後、一般網域
        PageNode p2 = PageNode.of(
            "https://ex2", "台中創作展",
            Map.of(k1, 1, k2, 4),
            LocalDate.now().plusDays(20),
            "台中",
            "example.com",
            List.of("台中", "創作", "展覽", "活動", "時間", "地點")
        );

        // 使用者偏好
        UserProfile up = new UserProfile();
        up.setUserCity("台北");
        up.bump("音樂");       // 偏好音樂 +1
        up.bump("音樂");       // 再 +1
        up.bumpHabit("音樂");  // 最近行為也強化音樂

        // 排名
        List<PageNode> pages = new ArrayList<>(List.of(p1, p2));
        RankCalculator.rank(pages, up);

        // 輸出結果
        for (PageNode p : pages) {
            System.out.printf(
                "%.2f  [%s] %s  (%s)  %s%n",
                p.getScore(), p.getCity(), p.getTitle(),
                p.getEventDate(), p.getUrl()
            );
        }
    }
}