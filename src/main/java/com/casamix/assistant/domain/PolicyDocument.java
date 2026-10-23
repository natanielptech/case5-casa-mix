package com.casamix.assistant.domain;

import java.time.LocalDate;

public record PolicyDocument(
        String origin,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String category,
        String content) {

    public boolean isEffectiveOn(LocalDate date) {
        return !effectiveFrom.isAfter(date) && (effectiveUntil == null || !effectiveUntil.isBefore(date));
    }
}
