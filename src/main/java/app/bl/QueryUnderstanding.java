package app.bl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QueryUnderstanding v1.0 - 查詢意圖理解
 * 
 * 功能：
 * 1. 識別查詢意圖（找活動、找場地、找日期、找類型）
 * 2. 時間表達式解析（這週末、下個月、聖誕節）
 * 3. 查詢擴展與改寫
 * 4. 實體識別（城市、場館、活動類型）
 * 5. 同義詞擴展
 */
public class QueryUnderstanding {

    // ================= 資料結構 =================

    public enum QueryIntent {
        FIND_EVENT,      // 找特定活動
        FIND_BY_DATE,    // 按日期找活動
        FIND_BY_PLACE,   // 按地點找活動
        FIND_BY_TYPE,    // 按類型找活動
        FIND_FREE,       // 找免費活動
        FIND_NEARBY,     // 找附近活動
        GENERAL          // 一般搜尋
    }

    public static class ParsedQuery {
        public String originalQuery;
        public String expandedQuery;
        public QueryIntent primaryIntent;
        public List<QueryIntent> intents = new ArrayList<>();
        
        // 提取的實體
        public String city;
        public String venue;
        public String eventType;
        public LocalDate targetDate;
        public LocalDate dateRangeStart;
        public LocalDate dateRangeEnd;
        public boolean wantsFree = false;
        
        // 查詢修飾
        public List<String> keywords = new ArrayList<>();
        public List<String> synonymExpansions = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ParsedQuery{\n");
            sb.append("  原始: ").append(originalQuery).append("\n");
            sb.append("  擴展: ").append(expandedQuery).append("\n");
            sb.append("  意圖: ").append(primaryIntent);
            if (intents.size() > 1) sb.append(" + ").append(intents.subList(1, intents.size()));
            sb.append("\n");
            if (city != null) sb.append("  城市: ").append(city).append("\n");
            if (venue != null) sb.append("  場館: ").append(venue).append("\n");
            if (eventType != null) sb.append("  類型: ").append(eventType).append("\n");
            if (targetDate != null) sb.append("  目標日期: ").append(targetDate).append("\n");
            if (dateRangeStart != null) {
                sb.append("  日期範圍: ").append(dateRangeStart);
                if (dateRangeEnd != null) sb.append(" ~ ").append(dateRangeEnd);
                sb.append("\n");
            }
            if (wantsFree) sb.append("  偏好: 免費\n");
            if (!keywords.isEmpty()) sb.append("  關鍵字: ").append(keywords).append("\n");
            if (!synonymExpansions.isEmpty()) sb.append("  同義擴展: ").append(synonymExpansions).append("\n");
            sb.append("}");
            return sb.toString();
        }
    }

    // ================= 同義詞庫 =================

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
        // 活動類型
        Map.entry("演唱會", List.of("音樂會", "live", "演出", "concert")),
        Map.entry("展覽", List.of("特展", "展出", "展示", "藝術展", "美術展", "exhibition")),
        Map.entry("市集", List.of("文創市集", "假日市集", "手作市集", "創意市集", "market")),
        Map.entry("音樂節", List.of("音樂祭", "festival", "music festival")),
        Map.entry("講座", List.of("座談會", "分享會", "工作坊", "workshop")),
        Map.entry("路跑", List.of("馬拉松", "健走", "慢跑", "run", "marathon")),
        Map.entry("派對", List.of("party", "趴踢", "狂歡")),
        Map.entry("親子", List.of("兒童", "家庭", "小朋友", "kids", "family")),
        
        // 時間表達
        Map.entry("週末", List.of("周末", "假日", "星期六", "星期日")),
        Map.entry("跨年", List.of("新年", "元旦", "12/31", "1/1")),
        Map.entry("聖誕", List.of("聖誕節", "耶誕", "christmas", "12/25")),
        
