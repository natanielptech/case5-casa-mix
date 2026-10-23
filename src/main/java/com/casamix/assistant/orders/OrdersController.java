package com.casamix.assistant.orders;

import com.casamix.assistant.auth.ClientIdentityResolver;
import com.casamix.assistant.domain.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/pedidos")
public class OrdersController {
    private final OrdersService ordersService;
    private final ClientIdentityResolver identityResolver;

    public OrdersController(OrdersService ordersService, ClientIdentityResolver identityResolver) {
        this.ordersService = ordersService;
        this.identityResolver = identityResolver;
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable String id, @RequestHeader("Authorization") String authorization) {
        return ordersService.getOrder(id, identityResolver.requireClientId(authorization));
    }
}
