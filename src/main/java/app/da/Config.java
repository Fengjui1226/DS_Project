package app.da;

import java.io.InputStream;
import java.util.Properties;

/**
 * 載入 application.properties（若存在），並允許用環境變數覆寫。
 *
 * 用法：
 *   String v = Config.get("google.cse.apiKey", null);
 *   boolean enabled = Config.getBoolean("google.cse.enabled", true);
 */
public final class Config {
    private static final Properties P = new Properties();

    static {
        try (InputStream in = Config.class.getClassLoader()
                                         .getResourceAsStream("application.properties")) {
            if (in != null) {
                P.load(in);
            }
        } catch (Exception e) {
            // 不要拋出例外，若沒有 properties 則使用預設值或環境變數
        }
    }

    private Config() { /* no instances */ }

    /**
     * 取得字串設定：優先使用 environment variable（KEY 會把 '.' 換成 '_' 並 uppercase），
     * 若無再使用 application.properties，兩者都沒有則回傳 def。
     */
    public static String get(String key, String def) {
        if (key == null) return def;

        // 將駝峰命名轉換為環境變數格式
        // 例如：google.cse.apiKey → GOOGLE_CSE_API_KEY
        String envKey = camelToEnvVar(key);
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;

        String p = P.getProperty(key);
        if (p != null && !p.isBlank()) return p;
        return def;
    }

    /**
     * 將 camelCase 轉換為 SCREAMING_SNAKE_CASE
     * 例如：google.cse.apiKey → GOOGLE_CSE_API_KEY
     */
    private static String camelToEnvVar(String key) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '.') {
                result.append('_');
            } else if (Character.isUpperCase(c)) {
                // 在大寫字母前加下劃線（除非是第一個字符）
                if (i > 0 && result.charAt(result.length() - 1) != '_') {
                    result.append('_');
                }
                result.append(c);
            } else {
                result.append(Character.toUpperCase(c));
            }
        }
        return result.toString();
    }

    /** 方便取得 boolean */
    public static boolean getBoolean(String key, boolean def) {
        String v = get(key, null);
        if (v == null) return def;
        v = v.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y");
    }

    /** 方便取得 int（失敗則回傳 def） */
    public static int getInt(String key, int def) {
        String v = get(key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}