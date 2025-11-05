package app.bl;

import java.util.*;

public class Index {
    // token -> docId 集合
    private final Map<String, Set<Integer>> postings = new HashMap<>();
    // docId -> PageNode
    private final List<PageNode> docs = new ArrayList<>();

    public int addDocument(PageNode p) {
        int id = docs.size();
        docs.add(p);
        for (String t : p.getTokens()) {
            postings.computeIfAbsent(norm(t), k -> new HashSet<>()).add(id);
        }
        return id;
    }

    public Set<Integer> lookup(String token) {
        return postings.getOrDefault(norm(token), Collections.emptySet());
    }

    public PageNode doc(int id) { return docs.get(id); }
    public List<PageNode> allDocs() { return Collections.unmodifiableList(docs); }

    private String norm(String s){ return s == null ? "" : s.trim().toLowerCase(); }
}