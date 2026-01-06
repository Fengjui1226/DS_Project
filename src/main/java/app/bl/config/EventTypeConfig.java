package app.bl.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EventTypeConfig - 活動類型相關常數
 * 
 * 包含：
 * - 活動類型列表
 * - 類別擴展詞
 * - 活動類型加成
 * - 同義詞對照
 */
public final class EventTypeConfig {

    private EventTypeConfig() {}

    // ==================== 活動類型 ====================
    public static final Set<String> EVENT_TYPES = Set.of(
        // 音樂相關
        "演唱會", "音樂會", "音樂節",
        // 展覽相關
        "展覽", "特展",
        // 市集相關
        "市集", "夜市",
        // 藝文相關
        "講座", "工作坊", "舞台劇", "音樂劇", "脫口秀", "相聲", "魔術", "馬戲",
        // 活動相關
        "派對", "路跑", "馬拉松", "親子", "電影",
        // 特殊活動
        "快閃店", "見面會", "簽書會",
        // 新增類型
        "美食節", "啤酒節", "咖啡節", "運動會", "球賽", "電競",
        "寵物展", "狗聚", "DJ", "電音", "嘉年華", "燈會", "花火",
        "野餐", "露營", "健行", "瑜珈", "手作", "體驗"
    );

    // ==================== 活動類型關鍵詞 ====================
    public static final List<String> EVENT_TERMS = List.of(
        "活動", "展覽", "音樂", "演唱會", "市集", "節慶",
        "快閃店", "見面會", "簽書會", "發表會", "體驗會",
        "生活節", "音樂祭", "派對", "路跑", "馬拉松",
        "festival", "concert", "exhibition", "event", "pop-up"
    );

    // ==================== 活動類型加成權重 ====================
    public static final Map<String, Double> EVENT_TYPE_BOOST = Map.ofEntries(
        // 音樂類（高加成）
        Map.entry("演唱會", 1.4), Map.entry("音樂會", 1.3), Map.entry("音樂節", 1.4),
        // 展覽類
        Map.entry("展覽", 1.3), Map.entry("特展", 1.3), Map.entry("快閃店", 1.4),
        // 市集類
        Map.entry("市集", 1.3), Map.entry("生活節", 1.3),
        // 藝文類
        Map.entry("講座", 1.2), Map.entry("工作坊", 1.2),
        // 見面會類
        Map.entry("見面會", 1.3), Map.entry("簽書會", 1.2),
        // 親子/免費
        Map.entry("親子", 1.2), Map.entry("免費", 1.1),
        // 美食類
        Map.entry("美食節", 1.3), Map.entry("啤酒節", 1.3), Map.entry("咖啡節", 1.2),
        // 運動類
        Map.entry("電競", 1.3), Map.entry("球賽", 1.2), Map.entry("運動會", 1.2),
        // 寵物類
        Map.entry("寵物", 1.2), Map.entry("毛小孩", 1.2),
        // 夜生活類
        Map.entry("派對", 1.2), Map.entry("電音", 1.2), Map.entry("DJ", 1.2),
        // 節慶類
        Map.entry("燈會", 1.3), Map.entry("花火", 1.3), Map.entry("煙火", 1.3),
        // 戶外類
        Map.entry("野餐", 1.2), Map.entry("露營", 1.2), Map.entry("嘉年華", 1.3)
    );

    // ==================== 活動類型關鍵詞對照 ====================
    public static final Map<String, String> EVENT_TYPE_KEYWORDS = Map.ofEntries(
        Map.entry("演唱會", "演唱會"), Map.entry("concert", "演唱會"),
        Map.entry("音樂會", "音樂會"), Map.entry("音樂節", "音樂節"),
        Map.entry("展覽", "展覽"), Map.entry("exhibition", "展覽"),
        Map.entry("市集", "市集"), Map.entry("market", "市集"),
        Map.entry("講座", "講座"), Map.entry("工作坊", "工作坊"),
        Map.entry("路跑", "路跑"), Map.entry("馬拉松", "路跑"),
        Map.entry("親子", "親子活動"), Map.entry("體驗", "親子活動")
    );

