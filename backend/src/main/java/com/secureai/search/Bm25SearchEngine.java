package com.secureai.search;

import com.secureai.model.CodeIndexEntry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class Bm25SearchEngine {

    private static final Pattern TOKEN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{1,}");
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    public List<SearchHit> search(List<CodeIndexEntry> entries, String query, int topK) {
        if (entries.isEmpty()) {
            return List.of();
        }

        List<Document> docs = new ArrayList<>();
        Map<String, Integer> docFreq = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CodeIndexEntry entry = entries.get(i);
            String text = entry.getFilePath() + " " + entry.getChunkText();
            Map<String, Integer> tf = termFreq(text);
            docs.add(new Document(i, entry, tf, tf.values().stream().mapToInt(Integer::intValue).sum()));
            for (String term : tf.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }

        double avgDl = docs.stream().mapToInt(d -> d.length).average().orElse(1.0);
        Set<String> queryTerms = new HashSet<>(tokenize(query));
        int n = docs.size();

        List<SearchHit> hits = new ArrayList<>();
        for (Document doc : docs) {
            double score = 0.0;
            for (String term : queryTerms) {
                if (!doc.termFreq.containsKey(term)) {
                    continue;
                }
                int df = docFreq.getOrDefault(term, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                int tf = doc.termFreq.get(term);
                double denom = tf + K1 * (1 - B + B * doc.length / avgDl);
                score += idf * (tf * (K1 + 1)) / denom;
            }
            if (score > 0) {
                hits.add(new SearchHit(doc.entry, score));
            }
        }

        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    private static Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokenize(text)) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN.matcher(text.toLowerCase());
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private record Document(int id, CodeIndexEntry entry, Map<String, Integer> termFreq, int length) {}

    public record SearchHit(CodeIndexEntry entry, double score) {}
}
