package com.casamix.assistant.domain;

import java.time.LocalDate;
import java.util.List;

public record DocumentChunk(
        String id,
        String origin,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String category,
        String text,
        List<Double> embedding) {

    public boolean isEffectiveOn(LocalDate date) {
        return !effectiveFrom.isAfter(date) && (effectiveUntil == null || !effectiveUntil.isBefore(date));
    }
}
