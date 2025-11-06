package app.da;

import app.bl.PageNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FileHandler {

    /** 存 PageNode 結果：score,title,city,date,domain,url */
    public static void savePagesCSV(List<PageNode> pages, String path) {
        // 確保父資料夾存在
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
        } catch (Exception ignore) {}

        try (PrintWriter out = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            out.println("score,title,city,date,domain,url");
            for (PageNode p : pages) {
                String dateStr = p.getEventDate() == null ? "" : p.getEventDate().toString();
                out.printf("%.2f,%s,%s,%s,%s,%s%n",
                        p.getScore(),
                        csv(p.getTitle()),
                        csv(p.getCity()),
                        csv(dateStr),
                        csv(p.getDomain()),
                        csv(p.getUrl()));
            }
            System.out.println("[FileHandler] 已輸出至 " + path);
        } catch (IOException e) {
            System.err.println("[FileHandler] savePagesCSV failed: " + e.getMessage());
        }
    }

    private static String csv(String s) {
        if (s == null) return "\"\"";
        String v = s.replace("\r", " ").replace("\n", " ");
        v = v.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}