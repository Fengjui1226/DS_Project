package app.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

import app.bl.PageNode;
import app.bl.UserProfile;

public class SessionManager {

    public static class Session {
        public final UserProfile profile = new UserProfile();
        public volatile List<PageNode> lastResults = new ArrayList<PageNode>();
        public volatile String lastQuery = "";
        public volatile long lastAccess = System.currentTimeMillis();
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final long ttlMillis;

    public SessionManager(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public Session getSession(HttpExchange ex) {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();

        Session s = sessions.get(ip);
        if (s == null) {
            Session created = new Session();
            Session prev = sessions.putIfAbsent(ip, created);
            s = (prev != null) ? prev : created;
        }
        s.lastAccess = System.currentTimeMillis();

        // 便宜的清理：每次 getSession 都順便清掉過期（避免 map 無限長）
        cleanupExpired();

        return s;
    }

    private void cleanupExpired() {
        if (ttlMillis <= 0) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            if (s != null && (now - s.lastAccess) > ttlMillis) {
                sessions.remove(e.getKey());
            }
        }
    }
}
