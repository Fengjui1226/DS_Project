package app.bl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CacheManager - 搜尋結果快取管理
 * 
 * 功能：
 * - LRU 快取策略
 * - TTL 過期機制
 * - 執行緒安全
 * - 快取統計
 */
public final class CacheManager {

    private CacheManager() {}

    // 快取設定（可從環境變數覆寫）
    private static final int MAX_CACHE_SIZE = getEnvInt("CACHE_MAX_SIZE", 100);
    private static final long CACHE_TTL_MS = getEnvLong("CACHE_TTL_SECONDS", 300) * 1000;

    // LRU 快取
    private static final Map<String, CacheEntry> cache = new LinkedHashMap<>(
        MAX_CACHE_SIZE + 1, 0.75f, true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    // 讀寫鎖
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 統計
    private static long hitCount = 0;
    private static long missCount = 0;

    // ==================== 快取項目 ====================

    public static class CacheEntry {
        public final String data;
        public final long createdAt;
        public final long expiresAt;

        public CacheEntry(String data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = this.createdAt + CACHE_TTL_MS;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long getRemainingTTL() {
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
    }

    // ==================== 主要 API ====================

    /**
     * 取得快取
     * @param key 快取鍵（通常是 query + city）
     * @return 快取資料，若不存在或已過期則回傳 null
     */
    public static String get(String key) {
        if (key == null) return null;

        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                missCount++;
                return null;
            }

            if (entry.isExpired()) {
                // 需要升級為寫鎖來刪除
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(key);
                    missCount++;
                    return null;
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }

            hitCount++;
            Logger.debug("快取命中: %s (剩餘 %d 秒)", key, entry.getRemainingTTL() / 1000);
            return entry.data;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 設定快取
     * @param key 快取鍵
     * @param data 要快取的資料
     */
    public static void put(String key, String data) {
        if (key == null || data == null) return;

        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry(data));
            Logger.debug("快取寫入: %s (大小: %d bytes)", key, data.length());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 建立快取鍵
     */
    public static String buildKey(String query, String city, int page) {
        return String.format("%s|%s|%d", 
            query != null ? query.toLowerCase() : "", 
            city != null ? city : "",
            page
        );
    }

    /**
     * 清除特定快取
     */
    public static void invalidate(String key) {
        if (key == null) return;

        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清除所有快取
     */
    public static void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            Logger.info("快取已清除");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清除過期的快取項目
     */
    public static int evictExpired() {
        lock.writeLock().lock();
        try {
            int before = cache.size();
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
            int evicted = before - cache.size();
            if (evicted > 0) {
                Logger.debug("清除 %d 個過期快取項目", evicted);
            }
            return evicted;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== 統計 ====================

    /**
     * 取得快取統計
     */
    public static CacheStats getStats() {
        lock.readLock().lock();
        try {
            return new CacheStats(cache.size(), hitCount, missCount, MAX_CACHE_SIZE, CACHE_TTL_MS);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static class CacheStats {
        public final int size;
        public final long hits;
        public final long misses;
        public final int maxSize;
        public final long ttlMs;

        public CacheStats(int size, long hits, long misses, int maxSize, long ttlMs) {
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.maxSize = maxSize;
            this.ttlMs = ttlMs;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, ttl=%ds}",
                size, maxSize, hits, misses, getHitRate() * 100, ttlMs / 1000
            );
        }

        public String toJson() {
            return String.format(
                "{\"size\":%d,\"maxSize\":%d,\"hits\":%d,\"misses\":%d,\"hitRate\":%.3f,\"ttlSeconds\":%d}",
                size, maxSize, hits, misses, getHitRate(), ttlMs / 1000
            );
        }
    }

    // ==================== 工具方法 ====================

    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long getEnvLong(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}