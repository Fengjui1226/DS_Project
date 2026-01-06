package app.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtil {

    private JsonUtil() {}

    /**
     * 命中快取時，把 cached 改成 true（使用 Gson parse，避免脆弱 replace）
     */
    public static String markCachedTrue(String cachedJson) {
        try {
            JsonObject obj = JsonParser.parseString(cachedJson).getAsJsonObject();
            obj.addProperty("cached", true);
            return obj.toString();
        } catch (Exception e) {
            // fallback：保底用 replace（不完美，但至少不會崩）
            return cachedJson.replace("\"cached\":false", "\"cached\":true");
        }
    }
}
