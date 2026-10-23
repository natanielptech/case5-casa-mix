package com.casamix.assistant.rag;

import com.casamix.assistant.config.AppProperties;
import com.casamix.assistant.domain.DocumentChunk;
import com.casamix.assistant.domain.PolicyDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?\\d{4,5}[-\\s]?\\d{4}(?!\\d)");

    private final AppProperties properties;
    private final EmbeddingService embeddingService;

    public DocumentChunker(AppProperties properties, EmbeddingService embeddingService) {
        this.properties = properties;
        this.embeddingService = embeddingService;
    }

    public List<DocumentChunk> chunk(PolicyDocument document) {
        String safeText = EMAIL.matcher(document.content()).replaceAll("[EMAIL REMOVIDO]");
        safeText = PHONE.matcher(safeText).replaceAll("[TELEFONE REMOVIDO]");
        String[] words = safeText.trim().split("\\s+");
        int chunkSize = properties.rag().chunkWords();
        int overlap = properties.rag().overlapWords();
        int step = Math.max(1, chunkSize - overlap);
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(words.length, start + chunkSize);
            String text = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));
            String id = document.origin() + "#" + (chunks.size() + 1);
            chunks.add(new DocumentChunk(id, document.origin(), document.effectiveFrom(),
                    document.effectiveUntil(), document.category(), text, embeddingService.embed(text)));
            if (end == words.length) {
                break;
            }
        }
        return List.copyOf(chunks);
    }
}
