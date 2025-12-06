package app.bl;

import java.util.*;

/**
 * KeywordSuggester - 搜尋關鍵字推薦
 * 
 * 類似 Google 的「其他人也搜尋了」功能
 */
public class KeywordSuggester {

    // 城市相關推薦
    private static final Map<String, List<String>> CITY_SUGGESTIONS = Map.ofEntries(
        Map.entry("台北", List.of("台北展覽", "台北市集", "台北演唱會", "信義區活動", "華山活動", "松菸展覽")),
        Map.entry("新北", List.of("新北耶誕城", "板橋活動", "淡水活動", "新北市集")),
        Map.entry("台中", List.of("台中展覽", "勤美活動", "台中市集", "台中演唱會")),
        Map.entry("台南", List.of("台南古蹟", "台南市集", "藍晒圖活動", "台南展覽")),
        Map.entry("高雄", List.of("高雄展覽", "駁二活動", "高雄演唱會", "衛武營活動")),
        Map.entry("桃園", List.of("桃園展覽", "中壢活動", "華泰名品城"))
    );

    // 類型相關推薦
    private static final Map<String, List<String>> TYPE_SUGGESTIONS = Map.ofEntries(
        Map.entry("市集", List.of("聖誕市集", "文創市集", "假日市集", "手作市集", "夜市")),
        Map.entry("展覽", List.of("免費展覽", "藝術展", "攝影展", "互動展覽", "博物館")),
        Map.entry("音樂", List.of("演唱會", "音樂節", "live house", "免費演唱會")),
        Map.entry("演唱會", List.of("跨年演唱會", "演唱會門票", "小巨蛋演唱會")),
        Map.entry("親子", List.of("親子展覽", "兒童活動", "親子餐廳", "親子市集", "親子DIY")),
        Map.entry("戶外", List.of("露營活動", "登山活動", "野餐活動", "路跑活動")),
        Map.entry("畫展", List.of("免費畫展", "藝術展覽", "美術館展覽", "當代藝術展")),
        Map.entry("棒球", List.of("棒球比賽", "棒球賽程", "棒球直播", "棒球今天", "棒球台灣"))
    );

    // 時間相關推薦
    private static final List<String> TIME_SUGGESTIONS = List.of(
        "週末活動", "今天活動", "本週活動", "免費活動", "跨年活動"
    );

    // 熱門推薦（默認）
    private static final List<String> HOT_SUGGESTIONS = List.of(
        "聖誕市集", "跨年演唱會", "免費展覽", "親子活動", "戶外活動",
        "台北展覽", "週末市集", "音樂節"
    );

    /**
     * 根據搜尋關鍵字生成推薦
     * 
     * @param query 使用者的搜尋關鍵字
     * @param city 使用者的城市
     * @return 推薦關鍵字列表（最多 8 個）
     */
    public static List<String> suggest(String query, String city) {
        Set<String> suggestions = new LinkedHashSet<>();
        String lowerQuery = query != null ? query.toLowerCase() : "";

        // 1. 根據查詢類型加入推薦
        for (Map.Entry<String, List<String>> entry : TYPE_SUGGESTIONS.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                for (String s : entry.getValue()) {
                    if (!lowerQuery.contains(s.toLowerCase())) {
                        suggestions.add(s);
                    }
                }
            }
        }

        // 2. 根據城市加入推薦
        if (city != null && CITY_SUGGESTIONS.containsKey(city)) {
            for (String s : CITY_SUGGESTIONS.get(city)) {
                if (!lowerQuery.contains(s.toLowerCase().replace(city, ""))) {
                    suggestions.add(s);
                }
            }
        }

        // 3. 加入時間相關推薦
        for (String s : TIME_SUGGESTIONS) {
            if (!lowerQuery.contains(s.replace("活動", ""))) {
                suggestions.add(s);
            }
        }

        // 4. 如果推薦太少，加入熱門推薦
        if (suggestions.size() < 6) {
            for (String s : HOT_SUGGESTIONS) {
                if (!lowerQuery.contains(s.toLowerCase())) {
                    suggestions.add(s);
                }
            }
        }

        // 限制最多 8 個
        List<String> result = new ArrayList<>(suggestions);
        if (result.size() > 8) {
            result = result.subList(0, 8);
        }

        return result;
    }

    /**
     * 取得熱門搜尋（首頁用）
     */
    public static List<String> getHotSearches() {
        return new ArrayList<>(HOT_SUGGESTIONS);
    }
}