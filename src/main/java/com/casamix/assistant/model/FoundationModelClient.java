package com.casamix.assistant.model;

import java.util.Optional;

public interface FoundationModelClient {
    ModelResult complete(String prompt);

    Optional<ToolExtraction> extractExchangeProposal(String userMessage);

    String modelVersion();
}
