package app.bl;

import java.util.*;

/**
 * SubPageNode - 子網頁節點
 */
public class SubPageNode {
    
    private String url;
    private String title;
    private String textContent;
    private String parentUrl;
    private double score = 0.0;
    private Map<Keyword, Integer> tf = new HashMap<>();
    private List<String> tokens = new ArrayList<>();
    
    public SubPageNode(String url, String title, String textContent, String parentUrl) {
        this.url = url;
        this.title = title != null ? title : "";
        this.textContent = textContent != null ? textContent : "";
        this.parentUrl = parentUrl;
    }
    
    public void calculateTF(List<String> queryTokens) {
        String content = (title + " " + textContent).toLowerCase();
        
        for (String token : queryTokens) {
            String t = token.toLowerCase();
            int count = countOccurrences(content, t);
            if (count > 0) {
                tf.put(Keyword.of(token), count);
                tokens.add(token);
            }
        }
    }
    
    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }
    
    public double calculateBaseScore() {
        double score = 0.0;
        
        for (Map.Entry<Keyword, Integer> entry : tf.entrySet()) {
            // 使用 base() 配合你的 Keyword 類別
            score += entry.getValue() * entry.getKey().base();
        }
        
        String titleLower = title.toLowerCase();
        for (String token : tokens) {
            if (titleLower.contains(token.toLowerCase())) {
                score += 5.0;
            }
        }
        
        return score;
    }
    
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getTextContent() { return textContent; }
    public String getParentUrl() { return parentUrl; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public Map<Keyword, Integer> getTf() { return tf; }
    public List<String> getTokens() { return tokens; }
    
    public String getDomain() {
        try {
            String u = url.toLowerCase();
            int p = u.indexOf("://");
            if (p >= 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            return (s > 0) ? u.substring(0, s) : u;
        } catch (Exception e) {
            return "";
        }
    }
    
    @Override
    public String toString() {
        return String.format("SubPage[%.1f]: %s", score, title);
    }
}