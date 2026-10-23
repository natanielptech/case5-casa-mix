package com.casamix.assistant.model;

import com.casamix.assistant.domain.ExchangeToolInput;

public record ToolExtraction(ExchangeToolInput input, int inputTokens, int outputTokens) {
}
