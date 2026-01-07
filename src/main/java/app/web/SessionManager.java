package app.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

import app.bl.PageNode;
import app.bl.UserProfile;

/**
 * 簡易 Session 管理器：根據客戶端 IP 維持 Session 資訊 (最近結果列表、最近查詢等)
 */
public class SessionManager {

    /**
     * Session 資料結構，包含使用者的個人資料、最後查詢結果清單、最後查詢字串等。
     */
    public static class Session {
        public final UserProfile profile = new UserProfile();
        public volatile List<PageNode> lastResults = new ArrayList<>();
        public volatile String lastQuery = "";
        public volatile long lastAccess = System.currentTimeMillis();
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final long ttlMillis;  // session 時效（毫秒）

    public SessionManager(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * 根據請求取得對應的 Session。如果該客戶端尚無 Session，則建立新的。
     * 每次調用都會更新 lastAccess 並順便清理過期 Session。
     */
    public Session getSession(HttpExchange ex) {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        Session s = sessions.get(ip);
        if (s == null) {
            Session newSession = new Session();
            Session existing = sessions.putIfAbsent(ip, newSession);
            s = (existing != null) ? existing : newSession;
        }
        // 更新存取時間
        s.lastAccess = System.currentTimeMillis();
        // 清理過期的 sessions
        cleanupExpired();
        return s;
    }

    /**
     * 清除過久未使用而過期的 Session。
     */
    private void cleanupExpired() {
        if (ttlMillis <= 0) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session != null && (now - session.lastAccess) > ttlMillis) {
                sessions.remove(entry.getKey());
            }
        }
    }
}
