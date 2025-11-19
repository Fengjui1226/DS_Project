package app.da;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/**
 * HTMLHandler - 擷取活動網站資料
 */
public class HTMLHandler {
    
    private static final List<Pattern> DATE_PATTERNS = List.of(
        Pattern.compile("(\\d{4})[/\\-\\.](\\d{1,2})[/\\-\\.](\\d{1,2})"),
        Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})(?![/\\-\\d])"),
        Pattern.compile("(\\d{1,2})月(\\d{1,2})日")
    );
    
    private static final List<String> CITIES = List.of(
        "台北", "臺北", "新北", "桃園", "台中", "臺中", "台南", "臺南",
        "高雄", "基隆", "新竹", "苗栗", "彰化", "南投", "雲林", "嘉義",
        "屏東", "宜蘭", "花蓮", "台東", "臺東"
    );
    
    /**
     * 解析 HTML 內容
     */
    public static Map<String, String> parseHTML(String html) {
        Map<String, String> info = new HashMap<>();
        
        if (html == null || html.isEmpty()) {
            return info;
        }
        
        String title = extractTitle(html);
        if (title != null) {
            info.put("title", title);
        }
        
        LocalDate date = extractDate(html);
        if (date != null) {
            info.put("date", date.toString());
        }
        
        String city = extractCity(html);
        if (city != null) {
            info.put("city", city);
        }
        
        return info;
    }
    
    /**
     * 提取標題
     */
    public static String extractTitle(String html) {
        Pattern titlePattern = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
        Matcher m = titlePattern.matcher(html);
        if (m.find()) {
            return cleanText(m.group(1));
        }
        return null;
    }
    
    /**
     * 提取日期
     */
    public static LocalDate extractDate(String html) {
        String text = html.replaceAll("<[^>]+>", " ");
        
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                try {
                    int year = LocalDate.now().getYear();
                    int month, day;
                    
                    if (m.groupCount() >= 3 && m.group(1).length() == 4) {
                        year = Integer.parseInt(m.group(1));
                        month = Integer.parseInt(m.group(2));
                        day = Integer.parseInt(m.group(3));
                    } else if (m.groupCount() >= 2) {
                        month = Integer.parseInt(m.group(1));
                        day = Integer.parseInt(m.group(2));
                    } else {
                        continue;
                    }
                    
                    if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                        return LocalDate.of(year, month, day);
                    }
                } catch (Exception e) {
                    // 繼續下一個 pattern
                }
            }
        }
        return null;
    }
    
    /**
     * 提取城市
     */
    public static String extractCity(String html) {
        String text = html.replaceAll("<[^>]+>", " ").toLowerCase(Locale.ROOT);
        
        for (String city : CITIES) {
            if (text.contains(city.toLowerCase())) {
                return LocationRecognizer.normalize(city);
            }
        }
        return null;
    }
    
    /**
     * 提取關鍵字
     */
    public static List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        if (text == null) return keywords;
        
        String lower = text.toLowerCase(Locale.ROOT);
        
        List<String> eventKeywords = List.of(
            "活動", "展覽", "音樂", "演唱會", "市集", "節慶", "表演",
            "藝術", "戶外", "親子", "免費", "週末", "假日", "體驗"
        );
        
        for (String kw : eventKeywords) {
            if (lower.contains(kw)) {
                keywords.add(kw);
            }
        }
        return keywords;
    }
    
    private static String cleanText(String text) {
        if (text == null) return null;
        return text.replaceAll("\\s+", " ").trim();
    }
}