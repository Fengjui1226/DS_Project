package app.bl;

import java.util.*;

public class UserProfile {
    // 關鍵字偏好次數
    private final Map<String, Integer> pref = new HashMap<>();

    // 使用者城市（給地點加成用）
    private String userCity = "台北";
    public void setUserCity(String city) { this.userCity = city; }
    public String getUserCity() { return userCity; }

    // 近期行為（常搜/常點）
    private final Map<String, Integer> habit = new HashMap<>();
    public void bumpHabit(String keyword) {
        habit.put(keyword, habit.getOrDefault(keyword, 0) + 1);
    }
    public int habitCount(String keyword) {
        return habit.getOrDefault(keyword, 0);
    }

    // 累積偏好
    public void bump(String keyword) {
        pref.put(keyword, pref.getOrDefault(keyword, 0) + 1);
    }
    public int getPrefCount(String keyword) {
        return pref.getOrDefault(keyword, 0);
    }

    // RankCalculator 用：依偏好調整權重
    public double adjustedWeight(Keyword k, double alpha) {
        int count = getPrefCount(k.name());
        // 你的 Keyword 有 base()，沒有 weight()，所以改用 base()
        return k.base() * (1.0 + alpha * Math.min(count, 5));
    }
}