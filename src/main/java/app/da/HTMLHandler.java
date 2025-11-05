package app.da;

import java.util.*;

public class HTMLHandler {
    public static Map<String, String> parseHTML(String html) {
        // TODO: 簡單模擬 HTML 抽取標題與日期
        Map<String, String> info = new HashMap<>();
        info.put("title", "台北音樂節");
        info.put("date", "2025-11-10");
        info.put("city", "台北");
        return info;
    }
}