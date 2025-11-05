package app.bl;
import java.util.*;

public class UserProfile {
    // 關鍵字名稱 -> 偏好次數
    private final Map<String,Integer> pref = new HashMap<>();
    public void bump(String keywordName){
        pref.put(keywordName, pref.getOrDefault(keywordName,0)+1);
    }
    // α 控制個人化強度
    public double adjustedWeight(Keyword k, double alpha){
        int c = pref.getOrDefault(k.name(),0);
        return k.base() * (1 + alpha * Math.min(c, 5));
    }
}
