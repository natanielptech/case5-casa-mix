package com.casamix.assistant.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Model model,
        Auth auth,
        Limits limits,
        Rag rag,
        Evaluation evaluation) {

    public record Model(String baseUrl, String chatModel, String embeddingModel, String apiKey,
                        String promptVersion, String toolsVersion, int maxOutputTokens) {}

    public record Auth(Map<String, String> demoTokens) {}

    public record Limits(int messagesPerConversation, int messagesPerClient, int maxInputCharacters,
                         int maxStepsPerConversation, int estimatedTokensPerClient) {}

    public record Rag(int chunkWords, int overlapWords, int candidateCount, int resultCount) {}

    public record Evaluation(boolean enabled, double tolerance) {}
}
