package app.bl.config;

import java.util.Set;

/**
 * FilterConfig - 過濾規則相關常數
 * 
 * 包含：
 * - 噪音關鍵字（學術論文、公文等）
 * - 海外關鍵字
 * - 排除網域
 * - 社群平台網域
 * - 申請類關鍵字
 * - 專有名詞
 */
public final class FilterConfig {

    private FilterConfig() {}

    // ==================== 噪音關鍵字 ====================
    public static final Set<String> NOISE_KEYWORDS = Set.of(
        // 學術論文
        "碩士論文", "博士論文", "學位論文", "研究生", "指導教授", "口試",
        "摘要", "Abstract", "參考文獻", "引用", "文獻探討", "研究方法",
        "研究動機", "研究目的", "關鍵詞", "Keywords", "DOI", "PDF下載",
        "期刊", "學報", "專題製作", "畢業製作", "成果報告书",
        // 公文與工具
        "決算書", "預算書", "財報", "報表", "公告", "標案", "決標", "開標", 
        "會議記錄", "公報",
        // 有線電視
        "有線電視", "第四台", "寬頻", "光纖", "維修", "客服", "安裝", "費率", "繳費",
        // 求職
        "徵才", "職缺", "招募", "求職", "人力銀行", "工讀", "實習",
        // 新聞（非活動）
        "新聞網", "日報", "快訊", "社論", "專欄"
    );

    // ==================== 海外關鍵字 ====================
    public static final Set<String> FOREIGN_KEYWORDS = Set.of(
        "東京", "大阪", "京都", "北海道", "首爾", "釜山", "曼谷", 
        "機票", "入境", "簽證"
    );

    // ==================== 排除網域 ====================
    public static final Set<String> EXCLUDED_DOMAINS = Set.of(
        // 社群（內容太碎）
        "x.com", "twitter.com",
        // 論壇
        "ptt.cc", "dcard.tw",
        // 日本購物
        "amazon.co.jp", "rakuten.co.jp", "yahoo.co.jp",
        // 訂房網站
        "booking.com", "agoda.com", "trivago.com", "hotels.com",
        // 學術
        "ndltd.ncl.edu.tw", "airitilibrary.com", "scholar.google.com.tw"
    );

    // ==================== 社群平台網域（使用 snippet 策略）====================
    public static final Set<String> SOCIAL_DOMAINS = Set.of(
        "instagram.com", "facebook.com", "threads.net", "fb.com"
    );

    // ==================== 申請類關鍵字 ====================
    public static final Set<String> APPLICATION_KEYWORDS = Set.of(
        "申請", "補助", "辦法", "要點", "徵選", "徵件",
        "招標", "採購", "規定", "須知", "下載", "表單"
    );

    // ==================== 專有名詞（加分用）====================
    public static final Set<String> PROPER_NOUNS = Set.of(
        // 場館
        "華山", "松菸", "駁二", "小巨蛋", "大巨蛋", "兩廳院", "故宮",
        "北美館", "衛武營", "高流", "北流", "科博館", "奇美博物館",
        // 知名活動
        "簡單生活節", "大港開唱", "春浪", "太魯閣峽谷音樂節",
        // 地區
        "信義區", "西門町", "草悟道", "勤美", "審計新村"
    );

    // ==================== 權威網域（政府官方）====================
    public static final Set<String> AUTHORITY_DOMAINS = Set.of(
        "travel.taipei",  // 台北旅遊網
        "gov.tw"          // 政府網站
    );

    // ==================== 工具方法 ====================

    /**
     * 檢查是否為噪音內容
     */
    public static boolean isNoise(String text) {
        if (text == null) return false;
        for (String keyword : NOISE_KEYWORDS) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 檢查是否為海外內容
     */
    public static boolean isForeign(String text) {
        if (text == null) return false;
        int count = 0;
        for (String keyword : FOREIGN_KEYWORDS) {
            if (text.contains(keyword)) count++;
        }
        return count >= 2;  // 至少兩個海外關鍵字才判定
    }

    /**
     * 檢查是否為排除網域
     */
    public static boolean isExcludedDomain(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : EXCLUDED_DOMAINS) {
            if (lower.contains(domain)) return true;
        }
        return false;
    }

    /**
     * 檢查是否為社群平台
     */
    public static boolean isSocialDomain(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        for (String social : SOCIAL_DOMAINS) {
            if (d.contains(social)) return true;
        }
        return false;
    }

    /**
     * 檢查是否為申請類內容
     */
    public static boolean isApplicationContent(String text) {
        if (text == null) return false;
        for (String keyword : APPLICATION_KEYWORDS) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 檢查是否包含專有名詞
     */
    public static boolean hasProperNoun(String text, String token) {
        for (String noun : PROPER_NOUNS) {
            if (token.contains(noun) || noun.contains(token)) return true;
        }
        return false;
    }

    /**
     * 檢查是否為權威網域
     */
    public static boolean isAuthorityDomain(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        for (String auth : AUTHORITY_DOMAINS) {
            if (d.contains(auth)) return true;
        }
        return false;
    }
}