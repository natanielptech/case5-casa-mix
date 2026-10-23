package com.casamix.assistant.domain;

public record ExchangeRequest(String pedido_id, String item_id, ExchangeReason motivo) {
}
