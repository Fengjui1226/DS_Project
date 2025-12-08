package app.bl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EventInfoExtractor v1.0 - 結構化活動資訊提取
 * 
 * 功能：
 * 1. 提取活動日期（開始/結束）
 * 2. 提取活動時間
 * 3. 提取地點/場館
 * 4. 提取票價資訊
 * 5. 提取主辦單位
 * 6. 計算資訊完整度分數
 */
public class EventInfoExtractor {

    // ================= 資料結構 =================

    public static class EventInfo {
        public LocalDate startDate;
        public LocalDate endDate;
        public LocalTime startTime;
        public LocalTime endTime;
        public String venue;           // 場館名稱
        public String address;         // 完整地址
        public String city;            // 城市
        public Integer priceMin;       // 最低票價
        public Integer priceMax;       // 最高票價
        public boolean isFree;         // 是否免費
        public String organizer;       // 主辦單位
        public String eventType;       // 活動類型
        public double completenessScore; // 資訊完整度 0-100

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

    // ================= 場館資料庫 =================

    private static final Map<String, String> VENUE_CITY_MAP = Map.ofEntries(
        // 台北
        Map.entry("華山1914", "台北"), Map.entry("華山文創", "台北"), Map.entry("華山", "台北"),
        Map.entry("松山文創", "台北"), Map.entry("松菸", "台北"),
        Map.entry("台北小巨蛋", "台北"), Map.entry("小巨蛋", "台北"),
        Map.entry("台北大巨蛋", "台北"), Map.entry("大巨蛋", "台北"),
        Map.entry("國家音樂廳", "台北"), Map.entry("兩廳院", "台北"), Map.entry("國家戲劇院", "台北"),
        Map.entry("台北流行音樂中心", "台北"), Map.entry("北流", "台北"),
        Map.entry("台北國際會議中心", "台北"), Map.entry("TICC", "台北"),
        Map.entry("南港展覽館", "台北"), Map.entry("世貿", "台北"),
        Map.entry("北美館", "台北"), Map.entry("台北市立美術館", "台北"),
        Map.entry("當代藝術館", "台北"), Map.entry("故宮", "台北"),
        Map.entry("中山堂", "台北"), Map.entry("西門紅樓", "台北"),
        Map.entry("誠品表演廳", "台北"), Map.entry("誠品信義", "台北"),
        Map.entry("Legacy", "台北"), Map.entry("The Wall", "台北"),
        Map.entry("Zepp New Taipei", "新北"),
        
        // 台中
        Map.entry("台中國家歌劇院", "台中"), Map.entry("歌劇院", "台中"),
        Map.entry("台中巨蛋", "台中"),
        Map.entry("科博館", "台中"), Map.entry("國立自然科學博物館", "台中"),
        Map.entry("草悟道", "台中"), Map.entry("勤美術館", "台中"),
        
        // 高雄
        Map.entry("駁二藝術特區", "高雄"), Map.entry("駁二", "高雄"),
        Map.entry("高雄流行音樂中心", "高雄"), Map.entry("高流", "高雄"),
        Map.entry("高雄巨蛋", "高雄"), Map.entry("高雄展覽館", "高雄"),
        Map.entry("衛武營", "高雄"), Map.entry("衛武營國家藝術文化中心", "高雄"),
        Map.entry("科工館", "高雄"),
        
        // 台南
        Map.entry("台南文化中心", "台南"), Map.entry("台南美術館", "台南"),
        Map.entry("藍晒圖", "台南"), Map.entry("十鼓文創", "台南"),
        
        // 其他
        Map.entry("海生館", "屏東"), Map.entry("國立海洋生物博物館", "屏東"),
        Map.entry("花蓮文創", "花蓮"),
        Map.entry("宜蘭傳藝", "宜蘭"), Map.entry("傳藝中心", "宜蘭")
    );

    // ================= 正則表達式 =================

