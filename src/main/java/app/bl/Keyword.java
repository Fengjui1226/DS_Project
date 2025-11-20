package app.bl;
import java.util.*;

public class Keyword {
    private final String name;
    private final double baseWeight;
    
    // 預定的關鍵字權重
    private static final Map<String, Double> PREDEFINED_WEIGHTS = Map.ofEntries(
        Map.entry("活動", 6.0),
        Map.entry("展覽", 5.0),
        Map.entry("音樂", 4.0),
        Map.entry("演唱會", 4.0),
        Map.entry("市集", 3.0),
        Map.entry("體驗", 3.0),
        Map.entry("週末", 2.0),
        Map.entry("假日", 2.0),
        Map.entry("節慶", 3.0),
        Map.entry("festival", 4.0),
        Map.entry("concert", 4.0),
        Map.entry("exhibition", 5.0),
        Map.entry("event", 6.0),
        Map.entry("戶外", 3.0),
        Map.entry("免費", 2.0),
        Map.entry("親子", 3.0),
        Map.entry("藝術", 4.0),
        Map.entry("表演", 4.0)
    );
    
    // 構造方法
    public Keyword(String name, double baseWeight) { 
        this.name = name; 
        this.baseWeight = baseWeight; 
    }
    
    // 使用預定的權重建立 Keyword
    public static Keyword of(String name) {
        double weight = PREDEFINED_WEIGHTS.getOrDefault(name.toLowerCase(), 1.0);
        return new Keyword(name, weight);
    }
    
    // 取得預設權重
    public static double getPredefinedWeight(String name) {
        return PREDEFINED_WEIGHTS.getOrDefault(name.toLowerCase(), 1.0);
    }
    
    // 取得關鍵字名稱
    public String name() { return name; }
    
    // 取得關鍵字權重
    public double base() { return baseWeight; }

    // 自定義 equals 方法，忽略大小寫
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Keyword)) return false;
        Keyword k = (Keyword) o;
        return k.name.equalsIgnoreCase(name);
    }
    
    // 自定義 hashCode，忽略大小寫
    @Override 
    public int hashCode() { 
        return Objects.hash(name.toLowerCase()); 
    }
    
    // 重寫 toString 以便顯示關鍵字名稱和權重
    @Override 
    public String toString() { 
        return name + "(" + baseWeight + ")"; 
    }
}