package app.web.handlers;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import app.web.HttpUtil;
import app.web.ServerContext;

/**
 * CategoriesHandler 負責提供活動分類清單 (/api/categories).
 * 回傳系統支援的活動類別列表。
 */
public class CategoriesHandler implements HttpHandler {

    private final ServerContext ctx;

    public CategoriesHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 處理預檢請求與 CORS
        if (HttpUtil.handleOptions(ex, ctx)) {
            return;
        }
        // 構建包含活動分類的回應 JSON
        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        JsonArray arr = new JsonArray();

        // 添加分類，每個分類包含 id, name, query, icon, color
        arr.add(createCategory("market", "市集", "市集", "🛍️", "#FF6B6B"));
        arr.add(createCategory("art", "展覽", "展覽", "🎨", "#4ECDC4"));
        arr.add(createCategory("concert", "演唱會", "演唱會", "🎤", "#95E1D3"));
        arr.add(createCategory("festival", "音樂節", "音樂節", "🎵", "#F38181"));
        arr.add(createCategory("lecture", "講座", "講座", "📚", "#AA96DA"));
        arr.add(createCategory("workshop", "工作坊", "工作坊", "🛠️", "#FCBAD3"));
        arr.add(createCategory("family", "親子", "親子", "👨‍👩‍👧‍👦", "#FFFFD2"));
        arr.add(createCategory("outdoor", "戶外", "戶外", "🏕️", "#A8D8EA"));
        arr.add(createCategory("food", "美食", "美食", "🍜", "#FFD93D"));
        arr.add(createCategory("sports", "運動", "運動", "⚽", "#6BCB77"));
        arr.add(createCategory("pet", "寵物", "寵物", "🐾", "#FDA7DF"));
        arr.add(createCategory("celebration", "節慶", "節慶", "🎊", "#E4A5FF"));

        root.add("categories", arr);
        HttpUtil.sendJson(ex, ctx, 200, root.toString());
    }

    private JsonObject createCategory(String id, String name, String query, String icon, String color) {
        JsonObject cat = new JsonObject();
        cat.addProperty("id", id);
        cat.addProperty("name", name);
        cat.addProperty("query", query);
        cat.addProperty("icon", icon);
        cat.addProperty("color", color);
        return cat;
    }
}
