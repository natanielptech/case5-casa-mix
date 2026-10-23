package com.casamix.assistant.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

public final class ObservedOperation implements AutoCloseable {
    private final Observation.Scope scope;
    private final Observation observation;

    public ObservedOperation(ObservationRegistry registry, String name) {
        observation = Observation.createNotStarted(name, registry).start();
        scope = observation.openScope();
    }

    public void tag(String key, String value) {
        observation.highCardinalityKeyValue(key, value);
    }

    @Override
    public void close() {
        scope.close();
        observation.stop();
    }
}
