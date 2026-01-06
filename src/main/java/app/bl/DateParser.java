package app.bl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DateParser v1.0 - 日期解析器
 * 
 * 從 SearchEngine 抽出，負責所有日期相關的解析邏輯：
 * 1. 完整日期格式 (yyyy/MM/dd, yyyy-MM-dd, yyyy年M月d日)
 * 2. 民國年格式 (111年10月2日)
 * 3. 無年份格式 (M月d日, M/d)
 * 4. 相對日期 (今天, 明天, 這週末, 下週一)
 * 5. 智慧年份推測
 */
public final class DateParser {

    private DateParser() {} // 工具類不允許實例化

    /**
     * 主要入口：解析文字中的日期
     * @param text 要解析的文字
     * @param today 今天日期（用於相對日期計算）
     * @return 解析出的日期，找不到則回傳 null
     */
    public static LocalDate parse(String text, LocalDate today) {
        if (text == null || text.isEmpty()) return null;
        int currentYear = today.getYear();

        // Step 0: 全局年份偵測 (Context Year)
        Integer contextYear = extractContextYear(text);

        // Step 0.3: 民國年快速判斷
        LocalDate rocDate = parseRocYearQuick(text, currentYear);
        if (rocDate != null) return rocDate;

        // Step 0.5: 相對日期
        LocalDate relativeDate = parseRelativeDate(text, today);
        if (relativeDate != null) return relativeDate;

        List<LocalDate> foundDates = new ArrayList<>();

        // Step 1: 民國年完整格式
        foundDates.addAll(parseRocFullDates(text, currentYear));

        // Step 2: 西元完整日期 yyyy/MM/dd, yyyy-MM-dd, yyyy.MM.dd
        foundDates.addAll(parseFullDates(text));

        // Step 3: 年份...月日格式（如「2025台北花伴野餐3月15日」）
        foundDates.addAll(parseYearEventDates(text));

        // Step 4: 檢查張貼日期/更新日期（過期判斷）
        LocalDate postDate = parsePostDate(text, currentYear);
        if (postDate != null) return postDate;

        // Step 5: 無年份格式 M月d日 或 M/d
        if (foundDates.isEmpty()) {
            foundDates.addAll(parseNoYearDates(text, today, contextYear));
        }

        if (foundDates.isEmpty()) {
            // Step 6: 特殊關鍵字
            if (text.contains("即日起") || text.contains("常設展") || text.contains("長期展出")) {
                return today.plusDays(30);
            }

            // 過期保底：標題明確是舊年份
            if (contextYear != null && contextYear < currentYear) {
                return LocalDate.of(contextYear, 12, 31);
            }
            return null;
        }

        // 優先回傳未來日期中最近的
        List<LocalDate> futureDates = foundDates.stream()
            .filter(d -> !d.isBefore(today))
            .sorted()
            .collect(Collectors.toList());

        if (!futureDates.isEmpty()) {
            return futureDates.get(0);
        }

        // Step 7: 未來年份救援
        LocalDate rescueDate = rescueFutureYear(text, currentYear);
        if (rescueDate != null) return rescueDate;

        // 都是過去日期，回傳最近的
        return foundDates.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElse(null);
    }

    /**
     * 從標題提取日期（簡化入口）
     */
    public static LocalDate parseFromTitle(String title, LocalDate today) {
        return parse(title, today);
    }

    /**
     * 從內容提取日期（簡化入口）
     */
    public static LocalDate parseFromContent(String content, LocalDate today) {
        return parse(content, today);
    }

    // ==================== 內部解析方法 ====================