        // 場地同義
        Map.entry("華山", List.of("華山1914", "華山文創園區")),
        Map.entry("松菸", List.of("松山文創", "松山菸廠")),
        Map.entry("駁二", List.of("駁二藝術特區", "pier-2")),
        Map.entry("小巨蛋", List.of("台北小巨蛋", "taipei arena"))
    );

    // ================= 時間表達式 =================

    private static final Map<String, Integer> WEEKDAY_MAP = Map.ofEntries(
        Map.entry("週一", 1), Map.entry("週二", 2), Map.entry("週三", 3),
        Map.entry("週四", 4), Map.entry("週五", 5), Map.entry("週六", 6),
        Map.entry("週日", 7),
        Map.entry("星期一", 1), Map.entry("星期二", 2), Map.entry("星期三", 3),
        Map.entry("星期四", 4), Map.entry("星期五", 5), Map.entry("星期六", 6),
        Map.entry("星期日", 7)
    );
    // ================= 城市與場館 =================

    private static final Set<String> CITIES = Set.of(
        "台北", "臺北", "新北", "桃園", "台中", "臺中", "台南", "臺南", "高雄",
        "基隆", "新竹", "嘉義", "花蓮", "台東", "宜蘭", "苗栗", "彰化", "南投",
        "雲林", "屏東", "澎湖", "金門", "連江"
    );

    private static final Map<String, String> VENUE_CITY = Map.ofEntries(
        Map.entry("華山", "台北"), Map.entry("松菸", "台北"), Map.entry("小巨蛋", "台北"),
        Map.entry("大巨蛋", "台北"), Map.entry("兩廳院", "台北"), Map.entry("北流", "台北"),
        Map.entry("駁二", "高雄"), Map.entry("衛武營", "高雄"), Map.entry("高流", "高雄"),
        Map.entry("歌劇院", "台中"), Map.entry("科博館", "台中"),
        Map.entry("故宮", "台北"), Map.entry("北美館", "台北")
    );

    // ================= 活動類型 =================

    private static final Set<String> EVENT_TYPES = Set.of(
        "演唱會", "音樂會", "音樂節", "展覽", "特展", "市集", "夜市",
        "講座", "工作坊", "派對", "路跑", "馬拉松", "親子", "電影",
        "舞台劇", "音樂劇", "脫口秀", "相聲", "魔術", "馬戲"
    );

    // ================= 主要 API =================

    /**
     * 解析查詢
     */
    public static ParsedQuery parse(String query) {
        ParsedQuery result = new ParsedQuery();
        result.originalQuery = query;

        if (query == null || query.trim().isEmpty()) {
            result.primaryIntent = QueryIntent.GENERAL;
            result.expandedQuery = query;
            return result;
        }

        String q = query.trim();

        // 1. 提取城市
        result.city = extractCity(q);

        // 2. 提取場館
        result.venue = extractVenue(q);
        if (result.venue != null && result.city == null) {
            result.city = VENUE_CITY.get(result.venue);
        }

        // 3. 提取活動類型
        result.eventType = extractEventType(q);

        // 4. 解析時間表達式
        parseTimeExpression(q, result);

        // 5. 檢查是否要免費
        result.wantsFree = q.contains("免費") || q.toLowerCase().contains("free");

        // 6. 判斷意圖
        determineIntent(result);

        // 7. 擴展查詢
        result.expandedQuery = expandQuery(q, result);

        // 8. 提取關鍵字
        result.keywords = extractKeywords(q);

        return result;
    }

    /**
     * 取得擴展後的搜尋查詢
     */
    public static String getExpandedQuery(String query) {
        ParsedQuery parsed = parse(query);
        return parsed.expandedQuery;
    }

    /**
     * 檢查查詢是否包含時間限制
     */
    public static boolean hasTimeConstraint(String query) {
        ParsedQuery parsed = parse(query);
        return parsed.targetDate != null || parsed.dateRangeStart != null;
    }

    // ================= 內部方法 =================

    private static String extractCity(String query) {
        for (String city : CITIES) {
            if (query.contains(city)) {
                // 標準化
                if (city.startsWith("臺")) {
                    return city.replace("臺", "台");
                }
                return city;
            }
        }
        return null;
    }

    private static String extractVenue(String query) {
        for (String venue : VENUE_CITY.keySet()) {
            if (query.contains(venue)) {
                return venue;
            }
        }
        return null;
    }

    private static String extractEventType(String query) {
        String lower = query.toLowerCase();
        for (String type : EVENT_TYPES) {
            if (lower.contains(type.toLowerCase())) {
                return type;
            }
        }
        return null;
    }

    private static void parseTimeExpression(String query, ParsedQuery result) {
        LocalDate today = LocalDate.now();
        String q = query.toLowerCase();

        // 今天
        if (q.contains("今天") || q.contains("today")) {
            result.targetDate = today;
            return;
        }

        // 明天
        if (q.contains("明天") || q.contains("tomorrow")) {
            result.targetDate = today.plusDays(1);
            return;
        }

        // 這週末
        if (q.contains("週末") || q.contains("周末") || q.contains("weekend")) {
            result.dateRangeStart = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            result.dateRangeEnd = result.dateRangeStart.plusDays(1);
            return;
        }

        // 下週末
        if (q.contains("下週末") || q.contains("下周末")) {
            result.dateRangeStart = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(1);
            result.dateRangeEnd = result.dateRangeStart.plusDays(1);
            return;
        }

        // 這個月
        if (q.contains("這個月") || q.contains("本月")) {
            result.dateRangeStart = today;
            result.dateRangeEnd = today.with(TemporalAdjusters.lastDayOfMonth());
            return;
        }

        // 下個月
        if (q.contains("下個月") || q.contains("下月")) {
            result.dateRangeStart = today.plusMonths(1).withDayOfMonth(1);
            result.dateRangeEnd = result.dateRangeStart.with(TemporalAdjusters.lastDayOfMonth());
            return;
        }

        // 特定月份
        Pattern monthPattern = Pattern.compile("(\\d{1,2})月");
        Matcher monthMatcher = monthPattern.matcher(query);
        if (monthMatcher.find()) {
            int month = Integer.parseInt(monthMatcher.group(1));
            int year = today.getYear();
            if (month < today.getMonthValue()) {
                year++; // 如果月份已過，預設為明年
            }
            result.dateRangeStart = LocalDate.of(year, month, 1);
            result.dateRangeEnd = result.dateRangeStart.with(TemporalAdjusters.lastDayOfMonth());
            return;
        }

        // 特定節日
        if (q.contains("聖誕") || q.contains("耶誕") || q.contains("christmas")) {
            int year = today.getMonthValue() == 12 && today.getDayOfMonth() > 25 
                       ? today.getYear() + 1 : today.getYear();
            result.dateRangeStart = LocalDate.of(year, 12, 20);
            result.dateRangeEnd = LocalDate.of(year, 12, 26);
            return;
        }

        if (q.contains("跨年") || q.contains("新年") || q.contains("元旦")) {
            int year = today.getMonthValue() >= 11 ? today.getYear() : today.getYear() - 1;
            result.dateRangeStart = LocalDate.of(year, 12, 28);
            result.dateRangeEnd = LocalDate.of(year + 1, 1, 3);
            return;
        }

        if (q.contains("春節") || q.contains("過年") || q.contains("農曆新年")) {
            // 簡化：假設春節在 1-2 月
            int year = today.getMonthValue() > 2 ? today.getYear() + 1 : today.getYear();
            result.dateRangeStart = LocalDate.of(year, 1, 20);
            result.dateRangeEnd = LocalDate.of(year, 2, 15);
            return;
        }

        // 星期幾
        for (Map.Entry<String, Integer> entry : WEEKDAY_MAP.entrySet()) {
            if (q.contains(entry.getKey())) {
                DayOfWeek dow = DayOfWeek.of(entry.getValue());
                result.targetDate = today.with(TemporalAdjusters.nextOrSame(dow));
                return;
            }
        }
    }

    private static void determineIntent(ParsedQuery result) {
        List<QueryIntent> intents = new ArrayList<>();

        // 按優先序判斷
        if (result.targetDate != null || result.dateRangeStart != null) {
            intents.add(QueryIntent.FIND_BY_DATE);
        }

        if (result.venue != null) {
            intents.add(QueryIntent.FIND_BY_PLACE);
        } else if (result.city != null) {
            intents.add(QueryIntent.FIND_BY_PLACE);
        }

        if (result.eventType != null) {
            intents.add(QueryIntent.FIND_BY_TYPE);
        }

        if (result.wantsFree) {
            intents.add(QueryIntent.FIND_FREE);
        }

        // 如果沒有特定意圖，視為找活動
        if (intents.isEmpty()) {
            intents.add(QueryIntent.FIND_EVENT);
        }

        result.intents = intents;
        result.primaryIntent = intents.get(0);
    }

    private static String expandQuery(String query, ParsedQuery parsed) {
        StringBuilder expanded = new StringBuilder(query);

        // 加入同義詞擴展
        for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
            if (query.contains(entry.getKey())) {
                // 只加第一個同義詞（避免過度擴展）
                String synonym = entry.getValue().get(0);
                if (!query.contains(synonym)) {
                    expanded.append(" ").append(synonym);
                    parsed.synonymExpansions.add(synonym);
                }
            }
        }

        // 如果有日期範圍，加入年份
        if (parsed.dateRangeStart != null) {
            String year = String.valueOf(parsed.dateRangeStart.getYear());
            if (!query.contains(year)) {
                expanded.append(" ").append(year);
            }
        }

        // 確保有「活動」或「台灣」相關詞
        String q = query.toLowerCase();
        if (!q.contains("活動") && !q.contains("event") && 
            !q.contains("展覽") && !q.contains("演唱會") && !q.contains("市集")) {
            expanded.append(" 活動");
        }

        return expanded.toString().trim();
    }

    private static List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();

        // 移除常見停用詞後的關鍵字
        String[] stopWords = {"的", "在", "有", "要", "找", "想", "去", "看", "參加"};
        String cleaned = query;
        for (String stop : stopWords) {
            cleaned = cleaned.replace(stop, " ");
        }

        // 分割並過濾
        for (String word : cleaned.split("\\s+")) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    // ================= 工具方法 =================

    /**
     * 取得查詢的時間過濾條件（給 RankCalculator 用）
     */
    public static DateRange getDateRange(String query) {
        ParsedQuery parsed = parse(query);

        if (parsed.targetDate != null) {
            return new DateRange(parsed.targetDate, parsed.targetDate);
        }

        if (parsed.dateRangeStart != null) {
            return new DateRange(
                parsed.dateRangeStart,
                parsed.dateRangeEnd != null ? parsed.dateRangeEnd : parsed.dateRangeStart.plusMonths(1)
            );
        }

        return null;
    }

    public static class DateRange {
        public final LocalDate start;
        public final LocalDate end;

        public DateRange(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }

        public boolean contains(LocalDate date) {
            if (date == null) return false;
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    /**
     * 建議相關查詢
     */
    public static List<String> suggestRelatedQueries(String query) {
        ParsedQuery parsed = parse(query);
        List<String> suggestions = new ArrayList<>();

        // 基於類型建議
        if (parsed.eventType != null && SYNONYMS.containsKey(parsed.eventType)) {
            for (String syn : SYNONYMS.get(parsed.eventType)) {
                if (!query.contains(syn)) {
                    suggestions.add(query.replace(parsed.eventType, syn));
                }
            }
        }

        // 基於城市建議其他城市
        if (parsed.city != null) {
            for (String city : List.of("台北", "台中", "高雄", "台南")) {
                if (!city.equals(parsed.city)) {
                    suggestions.add(query.replace(parsed.city, city));
                }
            }
        }

        return suggestions.subList(0, Math.min(5, suggestions.size()));
    }
}