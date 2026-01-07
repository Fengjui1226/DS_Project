package app.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

import app.bl.Logger;

/**
 * 簡單的每IP請求速率限制器，用於防止過頻的請求。
 */
public class RateLimiter {

    private static class RateLimitInfo {
        int count = 0;
        long windowStart = System.currentTimeMillis();
    }

    private final int maxRequests;
    private final int windowSeconds;
    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * 判斷給定請求是否在速率限制範圍內。超出則回傳 false，否則回傳 true。
     * 每個 IP 有獨立的計數窗口。
     */
    public boolean allow(HttpExchange ex) {
        String clientIP = ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        RateLimitInfo info = rateLimitMap.get(clientIP);
        if (info == null) {
            RateLimitInfo newInfo = new RateLimitInfo();
            RateLimitInfo existing = rateLimitMap.putIfAbsent(clientIP, newInfo);
            info = (existing != null) ? existing : newInfo;
        }
        synchronized (info) {
            // 若超出時間窗，重置計數與時間窗起始
            if (now - info.windowStart > windowSeconds * 1000L) {
                info.count = 0;
                info.windowStart = now;
            }
            info.count++;
            if (info.count > maxRequests) {
                Logger.warn(String.format("觸發限流: IP=%s, count=%d (超過每%d秒%d次)", 
                           clientIP, info.count, windowSeconds, maxRequests));
                return false;
            }
            return true;
        }
    }
}
