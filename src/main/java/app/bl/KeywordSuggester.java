package app.bl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * KeywordSuggester - 搜尋關鍵字推薦
 *
 * 類似 Google 的「其他人也搜尋了」功能
 *
 * ✅ Java 8 相容
 * ✅ 支援兩種呼叫：
 *   1) suggest(String query, String city)
 *   2) suggest(String query, List<PageNode> lastResults)  ← 給 SimpleServer 用
 */
public class KeywordSuggester {

    // 城市相關推薦（Java 8 寫法）
    private static final Map<String, List<String>> CITY_SUGGESTIONS = new HashMap<String, List<String>>();
    static {
        CITY_SUGGESTIONS.put("台北", Arrays.asList("台北展覽", "台北市集", "台北演唱會", "信義區活動", "華山活動", "松菸展覽"));
        CITY_SUGGESTIONS.put("新北", Arrays.asList("新北耶誕城", "板橋活動", "淡水活動", "新北市集"));
        CITY_SUGGESTIONS.put("台中", Arrays.asList("台中展覽", "勤美活動", "台中市集", "台中演唱會"));
        CITY_SUGGESTIONS.put("台南", Arrays.asList("台南古蹟", "台南市集", "藍晒圖活動", "台南展覽"));
        CITY_SUGGESTIONS.put("高雄", Arrays.asList("高雄展覽", "駁二活動", "高雄演唱會", "衛武營活動"));
        CITY_SUGGESTIONS.put("桃園", Arrays.asList("桃園展覽", "中壢活動", "華泰名品城"));
    }

    // 類型相關推薦（Java 8 寫法）
    private static final Map<String, List<String>> TYPE_SUGGESTIONS = new HashMap<String, List<String>>();
    static {
        TYPE_SUGGESTIONS.put("市集", Arrays.asList("聖誕市集", "文創市集", "假日市集", "手作市集", "夜市"));
        TYPE_SUGGESTIONS.put("展覽", Arrays.asList("免費展覽", "藝術展", "攝影展", "互動展覽", "博物館"));
        TYPE_SUGGESTIONS.put("音樂", Arrays.asList("演唱會", "音樂節", "live house", "免費演唱會"));
        TYPE_SUGGESTIONS.put("演唱會", Arrays.asList("跨年演唱會", "演唱會門票", "小巨蛋演唱會"));
        TYPE_SUGGESTIONS.put("親子", Arrays.asList("親子展覽", "兒童活動", "親子餐廳", "親子市集", "親子DIY"));
        TYPE_SUGGESTIONS.put("戶外", Arrays.asList("露營活動", "登山活動", "野餐活動", "路跑活動"));
        TYPE_SUGGESTIONS.put("畫展", Arrays.asList("免費畫展", "藝術展覽", "美術館展覽", "當代藝術展"));
        TYPE_SUGGESTIONS.put("棒球", Arrays.asList("棒球比賽", "棒球賽程", "棒球直播", "棒球今天", "棒球台灣"));
    }

    // 時間相關推薦
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList(
            "週末活動", "今天活動", "本週活動", "免費活動", "跨年活動"
    );

    // 熱門推薦（默認）
    private static final List<String> HOT_SUGGESTIONS = Arrays.asList(
            "聖誕市集", "跨年演唱會", "免費展覽", "親子活動", "戶外活動",
            "台北展覽", "週末市集", "音樂節"
    );

    /**
     * ✅ 新增：給 SimpleServer 用的版本
     * SimpleServer 目前會呼叫：suggest(query, lastResults)
     */
    public static List<String> suggest(String query, List<PageNode> lastResults) {
        String inferredCity = inferCityFromResults(lastResults);
        return suggest(query, inferredCity);
    }

    /**
     * 根據搜尋關鍵字生成推薦
     *
     * @param query 使用者的搜尋關鍵字
     * @param city 使用者的城市
     * @return 推薦關鍵字列表（最多 8 個）
     */
    public static List<String> suggest(String query, String city) {
        Set<String> suggestions = new LinkedHashSet<String>();
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
                // 原本你這裡想避免重複：把 city 拿掉再比
                String check = s.toLowerCase().replace(city.toLowerCase(), "");
                if (!lowerQuery.contains(check)) {
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
        List<String> result = new ArrayList<String>(suggestions);
        if (result.size() > 8) {
            result = result.subList(0, 8);
        }

        return result;
    }

    /**
     * 從 lastResults 推測城市（取出現次數最多的城市）
     * 只接受 CITY_SUGGESTIONS 有定義的城市，避免雜訊
     */
    private static String inferCityFromResults(List<PageNode> lastResults) {
        if (lastResults == null || lastResults.isEmpty()) return null;

        Map<String, Integer> countMap = new HashMap<String, Integer>();

        for (PageNode p : lastResults) {
            if (p == null) continue;

            String c = p.getCity();
            if (c == null) continue;

            c = c.trim();
            if (c.isEmpty()) continue;

            if (!CITY_SUGGESTIONS.containsKey(c)) continue;

            Integer old = countMap.get(c);
            countMap.put(c, old == null ? 1 : old + 1);
        }

        String bestCity = null;
        int bestCount = 0;

        for (Map.Entry<String, Integer> e : countMap.entrySet()) {
            if (e.getValue() != null && e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestCity = e.getKey();
            }
        }

        return bestCity;
    }

    /**
     * 取得熱門搜尋（首頁用）
     */
    public static List<String> getHotSearches() {
        return new ArrayList<String>(HOT_SUGGESTIONS);
    }

    // ✅ main 測試用
    public static void main(String[] args) {
        System.out.println("== KeywordSuggester quick test ==");

        // 測試 1：沒有 lastResults
        System.out.println("suggest(\"親子\", (List<PageNode>)null) => " + suggest("親子", (List<PageNode>) null));

        // 測試 2：指定城市
        System.out.println("suggest(\"展覽\", \"台北\") => " + suggest("展覽", "台北"));

        // 測試 3：熱門
        System.out.println("getHotSearches() => " + getHotSearches());

        // 若你要真的測 inferCityFromResults，需要有 PageNode 實例（看你 PageNode 建構子怎麼寫）
        System.out.println("Done.");
    }
}
