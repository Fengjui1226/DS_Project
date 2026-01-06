package app.bl;

import java.util.regex.Pattern;

/**
 * InputValidator - 輸入驗證與安全處理
 * 
 * 功能：
 * - XSS 防護
 * - SQL 注入防護（雖然沒用 DB，但預防萬一）
 * - 查詢長度限制
 * - 特殊字元過濾
 */
public final class InputValidator {

    private InputValidator() {}

    // 查詢限制
    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 100;
    public static final int MAX_CITY_LENGTH = 20;

    // 危險字元 Pattern
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "<script|</script|javascript:|on\\w+\\s*=|<iframe|<object|<embed",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('|--|;|/\\*|\\*/|xp_|exec|execute|insert|select|delete|update|drop|union|into|load_file|outfile)",
        Pattern.CASE_INSENSITIVE
    );

    // 允許的查詢字元（中文、英文、數字、空格、常用標點）
    private static final Pattern ALLOWED_QUERY_CHARS = Pattern.compile(
        "^[\\u4e00-\\u9fff\\u3400-\\u4dbfa-zA-Z0-9\\s\\-_.，。、！？]+$"
    );

    // ==================== 驗證結果 ====================

    public static class ValidationResult {
        public final boolean valid;
        public final String message;
        public final String sanitizedValue;

        private ValidationResult(boolean valid, String message, String sanitizedValue) {
            this.valid = valid;
            this.message = message;
            this.sanitizedValue = sanitizedValue;
        }

        public static ValidationResult success(String sanitizedValue) {
            return new ValidationResult(true, null, sanitizedValue);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message, null);
        }
    }

    // ==================== 主要 API ====================

    /**
     * 驗證搜尋查詢
     */
    public static ValidationResult validateQuery(String query) {
        // Null 檢查
        if (query == null) {
            return ValidationResult.fail("查詢不能為空");
        }

        // 清理空白
        String trimmed = query.trim();

        // 長度檢查
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return ValidationResult.fail("請輸入至少 " + MIN_QUERY_LENGTH + " 個字的關鍵字");
        }

        if (trimmed.length() > MAX_QUERY_LENGTH) {
            return ValidationResult.fail("查詢長度不能超過 " + MAX_QUERY_LENGTH + " 個字");
        }

        // XSS 檢查
        if (XSS_PATTERN.matcher(trimmed).find()) {
            Logger.warn("偵測到可疑 XSS 輸入: %s", maskSensitive(trimmed));
            return ValidationResult.fail("查詢包含不允許的字元");
        }

        // SQL 注入檢查
        if (SQL_INJECTION_PATTERN.matcher(trimmed).find()) {
            Logger.warn("偵測到可疑 SQL 注入: %s", maskSensitive(trimmed));
            return ValidationResult.fail("查詢包含不允許的字元");
        }

        // 清理並回傳
        String sanitized = sanitize(trimmed);
        return ValidationResult.success(sanitized);
    }

    /**
     * 驗證城市參數
     */
    public static ValidationResult validateCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            return ValidationResult.success("台北"); // 預設值
        }

        String trimmed = city.trim();

        if (trimmed.length() > MAX_CITY_LENGTH) {
            return ValidationResult.fail("城市名稱過長");
        }

        // XSS 檢查
        if (XSS_PATTERN.matcher(trimmed).find()) {
            return ValidationResult.fail("城市名稱包含不允許的字元");
        }

        return ValidationResult.success(sanitize(trimmed));
    }

    /**
     * 驗證頁碼
     */
    public static int validatePage(String pageStr, int defaultValue) {
        if (pageStr == null || pageStr.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            int page = Integer.parseInt(pageStr.trim());
            return (page >= 1 && page <= 100) ? page : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================== 清理方法 ====================

    /**
     * 清理輸入字串
     */
    public static String sanitize(String input) {
        if (input == null) return "";

        return input
            // 移除控制字元
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
            // HTML 實體編碼
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            // 移除多餘空白
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * 反清理（用於顯示）- 還原 HTML 實體
     */
    public static String unsanitize(String input) {
        if (input == null) return "";

        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&amp;", "&");
    }

    /**
     * 遮蔽敏感資訊（用於 log）
     */
    private static String maskSensitive(String input) {
        if (input == null || input.length() <= 4) return "****";
        return input.substring(0, 2) + "****" + input.substring(input.length() - 2);
    }

    // ==================== JSON 安全處理 ====================

    /**
     * 安全地轉義 JSON 字串
     */
    public static String escapeJson(String s) {
        if (s == null) return "";

        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

}