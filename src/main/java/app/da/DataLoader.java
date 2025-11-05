package app.da;

import app.bl.PageNode;
import app.bl.Keyword;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.util.*;

/**
 * 讀取 resources/events.csv
 * 欄位：title,city,date(yyyy-MM-dd),url,domain,tokens(空白分隔)
 */
public class DataLoader {

    public static List<PageNode> loadCSV(File csv, Map<Keyword, Integer> keywordSeed) throws Exception {
        List<PageNode> list = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csv))) {
            String line;
            boolean header = true;

            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] a = line.split(",", -1);
                if (a.length < 6) continue;

                String title  = a[0].trim();
                String city   = a[1].trim();
                LocalDate dt  = LocalDate.parse(a[2].trim());
                String url    = a[3].trim();
                String domain = a[4].trim();
                List<String> tokens = Arrays.asList(a[5].trim().split("\\s+"));

                // 用 tokens 粗略統計關鍵字詞頻（之後可換真正斷詞）
                Map<Keyword,Integer> tf = new HashMap<>();
                for (Keyword k : keywordSeed.keySet()) {
                    int c = 0;
                    for (String t : tokens) if (t.contains(k.name())) c++;
                    if (c > 0) tf.put(k, c);
                }

                list.add(PageNode.of(url, title, tf, dt, city, domain, tokens));
            }
        }
        return list;
    }
}