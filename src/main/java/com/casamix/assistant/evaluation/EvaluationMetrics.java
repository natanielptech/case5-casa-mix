package com.casamix.assistant.evaluation;

public record EvaluationMetrics(
        double faithfulness,
        double correctness,
        double toolUsage,
        double appropriateRefusal,
        int cases) {
}
