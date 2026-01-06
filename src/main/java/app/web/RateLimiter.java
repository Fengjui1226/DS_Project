package app.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

import app.bl.Logger;

public class RateLimiter {

    static class RateLimitInfo {
        int count = 0;
        long windowStart = System.currentTimeMillis();
    }

    private final int maxRequests;
    private final int windowSeconds;

    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<String, RateLimitInfo>();

    public RateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public boolean allow(HttpExchange ex) {
        String clientIP = ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();

        RateLimitInfo info = rateLimitMap.get(clientIP);
        if (info == null) {
            RateLimitInfo created = new RateLimitInfo();
            RateLimitInfo prev = rateLimitMap.putIfAbsent(clientIP, created);
            info = (prev != null) ? prev : created;
        }

        synchronized (info) {
            if (now - info.windowStart > windowSeconds * 1000L) {
                info.count = 0;
                info.windowStart = now;
            }
            info.count++;

            if (info.count > maxRequests) {
                Logger.warn("限流觸發: IP=%s, count=%d", clientIP, info.count);
                return false;
            }
            return true;
        }
    }
}
