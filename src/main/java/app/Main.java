package app;

import app.bl.*;        // SearchEngine, PageNode, UserProfile, Keyword, RankCalculator
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // 1) 使用者狀態（之後可從設定檔或 UI 帶入）
        UserProfile user = new UserProfile();
        user.setUserCity("台北");     // 你可以改成使用者所在城市
        user.bump("音樂");           // 偏好
        user.bumpHabit("音樂");      // 近期行為

        // 2) 取得查詢字串：優先用命令列參數，否則用預設
        String query = (args != null && args.length > 0)
                ? String.join(" ", args)
                : "台北 音樂 活動";

        // 3) 搜尋（會走 SearchEngine：GoogleConnector + HTMLHandler（目前是模擬））
        List<PageNode> results = SearchEngine.search(query, user);

        // 4) 輸出（注意 PageNode 需有 getEventDate()/getCity()/getTitle()/getUrl()/getScore()）
        for (PageNode p : results) {
            System.out.printf("%.2f  [%s] %s  (%s)  %s%n",
                    p.getScore(),
                    p.getCity(),
                    p.getTitle(),
                    String.valueOf(p.getEventDate()), // 目前 HTMLHandler 是假資料，可能為 null
                    p.getUrl());
        }
    }
}