    // ==================== 同義詞 ====================
    public static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
        Map.entry("演唱會", List.of("音樂會", "live", "演出")),
        Map.entry("展覽", List.of("特展", "展出", "展示")),
        Map.entry("市集", List.of("文創市集", "假日市集", "market")),
        Map.entry("音樂節", List.of("音樂祭", "festival")),
        Map.entry("週末", List.of("假日", "星期六", "星期日")),
        Map.entry("跨年", List.of("新年", "元旦"))
    );

    // ==================== 類別擴展 ====================
    public static final Map<String, List<String>> CATEGORY_EXPANSIONS = Map.ofEntries(
        // 市集類
        Map.entry("市集", List.of(
            "文創市集", "手作市集", "假日市集", "聖誕市集", "二手市集",
            "小農市集", "餐車市集", "風格市集", "主題市集", "復古市集",
            "蚤之市", "花市", "創意市集", "夜市", "market"
        )),
        // 展覽類
        Map.entry("展覽", List.of(
            "特展", "回顧展", "快閃店", "體驗展", "藝術展", "設計展",
            "攝影展", "插畫展", "動漫展", "博覽會", "畢業展", "個展",
            "聯展", "沉浸式展覽", "互動展", "光影展", "exhibition"
        )),
        // 音樂類
        Map.entry("音樂", List.of(
            "音樂節", "音樂祭", "live house", "聽團", "樂團演出",
            "獨立音樂", "爵士音樂節", "管弦樂", "售票演唱會", "免費演唱會",
            "音樂會", "DJ派對", "電音", "嘻哈", "饒舌", "music festival"
        )),
        // 演唱會類
        Map.entry("演唱會", List.of(
            "巡迴演唱會", "專場", "見面會", "粉絲見面會", "售票演唱會",
            "演唱會門票", "小型演出", "acoustic", "concert"
        )),
        // 親子類
        Map.entry("親子", List.of(
            "親子活動", "體驗營", "繪本", "說故事", "兒童劇", "DIY",
            "觀光工廠", "科普活動", "共融公園", "親子館", "夏令營", "冬令營",
            "兒童工作坊", "親子手作", "親子餐廳"
        )),
        // 戶外類
        Map.entry("戶外", List.of(
            "露營", "野餐", "登山", "健行", "路跑", "馬拉松", "單車",
            "自行車", "SUP", "衝浪", "生態導覽", "賞花", "賞螢", "賞楓",
            "獨木舟", "溯溪", "攀岩", "高空彈跳"
        )),
        // 藝文類
        Map.entry("藝文", List.of(
            "講座", "工作坊", "座談會", "分享會", "讀書會", "電影放映",
            "影展", "舞台劇", "舞蹈", "戲劇", "表演藝術", "脫口秀",
            "相聲", "音樂劇", "現代舞", "芭蕾"
        )),
        // 節慶類
        Map.entry("節慶", List.of(
            "跨年", "聖誕", "過年", "春節", "元宵", "燈會",
            "萬聖節", "情人節", "嘉年華", "祭典", "煙火", "花火節",
            "啤酒節", "週年慶"
        )),
        // 美食類
        Map.entry("美食", List.of(
            "美食節", "餐酒會", "品酒會", "咖啡節", "甜點節", "火鍋節",
            "啤酒節", "小吃節", "夜市美食", "美食展", "料理教室",
            "烘焙課", "品茗", "下午茶", "brunch"
        )),
        // 運動類
        Map.entry("運動", List.of(
            "籃球", "棒球", "足球", "電競", "賽事", "比賽", "球賽",
            "羽球", "網球", "桌球", "排球", "游泳", "拳擊", "格鬥",
            "健身", "瑜珈", "有氧", "重訓", "運動會"
        )),
        // 寵物類
        Map.entry("寵物", List.of(
            "毛小孩", "狗聚", "貓咪", "寵物友善", "寵物展", "認養",
            "寵物市集", "毛孩", "汪星人", "喵星人", "寵物野餐",
            "狗狗運動會", "寵物嘉年華"
        )),
        // 夜生活類
        Map.entry("夜生活", List.of(
            "夜店", "酒吧", "派對", "電音", "clubbing", "lounge",
            "DJ night", "ladies night", "主題派對", "泳池派對",
            "rooftop", "調酒"
        )),
        // 文青類
        Map.entry("文青", List.of(
            "獨立書店", "咖啡廳", "選物店", "vintage", "古著",
            "黑膠", "底片", "手沖咖啡", "文創園區", "藝術村",
            "老屋", "老宅", "文化祭"
        )),
        // 潮流類
        Map.entry("潮流", List.of(
            "潮牌", "球鞋", "sneaker", "街頭", "塗鴉", "滑板",
            "嘻哈", "饒舌", "街舞", "breaking", "潮流市集"
        ))
    );

    // ==================== 工具方法 ====================

    /**
     * 取得活動類型的加成權重
     */
    public static double getBoost(String eventType) {
        return EVENT_TYPE_BOOST.getOrDefault(eventType, 1.0);
    }

    /**
     * 檢查是否為活動類型關鍵字
     */
    public static boolean isEventTerm(String term) {
        return EVENT_TERMS.stream()
            .anyMatch(t -> t.equalsIgnoreCase(term));
    }

    /**
     * 取得類別的擴展詞
     */
    public static List<String> getExpansions(String category) {
        return CATEGORY_EXPANSIONS.getOrDefault(category, List.of());
    }
}