package app.bl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger - 統一日誌管理
 * 
 * 取代所有 System.out.println，支援：
 * - 日誌等級 (DEBUG, INFO, WARN, ERROR)
 * - 時間戳記
 * - 環境變數控制日誌等級
 * 
 * 生產環境設定：LOG_LEVEL=INFO 或 LOG_LEVEL=WARN
 * 開發環境設定：LOG_LEVEL=DEBUG
 */
public final class Logger {

    private Logger() {}

    public enum Level {
        DEBUG(0), INFO(1), WARN(2), ERROR(3), OFF(4);
        
        final int value;
        Level(int value) { this.value = value; }
    }

    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // 從環境變數讀取日誌等級，預設 INFO
    private static final Level CURRENT_LEVEL = parseLevel(
        System.getenv("LOG_LEVEL"), Level.INFO
    );

    private static Level parseLevel(String levelStr, Level defaultLevel) {
        if (levelStr == null || levelStr.isEmpty()) return defaultLevel;
        try {
            return Level.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultLevel;
        }
    }

    // ==================== 主要 API ====================

    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    public static void debug(String format, Object... args) {
        log(Level.DEBUG, String.format(format, args));
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void info(String format, Object... args) {
        log(Level.INFO, String.format(format, args));
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void warn(String format, Object... args) {
        log(Level.WARN, String.format(format, args));
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    public static void error(String format, Object... args) {
        log(Level.ERROR, String.format(format, args));
    }

    public static void error(String message, Throwable t) {
        log(Level.ERROR, message);
        if (t != null && CURRENT_LEVEL.value <= Level.ERROR.value) {
            t.printStackTrace(System.err);
        }
    }

    // ==================== 特殊格式 ====================

    /**
     * 印出標題框（用於啟動訊息等）
     */
    public static void banner(String... lines) {
        if (CURRENT_LEVEL.value > Level.INFO.value) return;
        
        int maxLen = 0;
        for (String line : lines) {
            maxLen = Math.max(maxLen, line.length());
        }
        
        String border = "═".repeat(maxLen + 4);
        System.out.println("╔" + border + "╗");
        for (String line : lines) {
            System.out.printf("║  %-" + maxLen + "s  ║%n", line);
        }
        System.out.println("╚" + border + "╝");
    }

    /**
     * 印出分隔線
     */
    public static void separator(String title) {
        if (CURRENT_LEVEL.value > Level.INFO.value) return;
        System.out.println("\n========== " + title + " ==========");
    }

    /**
     * 印出搜尋結果摘要
     */
    public static void searchSummary(String title, Object... items) {
        if (CURRENT_LEVEL.value > Level.INFO.value) return;
        System.out.println("\n📊 " + title + "：");
        for (Object item : items) {
            System.out.println("  " + item);
        }
    }

    // ==================== 內部方法 ====================

    private static void log(Level level, String message) {
        if (level.value < CURRENT_LEVEL.value) return;
        
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String levelTag = formatLevel(level);
        
        String output = String.format("[%s] %s %s", timestamp, levelTag, message);
        
        if (level == Level.ERROR) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
    }

    private static String formatLevel(Level level) {
        switch (level) {
            case DEBUG: return "[DEBUG]";
            case INFO:  return "[INFO] ";
            case WARN:  return "[WARN] ";
            case ERROR: return "[ERROR]";
            default:    return "[?????]";
        }
    }


    /**
     * 取得目前日誌等級
     */
    public static Level getCurrentLevel() {
        return CURRENT_LEVEL;
    }

    /**
     * 檢查是否啟用 DEBUG
     */
    public static boolean isDebugEnabled() {
        return CURRENT_LEVEL.value <= Level.DEBUG.value;
    }
}