package com.casamix.assistant.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExchangeToolInput(
        @NotBlank String pedido_id,
        @NotBlank String item_id,
        @NotNull ExchangeReason motivo) {
}
