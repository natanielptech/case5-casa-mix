package com.casamix.assistant.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AssistantMetrics {
    private final MeterRegistry registry;

    public AssistantMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void modelUsage(int input, int output) {
        registry.counter("casamix.model.tokens", "direction", "input").increment(input);
        registry.counter("casamix.model.tokens", "direction", "output").increment(output);
        registry.counter("casamix.model.cost.estimated", "currency", "USD")
                .increment((input * 0.00000015) + (output * 0.0000006));
    }

    public void embeddingUsage(int input) {
        registry.counter("casamix.model.tokens", "direction", "embedding_input").increment(input);
        registry.counter("casamix.model.cost.estimated", "currency", "USD")
                .increment(input * 0.00000002);
    }

    public void notKnown() {
        registry.counter("casamix.responses.unknown").increment();
    }

    public void handoff() {
        registry.counter("casamix.handoffs").increment();
    }

    public void blockedInput() {
        registry.counter("casamix.guardrail.blocked").increment();
    }
}
