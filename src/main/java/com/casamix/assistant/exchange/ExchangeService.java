package com.casamix.assistant.exchange;

import com.casamix.assistant.domain.ExchangeReceipt;
import com.casamix.assistant.domain.ExchangeRequest;
import com.casamix.assistant.orders.OrdersService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExchangeService {
    private final OrdersService ordersService;

    public ExchangeService(OrdersService ordersService) {
        this.ordersService = ordersService;
    }

    public ExchangeReceipt create(ExchangeRequest request, String authenticatedClientId) {
        ordersService.validateItem(request.pedido_id(), request.item_id(), authenticatedClientId);
        return new ExchangeReceipt("TROCA-" + UUID.randomUUID(), "solicitada");
    }
}
