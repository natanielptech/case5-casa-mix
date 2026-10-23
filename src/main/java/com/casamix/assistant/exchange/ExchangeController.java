package com.casamix.assistant.exchange;

import com.casamix.assistant.auth.ClientIdentityResolver;
import com.casamix.assistant.domain.ExchangeReceipt;
import com.casamix.assistant.domain.ExchangeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/trocas")
public class ExchangeController {
    private final ExchangeService exchangeService;
    private final ClientIdentityResolver identityResolver;

    public ExchangeController(ExchangeService exchangeService, ClientIdentityResolver identityResolver) {
        this.exchangeService = exchangeService;
        this.identityResolver = identityResolver;
    }

    @PostMapping
    public ExchangeReceipt create(@Valid @RequestBody ExchangeRequest request,
                                  @RequestHeader("Authorization") String authorization) {
        return exchangeService.create(request, identityResolver.requireClientId(authorization));
    }
}
