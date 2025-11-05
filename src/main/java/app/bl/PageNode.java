package app.bl;
import java.util.*;

public class PageNode {
    private final String url;
    private final String title;
    // 關鍵字 -> 詞頻
    private final Map<Keyword,Integer> tf;
    private double score = 0.0;

    private PageNode(String url, String title, Map<Keyword,Integer> tf){
        this.url=url; this.title=title; this.tf=new HashMap<>(tf);
    }
    public static PageNode of(String url, String title, Map<Keyword,Integer> tf){
        return new PageNode(url,title,tf);
    }
    public Map<Keyword,Integer> tf(){ return tf; }
    public String getUrl(){ return url; }
    public String getTitle(){ return title; }
    public double getScore(){ return score; }
    public void setScore(double s){ this.score=s; }
}
