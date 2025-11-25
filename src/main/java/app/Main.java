package app;

import app.bl.*;
import app.da.FileHandler;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String query = (args != null && args.length > 0) ? String.join(" ", args) : "台北 音樂 活動";

        // 可以先設定一下預設城市，之後接 UI/設定檔再動態調
        UserProfile user = new UserProfile();
        user.setUserCity("台北");

        System.out.println("# Search for: " + query);
        List<PageNode> results = SearchEngine.search(query, user);

        // 儲存
        FileHandler.savePagesCSV(results, "resources/events.csv");

        // 列印
        int i = 1;
        for (PageNode p : results) {
            String url = p.getUrl();
            String displayText = (url.length() > 30) ? url.substring(0, 30) + "..." : url; // 顯示簡化的 URL
            System.out.printf("%2d. %.2f  [%s] %s%n    %s%n",
                    i++,
                    p.getScore(),
                    p.getCity() == null ? "" : p.getCity(),
                    p.getTitle(),
                    displayText);
        }
        if (results.isEmpty()) {
            System.out.println("(No results — check API key / CX / quota)");
        }

        // ---- UserProfile 更新（需求 3）----
        // 先用 CLI 模擬「點擊第一筆」：把第一筆的 tokens 納入偏好，並記錄最近行為
        if (!results.isEmpty()) {
            PageNode top = results.get(0);
            for (String t : top.getTokens()) {
                user.bump(t);
            }
            user.bumpHabit("點擊");
        }
    }
}