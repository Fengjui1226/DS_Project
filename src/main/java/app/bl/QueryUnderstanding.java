package app.bl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.bl.Constants.*;

/**
 * QueryUnderstanding v2.0 - 重構版（使用統一常數）
 */
public class QueryUnderstanding {

    public enum QueryIntent {
        FIND_EVENT, FIND_BY_DATE, FIND_BY_PLACE, FIND_BY_TYPE, FIND_FREE, FIND_NEARBY, GENERAL
    }

    public static class ParsedQuery {
        public String originalQuery;
        public String expandedQuery;
        public QueryIntent primaryIntent;
        public List<QueryIntent> intents = new ArrayList<>();
        public String city;
        public String venue;
        public String eventType;
        public LocalDate targetDate;
        public LocalDate dateRangeStart;
        public LocalDate dateRangeEnd;
        public boolean wantsFree = false;
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
        result.city = extractCityFromQuery(q);

        // 2. 提取場館
        result.venue = extractVenue(q);
        if (result.venue != null && result.city == null) {
            result.city = getCityByVenue(result.venue);
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
        result.expandedQuery = expandQueryText(q, result);

        // 8. 提取關鍵字
        result.keywords = extractKeywords(q);

        return result;
    }

    public static String getExpandedQuery(String query) {
        return parse(query).expandedQuery;
    }

    public static boolean hasTimeConstraint(String query) {
        ParsedQuery parsed = parse(query);
        return parsed.targetDate != null || parsed.dateRangeStart != null;
    }

    private static String extractCityFromQuery(String query) {
        for (String city : TAIWAN_CITIES) {
            if (query.contains(city)) {
                return city;
            }
        }
        // 檢查別名
        String lower = query.toLowerCase();
        for (Map.Entry<String, String> entry : CITY_ALIASES.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
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

        if (q.contains("今天") || q.contains("today")) {
            result.targetDate = today;
            return;
        }
        if (q.contains("明天") || q.contains("tomorrow")) {
            result.targetDate = today.plusDays(1);
            return;
        }
        if (q.contains("週末") || q.contains("周末") || q.contains("weekend")) {
            result.dateRangeStart = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            result.dateRangeEnd = result.dateRangeStart.plusDays(1);
            return;
        }
        if (q.contains("下週末") || q.contains("下周末")) {
            result.dateRangeStart = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(1);
            result.dateRangeEnd = result.dateRangeStart.plusDays(1);
            return;
        }
        if (q.contains("這個月") || q.contains("本月")) {
            result.dateRangeStart = today;
            result.dateRangeEnd = today.with(TemporalAdjusters.lastDayOfMonth());
            return;
        }
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
                year++;
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

        if (result.targetDate != null || result.dateRangeStart != null) {
            intents.add(QueryIntent.FIND_BY_DATE);
        }
        if (result.venue != null || result.city != null) {
            intents.add(QueryIntent.FIND_BY_PLACE);
        }
        if (result.eventType != null) {
            intents.add(QueryIntent.FIND_BY_TYPE);
        }
        if (result.wantsFree) {
            intents.add(QueryIntent.FIND_FREE);
        }
        if (intents.isEmpty()) {
            intents.add(QueryIntent.FIND_EVENT);
        }

        result.intents = intents;
        result.primaryIntent = intents.get(0);
    }

    private static String expandQueryText(String query, ParsedQuery parsed) {
        StringBuilder expanded = new StringBuilder(query);

        // 加入同義詞擴展
        for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
            if (query.contains(entry.getKey())) {
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

        // 確保有活動相關詞
        String q = query.toLowerCase();
        if (!q.contains("活動") && !q.contains("event") &&
            !q.contains("展覽") && !q.contains("演唱會") && !q.contains("市集")) {
            expanded.append(" 活動");
        }

        return expanded.toString().trim();
    }

    private static List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        String[] stopWords = {"的", "在", "有", "要", "找", "想", "去", "看", "參加"};
        String cleaned = query;
        for (String stop : stopWords) {
            cleaned = cleaned.replace(stop, " ");
        }
        for (String word : cleaned.split("\\s+")) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }
        return keywords;
    }

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

    public static List<String> suggestRelatedQueries(String query) {
        ParsedQuery parsed = parse(query);
        List<String> suggestions = new ArrayList<>();

        if (parsed.eventType != null && SYNONYMS.containsKey(parsed.eventType)) {
            for (String syn : SYNONYMS.get(parsed.eventType)) {
                if (!query.contains(syn)) {
                    suggestions.add(query.replace(parsed.eventType, syn));
                }
            }
        }

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