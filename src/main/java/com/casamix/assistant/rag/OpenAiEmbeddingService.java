package com.casamix.assistant.rag;

import com.casamix.assistant.config.AppProperties;
import com.casamix.assistant.observability.AssistantMetrics;
import com.casamix.assistant.observability.ObservedOperation;
import java.util.List;
import java.util.Map;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Primary
public class OpenAiEmbeddingService implements EmbeddingService {
    private final AppProperties properties;
    private final RestClient restClient;
    private final HashEmbeddingService localEmbeddingService;
    private final AssistantMetrics metrics;
    private final ObservationRegistry observationRegistry;

    public OpenAiEmbeddingService(AppProperties properties, RestClient.Builder builder,
                                  HashEmbeddingService localEmbeddingService, AssistantMetrics metrics,
                                  ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.model().baseUrl()).build();
        this.localEmbeddingService = localEmbeddingService;
        this.metrics = metrics;
        this.observationRegistry = observationRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        int inputTokens = Math.max(1, text.length() / 4);
        List<Double> embedding;
        try (ObservedOperation span = new ObservedOperation(observationRegistry, "assistente.modelo.embedding")) {
            span.tag("input.tokens", Integer.toString(inputTokens));
            span.tag("model.version", properties.model().apiKey() == null
                    || properties.model().apiKey().isBlank()
                    ? "local-hash-embedding-v1" : properties.model().embeddingModel());
            if (properties.model().apiKey() == null || properties.model().apiKey().isBlank()) {
                embedding = localEmbeddingService.embed(text);
            } else {
                embedding = embedRemotely(text);
            }
            metrics.embeddingUsage(inputTokens);
        }
        return embedding;
    }

    @SuppressWarnings("unchecked")
    private List<Double> embedRemotely(String text) {
        Map<String, Object> response = restClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + properties.model().apiKey())
                .body(Map.of("model", properties.model().embeddingModel(), "input", text))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()
                || !(data.get(0) instanceof Map<?, ?> first)
                || !(first.get("embedding") instanceof List<?> vector)) {
            throw new IllegalStateException("Resposta inválida do provedor de embeddings");
        }
        return vector.stream().map(value -> ((Number) value).doubleValue()).toList();
    }
}
