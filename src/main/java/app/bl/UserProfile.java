package app.bl;

import java.util.*;
import java.time.LocalDateTime;

public class UserProfile {
    // 關鍵字偏好次數
    private final Map<String, Integer> pref = new HashMap<>();

    // 使用者城市（給地點加成用）
    private String userCity = "台北";
    public void setUserCity(String city) { this.userCity = city; }
    public String getUserCity() { return userCity; }

    // 使用者 GPS 座標（Haversine 距離排序用）
    private double userLat = Double.NaN;
    private double userLng = Double.NaN;
    public void setUserCoords(double lat, double lng) { this.userLat = lat; this.userLng = lng; }
    public double getUserLat() { return userLat; }
    public double getUserLng() { return userLng; }
    public boolean hasCoords() { return !Double.isNaN(userLat) && !Double.isNaN(userLng); }

    // 近期行為（常搜/常點）with timestamps
    private final Map<String, Integer> habit = new HashMap<>();
    private final Map<String, LocalDateTime> lastSearchTime = new HashMap<>();
    
    // Habit boost coefficient (as per proposal: habitBoost = 0.05 * count)
    private static final double HABIT_COEFFICIENT = 0.05;
    
    public void bumpHabit(String keyword) {
        String key = keyword.toLowerCase();
        habit.put(key, habit.getOrDefault(key, 0) + 1);
        lastSearchTime.put(key, LocalDateTime.now());
    }
    
    public int habitCount(String keyword) {
        return habit.getOrDefault(keyword.toLowerCase(), 0);
    }
    
    /**
     * Calculate habit boost based on recent search history
     * Formula: habitBoost = HABIT_COEFFICIENT * count
     */
    public double getHabitBoost(String keyword) {
        int count = habitCount(keyword);
        return HABIT_COEFFICIENT * count;
    }
    
    /**
     * Get total habit boost for a list of keywords
     */
    public double getTotalHabitBoost(List<String> keywords) {
        double total = 0.0;
        for (String kw : keywords) {
            total += getHabitBoost(kw);
        }
        return Math.min(total, 2.0); // Cap at 2.0
    }

    // 累積偏好
    public void bump(String keyword) {
        pref.put(keyword.toLowerCase(), pref.getOrDefault(keyword.toLowerCase(), 0) + 1);
    }
    
    public int getPrefCount(String keyword) {
        return pref.getOrDefault(keyword.toLowerCase(), 0);
    }

    // RankCalculator 用：依偏好調整權重
    public double adjustedWeight(Keyword k, double alpha) {
        int count = getPrefCount(k.name());
        return k.base() * (1.0 + alpha * Math.min(count, 10));
    }
    
    public Map<String, Integer> getHabitData() {
        return Collections.unmodifiableMap(habit);
    }
    
    public Map<String, Integer> getPrefData() {
        return Collections.unmodifiableMap(pref);
    }
    
    public void loadHabitData(Map<String, Integer> data) {
        habit.putAll(data);
    }
    
    public void loadPrefData(Map<String, Integer> data) {
        pref.putAll(data);
    }
}