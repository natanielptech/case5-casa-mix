package com.casamix.assistant.exchange;

import com.casamix.assistant.auth.ClientIdentityResolver;
import com.casamix.assistant.domain.ExchangeReceipt;
import com.casamix.assistant.domain.ExchangeRequest;
import com.casamix.assistant.domain.ExchangeToolInput;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SolicitarTrocaTool {
    public static final String NAME = "solicitar_troca";
    public static final String DESCRIPTION =
            "Abre uma solicitação de troca para um item de um pedido do cliente autenticado. "
                    + "Use somente após confirmar com o cliente o item e o motivo.";
    public static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
              "pedido_id":{"type":"string"},
              "item_id":{"type":"string"},
              "motivo":{"type":"string","enum":["defeito","avaria_transporte","arrependimento","item_errado"]}
            },"required":["pedido_id","item_id","motivo"],"additionalProperties":false}
            """;

    public static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pedido_id", Map.of("type", "string"),
                        "item_id", Map.of("type", "string"),
                        "motivo", Map.of("type", "string", "enum",
                                List.of("defeito", "avaria_transporte", "arrependimento", "item_errado"))),
                "required", List.of("pedido_id", "item_id", "motivo"),
                "additionalProperties", false);
    }

    private final ExchangeService exchangeService;
    private final ClientIdentityResolver identityResolver;

    public SolicitarTrocaTool(ExchangeService exchangeService, ClientIdentityResolver identityResolver) {
        this.exchangeService = exchangeService;
        this.identityResolver = identityResolver;
    }

    public ExchangeReceipt execute(ExchangeToolInput input, String authenticatedBearerToken) {
        String authenticatedClientId = identityResolver.requireClientId(authenticatedBearerToken);
        return exchangeService.create(
                new ExchangeRequest(input.pedido_id(), input.item_id(), input.motivo()), authenticatedClientId);
    }
}
