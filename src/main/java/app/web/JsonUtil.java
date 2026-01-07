package app.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON 資料處理相關的工具方法類別
 */
public class JsonUtil {

    private JsonUtil() {}

    /**
     * 標記快取命中：將 JSON 字串中的 "cached" 欄位設為 true。
     * 若解析 JSON 發生問題，退而使用字串替換方式處理。
     */
    public static String markCachedTrue(String cachedJson) {
        try {
            JsonObject obj = JsonParser.parseString(cachedJson).getAsJsonObject();
            obj.addProperty("cached", true);
            return obj.toString();
        } catch (Exception e) {
            // 保底方案：簡單字串替換（可能不完全可靠，但可避免程式崩潰）
            return cachedJson.replace("\"cached\":false", "\"cached\":true");
        }
    }

    /**
     * 對字串做空值安全處理：若為 null 則回傳空字串，否則回傳原字串。
     */
    public static String safe(String s) {
        return (s == null ? "" : s);
    }

    /**
     * 將數值四捨五入至小數點後 1 位。
     */
    public static double round1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }
}
