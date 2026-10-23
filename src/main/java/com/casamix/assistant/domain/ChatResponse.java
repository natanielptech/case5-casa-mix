package com.casamix.assistant.domain;

import java.util.List;

public record ChatResponse(
        String resposta,
        String intencao,
        boolean transferenciaHumana,
        String resumoConversa,
        List<String> fontes,
        String promptVersion,
        String modelVersion,
        String toolsVersion,
        int inputTokens,
        int outputTokens) {
}
