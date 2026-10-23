package com.casamix.assistant.orders;

import com.casamix.assistant.domain.Order;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrdersService {
    private final List<Order> orders = List.of(
            new Order("PED-1001", "cliente-1", "em_transporte",
                    List.of(new Order.OrderItem("ITEM-1", "Mesa de jantar"))),
            new Order("PED-2001", "cliente-2", "entregue",
                    List.of(new Order.OrderItem("ITEM-2", "Luminária"))));

    public Order getOrder(String orderId, String authenticatedClientId) {
        return orders.stream()
                .filter(order -> order.id().equals(orderId) && order.clientId().equals(authenticatedClientId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
    }

    public void validateItem(String orderId, String itemId, String authenticatedClientId) {
        Order order = getOrder(orderId, authenticatedClientId);
        if (order.items().stream().noneMatch(item -> item.id().equals(itemId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado no pedido");
        }
    }
}
