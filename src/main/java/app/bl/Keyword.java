package app.bl;
import java.util.*;

public class Keyword {
    private final String name;
    private final double baseWeight;
    
    // Predefined keyword weights as per proposal (Search Tricks #1)
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
    
    public Keyword(String name, double baseWeight) { 
        this.name = name; 
        this.baseWeight = baseWeight; 
    }
    
    // Factory method using predefined weights
    public static Keyword of(String name) {
        double weight = PREDEFINED_WEIGHTS.getOrDefault(name.toLowerCase(), 1.0);
        return new Keyword(name, weight);
    }
    
    public static double getPredefinedWeight(String name) {
        return PREDEFINED_WEIGHTS.getOrDefault(name.toLowerCase(), 1.0);
    }
    
    public String name() { return name; }
    public double base() { return baseWeight; }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Keyword)) return false;
        Keyword k = (Keyword) o;
        return k.name.equalsIgnoreCase(name);
    }
    
    @Override 
    public int hashCode() { return Objects.hash(name.toLowerCase()); }
    
    @Override 
    public String toString() { return name + "(" + baseWeight + ")"; }
}