    // 日期模式
    private static final List<Pattern> DATE_PATTERNS = List.of(
        // 完整日期範圍
        Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})\\s*[~至到-]\\s*(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})"),
        Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})\\s*[~至到-]\\s*(\\d{1,2})[/.-](\\d{1,2})"),
        // 民國年
        Pattern.compile("(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?\\s*[~至到-]\\s*(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?"),
        Pattern.compile("(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?\\s*[~至到-]\\s*(\\d{1,2})月(\\d{1,2})日?"),
        Pattern.compile("(\\d{2,3})年(\\d{1,2})月(\\d{1,2})日?"),
        // 單一日期
        Pattern.compile("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})"),
        // 中文日期
        Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日"),
        Pattern.compile("(\\d{1,2})月(\\d{1,2})日")
    );

    // 時間模式
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(\\d{1,2})[:：](\\d{2})\\s*[~至到-]?\\s*(?:(\\d{1,2})[:：](\\d{2}))?"
    );

    private static final Pattern TIME_PERIOD_PATTERN = Pattern.compile(
        "([上下]午)?\\s*(\\d{1,2})[:：](\\d{2})"
    );

    // 票價模式
    private static final List<Pattern> PRICE_PATTERNS = List.of(
        Pattern.compile("NT\\$?\\s*(\\d{1,5})\\s*[~至到-]\\s*NT?\\$?\\s*(\\d{1,5})"),
        Pattern.compile("票價[：:]?\\s*(\\d{1,5})\\s*[~至到/-]\\s*(\\d{1,5})"),
        Pattern.compile("票價[：:]?\\s*NT?\\$?\\s*(\\d{1,5})"),
        Pattern.compile("(\\d{3,5})\\s*元"),
        Pattern.compile("\\$(\\d{3,5})")
    );

    // 免費模式
    private static final Pattern FREE_PATTERN = Pattern.compile(
        "免費|免票|free|Free|FREE|入場免費|免費入場|免費參觀|自由入場"
    );

    // 主辦單位模式
    private static final List<Pattern> ORGANIZER_PATTERNS = List.of(
        Pattern.compile("主辦[單位方]?[：:]\\s*(.{2,30})"),
        Pattern.compile("主辦[：:]\\s*(.{2,30})"),
        Pattern.compile("指導單位[：:]\\s*(.{2,30})"),
        Pattern.compile("協辦[：:]\\s*(.{2,30})")
    );

    // 活動類型關鍵字
    private static final Map<String, String> EVENT_TYPE_KEYWORDS = Map.ofEntries(
        Map.entry("演唱會", "演唱會"), Map.entry("concert", "演唱會"),
        Map.entry("音樂會", "音樂會"), Map.entry("音樂節", "音樂節"),
        Map.entry("展覽", "展覽"), Map.entry("特展", "展覽"), Map.entry("exhibition", "展覽"),
        Map.entry("市集", "市集"), Map.entry("market", "市集"),
        Map.entry("講座", "講座"), Map.entry("工作坊", "工作坊"), Map.entry("workshop", "工作坊"),
        Map.entry("路跑", "路跑"), Map.entry("馬拉松", "路跑"),
        Map.entry("派對", "派對"), Map.entry("party", "派對"),
        Map.entry("舞台劇", "戲劇"), Map.entry("話劇", "戲劇"), Map.entry("音樂劇", "戲劇"),
        Map.entry("電影", "電影"), Map.entry("影展", "電影"),
        Map.entry("親子", "親子活動")
    );

    // ================= 主要 API =================

    /**
     * 從文字中提取活動資訊
     */
    public static EventInfo extract(String text) {
        if (text == null || text.isEmpty()) {
            return new EventInfo();
        }

        EventInfo info = new EventInfo();

        // 1. 提取日期
        extractDates(text, info);

        // 2. 提取時間
        extractTimes(text, info);

        // 3. 提取場館和地點
        extractVenue(text, info);

        // 4. 提取票價
        extractPrice(text, info);

        // 5. 提取主辦單位
        extractOrganizer(text, info);

        // 6. 判斷活動類型
        extractEventType(text, info);

        // 7. 計算完整度
        info.completenessScore = calculateCompleteness(info);

        return info;
    }

    /**
     * 從 PageNode 提取活動資訊並更新
     */
    public static EventInfo extractAndUpdate(PageNode page) {
        if (page == null) return new EventInfo();

        String content = (page.getTitle() != null ? page.getTitle() + " " : "") +
                         (page.getTextContent() != null ? page.getTextContent() : "");

        EventInfo info = extract(content);

        // 更新 PageNode
        if (info.startDate != null && page.getEventDate() == null) {
            page.setEventDate(info.startDate);
        }
        if (info.city != null && (page.getCity() == null || "全台".equals(page.getCity()))) {
            page.setCity(info.city);
        }

        return info;
    }

    /**
     * 為 PageNode 列表計算資訊完整度加分
     */
    public static void applyCompletenessBonus(List<PageNode> pages) {
        if (pages == null || pages.isEmpty()) return;

        System.out.println("\n📋 結構化資訊提取中...");
        int extracted = 0;

        for (PageNode page : pages) {
            EventInfo info = extractAndUpdate(page);

            // 根據完整度加分（最高 20 分）
            double bonus = info.completenessScore * 0.2;
            page.addScore(bonus);

            // 有票價資訊額外加分（實用性高）
            if (info.priceMin != null || info.isFree) {
                page.addScore(5);
            }

            // 有明確場館加分
            if (info.venue != null) {
                page.addScore(3);
            }

            if (info.completenessScore > 30) {
                extracted++;
            }
        }

        System.out.println("✅ 結構化提取完成，" + extracted + "/" + pages.size() + " 個結果有詳細資訊");
    }

    // ================= 提取方法 =================

    private static void extractDates(String text, EventInfo info) {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    int groups = matcher.groupCount();

                    if (groups >= 6 && matcher.group(4) != null) {
                        // 日期範圍
                        int y1 = parseYear(matcher.group(1), currentYear);
                        int m1 = Integer.parseInt(matcher.group(2));
                        int d1 = Integer.parseInt(matcher.group(3));
                        info.startDate = LocalDate.of(y1, m1, d1);

                        int y2 = parseYear(matcher.group(4), currentYear);
                        int m2 = Integer.parseInt(matcher.group(5));
                        int d2 = Integer.parseInt(matcher.group(6));
                        info.endDate = LocalDate.of(y2, m2, d2);
                        return;

                    } else if (groups >= 3) {
                        // 單一日期
                        int year = currentYear;
                        int month, day;

                        if (matcher.group(1).length() <= 2) {
                            // 只有月日
                            month = Integer.parseInt(matcher.group(1));
                            day = Integer.parseInt(matcher.group(2));
                            // 智慧判斷年份
                            LocalDate candidate = LocalDate.of(currentYear, month, day);
                            if (candidate.isBefore(today.minusMonths(2))) {
                                year = currentYear + 1;
                            }
                        } else {
                            year = parseYear(matcher.group(1), currentYear);
                            month = Integer.parseInt(matcher.group(2));
                            day = Integer.parseInt(matcher.group(3));
                        }

                        info.startDate = LocalDate.of(year, month, day);
                        return;
                    }
                } catch (Exception e) {
                    // 解析失敗，繼續嘗試下一個模式
                }
            }
        }
    }

    private static int parseYear(String yearStr, int currentYear) {
        int year = Integer.parseInt(yearStr);
        if (year < 200) {
            // 民國年轉換
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
        // 先找已知場館
        for (Map.Entry<String, String> entry : VENUE_CITY_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                info.venue = entry.getKey();
                info.city = entry.getValue();
                break;
            }
        }

        // 嘗試提取地址
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
        // 先檢查是否免費
        if (FREE_PATTERN.matcher(text).find()) {
            info.isFree = true;
            info.priceMin = 0;
            info.priceMax = 0;
            return;
        }

        // 提取票價
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
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private static void extractOrganizer(String text, EventInfo info) {
        for (Pattern pattern : ORGANIZER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String org = matcher.group(1).trim();
                // 清理結尾
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

    /**
     * 計算資訊完整度（0-100）
     */
    private static double calculateCompleteness(EventInfo info) {
        double score = 0;

        // 日期（30%）
        if (info.startDate != null) {
            score += 20;
            if (info.endDate != null) score += 10;
        }

        // 時間（10%）
        if (info.startTime != null) score += 10;

        // 地點（25%）
        if (info.venue != null) score += 15;
        if (info.city != null) score += 10;

        // 票價（20%）
        if (info.isFree || info.priceMin != null) score += 20;

        // 主辦（10%）
        if (info.organizer != null) score += 10;

        // 類型（5%）
        if (info.eventType != null) score += 5;

        return score;
    }

    // ================= 工具方法 =================

    /**
     * 取得場館對應的城市
     */
    public static String getCityByVenue(String venue) {
        if (venue == null) return null;
        for (Map.Entry<String, String> entry : VENUE_CITY_MAP.entrySet()) {
            if (venue.contains(entry.getKey()) || entry.getKey().contains(venue)) {
                return entry.getValue();
            }
        }
        return null;
    }
}