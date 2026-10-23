package com.casamix.assistant.rag;

import com.casamix.assistant.config.AppProperties;
import com.casamix.assistant.domain.DocumentChunk;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RagService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private final PolicyIndex index;
    private final EmbeddingService embeddingService;
    private final AppProperties properties;

    public RagService(PolicyIndex index, EmbeddingService embeddingService, AppProperties properties) {
        this.index = index;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    public List<DocumentChunk> retrieve(String query) {
        List<Double> queryVector = embeddingService.embed(query);
        Set<String> terms = terms(query);
        List<ScoredChunk> candidates = index.currentChunks(LocalDate.now()).stream()
                .map(chunk -> new ScoredChunk(chunk, 0.25 * cosine(queryVector, chunk.embedding())
                        + 0.75 * keywordScore(terms, chunk.text())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(properties.rag().candidateCount())
                .toList();

        return candidates.stream()
                .map(candidate -> new ScoredChunk(candidate.chunk(),
                        0.2 * candidate.score() + 0.8 * keywordScore(terms, candidate.chunk().text())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(properties.rag().resultCount())
                .map(ScoredChunk::chunk)
                .toList();
    }

    private Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() > 2) {
                result.add(term);
                if (term.equals("prazo")) {
                    result.add("dias");
                }
            }
        }
        return result;
    }

    private double keywordScore(Set<String> queryTerms, String text) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> documentTerms = terms(text);
        long matches = queryTerms.stream().filter(documentTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        return leftNorm == 0.0 || rightNorm == 0.0 ? 0.0 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
