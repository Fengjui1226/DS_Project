package app;

import app.da.GoogleConnector;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String query = (args != null && args.length > 0) ? String.join(" ", args) : "台北 音樂 活動";

        System.out.println("# Google CSE results for: " + query);
        List<GoogleConnector.Result> results = GoogleConnector.search(query, 10);

        int i = 1;
        for (var r : results) {
            System.out.printf("%2d. %s%n    %s%n", i++, r.title, r.link);
        }
        if (results.isEmpty()) {
            System.out.println("(No results — check API key / CX / quota)");
        }
    }
}