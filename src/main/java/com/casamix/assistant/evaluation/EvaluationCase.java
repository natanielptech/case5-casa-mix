package com.casamix.assistant.evaluation;

public record EvaluationCase(
        String id,
        String question,
        String expectedIntent,
        String expectedAnswer,
        String expectedSource,
        boolean expectedTool,
        boolean expectedRefusal) {
}
