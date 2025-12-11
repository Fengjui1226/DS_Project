package app.bl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.bl.Constants.*;

/**
 * EventInfoExtractor v3.1 - 完整修復版
 * * 結合了 v2.0 的完整 Regex 規則與 v3.0 的智慧日期邏輯。
 * * 功能：
 * 1. [智慧日期] 優先找未來日期，過濾舊年份。
 * 2. [完整提取] 包含時間、地點、票價、主辦單位的提取邏輯。
 */
public class EventInfoExtractor {

    public static class EventInfo {
        public LocalDate startDate;
        public LocalDate endDate;
        public LocalTime startTime;
        public LocalTime endTime;
        public String venue;
        public String address;
        public String city;
        public Integer priceMin;
        public Integer priceMax;
        public boolean isFree;
        public String organizer;
        public String eventType;
        public double completenessScore;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("EventInfo{\n");
            if (startDate != null) sb.append("  日期: ").append(startDate);
            if (endDate != null && !endDate.equals(startDate)) sb.append(" ~ ").append(endDate);
            sb.append("\n");
            if (startTime != null) sb.append("  時間: ").append(startTime);
            if (endTime != null) sb.append(" ~ ").append(endTime);
            sb.append("\n");
            if (venue != null) sb.append("  場館: ").append(venue).append("\n");
            if (address != null) sb.append("  地址: ").append(address).append("\n");
            if (city != null) sb.append("  城市: ").append(city).append("\n");
            if (isFree) {
                sb.append("  票價: 免費\n");
            } else if (priceMin != null) {
                sb.append("  票價: NT$").append(priceMin);
                if (priceMax != null && !priceMax.equals(priceMin)) sb.append(" ~ NT$").append(priceMax);
                sb.append("\n");
            }
            if (organizer != null) sb.append("  主辦: ").append(organizer).append("\n");
            if (eventType != null) sb.append("  類型: ").append(eventType).append("\n");
            sb.append("  完整度: ").append(String.format("%.1f", completenessScore)).append("%\n");
            sb.append("}");
            return sb.toString();
        }
    }

    // ================= 正則表達式庫 (完整版) =================

    private static final List<Pattern> DATE_PATTERNS = List.of(
        Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})\\s*[~至到-]\\s*(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})"),
        Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})\\s*[~至到-]\\s*(\\d{1,2})[/.-](\\d{1,2})"),
        Pattern.compile("(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?\\s*[~至到-]\\s*(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?"),
        Pattern.compile("(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?\\s*[~至到-]\\s*(\\d{1,2})月(\\d{1,2})日?"),
        Pattern.compile("(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?"),
        Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})"),
        Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日"),
        Pattern.compile("(\\d{1,2})月(\\d{1,2})日")
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(\\d{1,2})[:：](\\d{2})\\s*[~至到-]?\\s*(?:(\\d{1,2})[:：](\\d{2}))?"
    );

    private static final List<Pattern> PRICE_PATTERNS = List.of(
        Pattern.compile("NT\\$?\\s*(\\d{1,5})\\s*[~至到-]\\s*NT?\\$?\\s*(\\d{1,5})"),
        Pattern.compile("票價[：:]?\\s*(\\d{1,5})\\s*[~至到/-]\\s*(\\d{1,5})"),
        Pattern.compile("票價[：:]?\\s*NT?\\$?\\s*(\\d{1,5})"),
        Pattern.compile("(\\d{3,5})\\s*元"),
        Pattern.compile("\\$(\\d{3,5})")
    );

    private static final Pattern FREE_PATTERN = Pattern.compile(
        "免費|免票|free|Free|FREE|入場免費|免費入場|免費參觀|自由入場"
    );

    private static final List<Pattern> ORGANIZER_PATTERNS = List.of(
        Pattern.compile("主辦[單位方]?[：:]\\s*(.{2,30})"),
        Pattern.compile("主辦[：:]\\s*(.{2,30})"),
        Pattern.compile("指導單位[：:]\\s*(.{2,30})"),
        Pattern.compile("協辦[：:]\\s*(.{2,30})")
    );

    // ================= 主要提取邏輯 =================

    public static EventInfo extract(String text) {
        if (text == null || text.isEmpty()) {
            return new EventInfo();
        }

        EventInfo info = new EventInfo();
        
        // 1. 日期提取 (使用 v3.0 的智慧邏輯)
        extractSmartDates(text, info);
        
        // 2. 其他資訊提取 (恢復 v2.0 的完整邏輯)
        extractTimes(text, info);
        extractVenue(text, info);
        extractPrice(text, info);
        extractOrganizer(text, info);
        extractEventType(text, info);
        
        info.completenessScore = calculateCompleteness(info);

        return info;
    }

    public static EventInfo extractAndUpdate(PageNode page) {
        if (page == null) return new EventInfo();

        String content = (page.getTitle() != null ? page.getTitle() + " " : "") +
                         (page.getTextContent() != null ? page.getTextContent() : "");

        EventInfo info = extract(content);

        if (info.startDate != null && page.getEventDate() == null) {
            page.setEventDate(info.startDate);
        }
        if (info.city != null && (page.getCity() == null || "全台".equals(page.getCity()))) {
            page.setCity(info.city);
        }

        return info;
    }

    public static void applyCompletenessBonus(List<PageNode> pages) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n📋 結構化資訊提取中...");
        int extracted = 0;

        for (PageNode page : pages) {
            EventInfo info = extractAndUpdate(page);
            double bonus = info.completenessScore * 0.2;
            page.addScore(bonus);

            if (info.priceMin != null || info.isFree) {
                page.addScore(5);
            }
            if (info.venue != null) {
                page.addScore(3);
            }
            if (info.completenessScore > 30) {
                extracted++;
            }
        }

        System.out.println("✅ 結構化提取完成，" + extracted + "/" + pages.size() + " 個結果有詳細資訊");
    }

    // ================= 智慧日期提取 (v3.0 Logic) =================

    private static void extractSmartDates(String text, EventInfo info) {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        List<LocalDate> foundDates = new ArrayList<>();

        // 掃描所有可能的日期
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) { 
                LocalDate date = parseDateFromMatcher(matcher, currentYear);
                if (date != null) {
                    foundDates.add(date);
                }
            }
        }

        if (foundDates.isEmpty()) return;

        // 規則 A: 忽略太舊的日期 (超過 1 年前)
        foundDates.removeIf(d -> d.isBefore(today.minusDays(365)));

        // 規則 B: 優先找「未來日期」
        List<LocalDate> futureDates = new ArrayList<>();
        List<LocalDate> pastDates = new ArrayList<>();

        for (LocalDate d : foundDates) {
            if (d.isAfter(today) || d.isEqual(today)) {
                futureDates.add(d);
            } else {
                pastDates.add(d);
            }
        }

        // 決策
        if (!futureDates.isEmpty()) {
            Collections.sort(futureDates);
            info.startDate = futureDates.get(0);
            if (futureDates.size() > 1) {
                info.endDate = futureDates.get(futureDates.size() - 1);
            }
        } else if (!pastDates.isEmpty()) {
            // 如果只有過去日期，取「離今天最近的」
            pastDates.sort(Collections.reverseOrder());
            info.startDate = pastDates.get(0);
        }
    }

    private static LocalDate parseDateFromMatcher(Matcher matcher, int currentYear) {
        try {
            int y = currentYear, m = 0, d = 0;
            // 處理不同的 regex group
            if (matcher.groupCount() >= 6 && matcher.group(4) != null) {
                 // 區間日期 (yyyy-mm-dd ~ yyyy-mm-dd) 的第一組
                 y = parseYear(matcher.group(1), currentYear);
                 m = Integer.parseInt(matcher.group(2));
                 d = Integer.parseInt(matcher.group(3));
            } else if (matcher.groupCount() >= 3) {
                // yyyy-mm-dd
                y = parseYear(matcher.group(1), currentYear);
                m = Integer.parseInt(matcher.group(2));
                d = Integer.parseInt(matcher.group(3));
            } else if (matcher.groupCount() == 2) {
                // mm-dd (預設今年)
                m = Integer.parseInt(matcher.group(1));
                d = Integer.parseInt(matcher.group(2));
                if (LocalDate.now().getMonthValue() >= 11 && m <= 2) {
                    y = currentYear + 1;
                }
            }
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    // ================= 輔助提取方法 (v2.0 Restored) =================

    private static int parseYear(String yearStr, int currentYear) {
        int year = Integer.parseInt(yearStr);
        if (year < 200) { // 民國年處理
            year += 1911;
        }
        return year;
    }

    private static void extractTimes(String text, EventInfo info) {
        Matcher matcher = TIME_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int hour1 = Integer.parseInt(matcher.group(1));
                int min1 = Integer.parseInt(matcher.group(2));
                info.startTime = LocalTime.of(hour1, min1);

                if (matcher.group(3) != null && matcher.group(4) != null) {
                    int hour2 = Integer.parseInt(matcher.group(3));
                    int min2 = Integer.parseInt(matcher.group(4));
                    info.endTime = LocalTime.of(hour2, min2);
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static void extractVenue(String text, EventInfo info) {
        for (Map.Entry<String, String> entry : VENUE_CITY.entrySet()) {
            if (text.contains(entry.getKey())) {
                info.venue = entry.getKey();
                info.city = entry.getValue();
                break;
            }
        }
        // 簡單地址提取
        Pattern addressPattern = Pattern.compile(
            "(台北|新北|桃園|台中|台南|高雄|基隆|新竹|嘉義|花蓮|台東|宜蘭|苗栗|彰化|南投|雲林|屏東|澎湖|金門|連江)" +
            "[市縣]?.{2,30}[路街道巷弄號樓]"
        );
        Matcher addressMatcher = addressPattern.matcher(text);
        if (addressMatcher.find()) {
            info.address = addressMatcher.group();
            if (info.city == null) {
                info.city = addressMatcher.group(1);
            }
        }
    }

    private static void extractPrice(String text, EventInfo info) {
        if (FREE_PATTERN.matcher(text).find()) {
            info.isFree = true;
            info.priceMin = 0;
            info.priceMax = 0;
            return;
        }
        for (Pattern pattern : PRICE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    info.priceMin = Integer.parseInt(matcher.group(1));
                    if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                        info.priceMax = Integer.parseInt(matcher.group(2));
                    } else {
                        info.priceMax = info.priceMin;
                    }
                    return;
                } catch (Exception e) {}
            }
        }
    }

    private static void extractOrganizer(String text, EventInfo info) {
        for (Pattern pattern : ORGANIZER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String org = matcher.group(1).trim();
                org = org.replaceAll("[，。、；\\s]+$", "");
                if (org.length() >= 2 && org.length() <= 30) {
                    info.organizer = org;
                    return;
                }
            }
        }
    }

    private static void extractEventType(String text, EventInfo info) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : EVENT_TYPE_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                info.eventType = entry.getValue();
                return;
            }
        }
    }

    private static double calculateCompleteness(EventInfo info) {
        double score = 0;
        if (info.startDate != null) {
            score += 20;
            if (info.endDate != null) score += 10;
        }
        if (info.startTime != null) score += 10;
        if (info.venue != null) score += 15;
        if (info.city != null) score += 10;
        if (info.isFree || info.priceMin != null) score += 20;
        if (info.organizer != null) score += 10;
        if (info.eventType != null) score += 5;
        return score;
    }
}