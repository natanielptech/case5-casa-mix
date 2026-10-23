package com.casamix.assistant.domain;

import java.util.List;

public record Order(String id, String clientId, String status, List<OrderItem> items) {
    public record OrderItem(String id, String name) {}
}
