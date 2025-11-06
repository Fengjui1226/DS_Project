package app.da;

import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties P = new Properties();
    static {
        try (InputStream in = Config.class.getClassLoader()
                                          .getResourceAsStream("application.properties")) {
            if (in != null) P.load(in);
        } catch (Exception ignore) {}
    }
    public static String get(String key, String def) {
        String v = System.getenv(key.replace('.', '_').toUpperCase()); // 允許用環境變數覆寫
        if (v != null && !v.isBlank()) return v;
        return P.getProperty(key, def);
    }
}