    /**
     * 提取全局年份上下文（如 "2024 聖誕市集"）
     */
    private static Integer extractContextYear(String text) {
        Pattern p = Pattern.compile("(?i)(?:^|[\\s【\\[(#])(20[2-9]\\d)(?:$|[\\s】\\])年]|全台|精選|最新|活動|整理|市集|展覽|大賞|節|懶人包)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /**
     * 民國年快速判斷（如「108年度」「111年」）
     */
    private static LocalDate parseRocYearQuick(String text, int currentYear) {
        int currentRocYear = currentYear - 1911;
        Pattern p = Pattern.compile("(1[0-9][0-9])年(?:度)?(?:原|市集|活動|展覽|好市)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            int rocYear = Integer.parseInt(m.group(1));
            if (rocYear < currentRocYear - 1) {
                int adYear = rocYear + 1911;
                return LocalDate.of(adYear, 12, 31);
            }
        }
        return null;
    }

    /**
     * 解析相對日期（今天、明天、週末、星期X等）
     */
    public static LocalDate parseRelativeDate(String text, LocalDate today) {
        if (text == null) return null;

        // 今天、明天、後天
        if (text.contains("今天") || text.contains("今日")) return today;
        if (text.contains("明天") || text.contains("明日")) return today.plusDays(1);
        if (text.contains("後天")) return today.plusDays(2);

        // 這週末、本週末
        if (text.contains("這週末") || text.contains("本週末") || 
            text.contains("這個週末") || text.contains("本周末")) {
            int daysUntilSat = (DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            if (daysUntilSat == 0 && today.getDayOfWeek() != DayOfWeek.SATURDAY) {
                daysUntilSat = 7;
            }
            return today.plusDays(daysUntilSat);
        }

        // 下週末
        if (text.contains("下週末") || text.contains("下個週末") || text.contains("下周末")) {
            int daysUntilSat = (DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            return today.plusDays(daysUntilSat + 7);
        }

        // 星期X / 週X / 禮拜X
        return parseWeekday(text, today);
    }

    /**
     * 解析星期幾
     */
    private static LocalDate parseWeekday(String text, LocalDate today) {
        String[] weekdayPatterns = {
            "週一|周一|星期一|禮拜一",
            "週二|周二|星期二|禮拜二",
            "週三|周三|星期三|禮拜三",
            "週四|周四|星期四|禮拜四",
            "週五|周五|星期五|禮拜五",
            "週六|周六|星期六|禮拜六",
            "週日|周日|星期日|星期天|禮拜日|禮拜天"
        };

        for (int i = 0; i < weekdayPatterns.length; i++) {
            Pattern p = Pattern.compile("(這|本|下)?(" + weekdayPatterns[i] + ")");
            Matcher m = p.matcher(text);
            if (m.find()) {
                int targetDayOfWeek = (i == 6) ? 7 : i + 1;
                String prefix = m.group(1);

                int daysToAdd = targetDayOfWeek - today.getDayOfWeek().getValue();
                if (daysToAdd <= 0) daysToAdd += 7;
                if ("下".equals(prefix)) daysToAdd += 7;

                return today.plusDays(daysToAdd);
            }
        }
        return null;
    }

    /**
     * 解析民國年完整格式（111年10月2日）
     */
    private static List<LocalDate> parseRocFullDates(String text, int currentYear) {
        List<LocalDate> dates = new ArrayList<>();
        Pattern p = Pattern.compile("(1[0-9][0-9])年(?:(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日?)?");
        Matcher m = p.matcher(text);

        while (m.find()) {
            int rocYear = Integer.parseInt(m.group(1));
            int adYear = rocYear + 1911;

            if (m.group(2) != null && m.group(3) != null) {
                try {
                    int month = Integer.parseInt(m.group(2));
                    int day = Integer.parseInt(m.group(3));
                    dates.add(LocalDate.of(adYear, month, day));
                } catch (Exception ignored) {}
            } else {
                dates.add(LocalDate.of(adYear, 12, 31));
            }
        }
        return dates;
    }

    /**
     * 解析西元完整日期 (yyyy/MM/dd, yyyy-MM-dd, yyyy.MM.dd)
     */
    private static List<LocalDate> parseFullDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Pattern p = Pattern.compile("(20[2-9]\\d)[/.\\-年](0?[1-9]|1[0-2])[/.\\-月](0?[1-9]|[12]\\d|3[01])");
        Matcher m = p.matcher(text);

        while (m.find()) {
            try {
                int year = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int day = Integer.parseInt(m.group(3));
                dates.add(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }
        return dates;
    }

    /**
     * 解析年份...活動...月日格式（如「2025台北花伴野餐3月15日」）
     */
    private static List<LocalDate> parseYearEventDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Pattern yearPattern = Pattern.compile("(20[2-9]\\d)(?:年|\\s|#)*(?:台|活動|花|聖誕|跨年|春節|演唱|展覽|市集|節|祭)");
        Matcher yearMatcher = yearPattern.matcher(text);

        while (yearMatcher.find()) {
            int foundYear = Integer.parseInt(yearMatcher.group(1));
            String afterYear = text.substring(yearMatcher.end());
            Pattern monthDayPattern = Pattern.compile("^.{0,30}?(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日?");
            Matcher mdMatcher = monthDayPattern.matcher(afterYear);

            if (mdMatcher.find()) {
                try {
                    int month = Integer.parseInt(mdMatcher.group(1));
                    int day = Integer.parseInt(mdMatcher.group(2));
                    dates.add(LocalDate.of(foundYear, month, day));
                } catch (Exception ignored) {}
            }
        }
        return dates;
    }

    /**
     * 解析張貼日期/更新日期（用於過期判斷）
     */
    private static LocalDate parsePostDate(String text, int currentYear) {
        Pattern p = Pattern.compile("(?:張貼日期|發布日期|更新日期|發佈)[：:]?\\s*(20[1-2]\\d)[/.\\-](0?[1-9]|1[0-2])[/.\\-](0?[1-9]|[12]\\d|3[01])");
        Matcher m = p.matcher(text);

        if (m.find()) {
            int postYear = Integer.parseInt(m.group(1));
            if (postYear < currentYear - 1) {
                try {
                    int month = Integer.parseInt(m.group(2));
                    int day = Integer.parseInt(m.group(3));
                    return LocalDate.of(postYear, month, day);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * 解析無年份格式 (M月d日, M/d)
     */
    private static List<LocalDate> parseNoYearDates(String text, LocalDate today, Integer contextYear) {
        List<LocalDate> dates = new ArrayList<>();
        int currentYear = today.getYear();
        Pattern p = Pattern.compile("(?<!\\d)(0?[1-9]|1[0-2])[/月](0?[1-9]|[12]\\d|3[01])(?:日|\\s|\\)|$)");
        Matcher m = p.matcher(text);

        while (m.find()) {
            try {
                int month = Integer.parseInt(m.group(1));
                int day = Integer.parseInt(m.group(2));

                // 使用 contextYear 或 currentYear
                int guessYear = (contextYear != null) ? contextYear : currentYear;

                // 嘗試找周邊年份提示
                Pattern eventYear = Pattern.compile("(20[2-9]\\d)(?:年|\\s|#)*(?:活動|市集|展覽|演唱|音樂|聖誕|跨年|春節|節|祭)");
                Matcher eym = eventYear.matcher(text);
                if (eym.find()) {
                    guessYear = Integer.parseInt(eym.group(1));
                }

                dates.add(LocalDate.of(guessYear, month, day));
            } catch (Exception ignored) {}
        }
        return dates;
    }

    /**
     * 未來年份救援機制
     */
    private static LocalDate rescueFutureYear(String text, int currentYear) {
        Pattern p = Pattern.compile("(?<!\\d)(20[2-9]\\d)(?!\\d)");
        Matcher m = p.matcher(text);
        int maxFutureYear = -1;

        while (m.find()) {
            try {
                int year = Integer.parseInt(m.group(1));
                if (year > currentYear) {
                    maxFutureYear = Math.max(maxFutureYear, year);
                }
            } catch (Exception ignored) {}
        }

        if (maxFutureYear > currentYear) {
            return LocalDate.of(maxFutureYear, 1, 1);
        }
        return null;
    }
}