package app.bl;

/**
 * TextUtils - 共用文字處理工具
 * 
 * 解決重複程式碼問題：
 * - truncate() 原本在 SearchEngine 和 RankCalculator 各有一份
 * - 其他常用文字處理方法
 * 
 * 重構手法：Extract Method -> Move to Utility Class
 */
public final class TextUtils {

    private TextUtils() {}

    /**
     * 截斷文字到指定長度
     * @param text 原始文字
     * @param maxLength 最大長度
     * @return 截斷後的文字（超過會加 ...）
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength 
            ? text.substring(0, maxLength) + "..." 
            : text;
    }

    /**
     * 安全地取得小寫文字
     */
    public static String toLowerCase(String text) {
        return text == null ? "" : text.toLowerCase();
    }

    /**
     * 檢查文字是否為空
     */
    public static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 檢查文字是否不為空
     */
    public static boolean isNotEmpty(String text) {
        return !isEmpty(text);
    }

    /**
     * 從 URL 提取網域
     */
    public static String extractDomain(String url) {
        if (url == null) return "";
        try {
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 從 URL 提取標題（最後一段路徑）
     */
    public static String extractTitleFromUrl(String url) {
        if (url == null) return "";
        try {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < url.length() - 1) {
                String segment = url.substring(lastSlash + 1);
                return segment.replaceAll("[-_]", " ");
            }
        } catch (Exception e) {}
        return "";
    }

    /**
     * 清理 HTML 實體編碼
     */
    public static String cleanHtmlEntities(String text) {
        if (text == null) return "";
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ");
    }

    /**
     * 移除多餘空白
     */
    public static String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 合併多個文字（用空格分隔，自動過濾 null）
     */
    public static String combine(String... texts) {
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            if (isNotEmpty(text)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(text.trim());
            }
        }
        return sb.toString();
    }
}