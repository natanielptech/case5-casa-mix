package com.casamix.assistant.api;

import com.casamix.assistant.auth.ClientIdentityResolver;
import com.casamix.assistant.config.AppProperties;
import com.casamix.assistant.domain.ChatRequest;
import com.casamix.assistant.domain.ChatResponse;
import com.casamix.assistant.exchange.SolicitarTrocaTool;
import com.casamix.assistant.service.AssistantService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/assistente")
public class AssistantController {
    private final AssistantService assistantService;
    private final ClientIdentityResolver identityResolver;
    private final AppProperties properties;

    public AssistantController(AssistantService assistantService, ClientIdentityResolver identityResolver,
                               AppProperties properties) {
        this.assistantService = assistantService;
        this.identityResolver = identityResolver;
        this.properties = properties;
    }

    @PostMapping("/conversas/{conversaId}/mensagens")
    public ChatResponse message(@PathVariable String conversaId, @Valid @RequestBody ChatRequest request,
                                @RequestHeader("Authorization") String authorization) {
        String clientId = identityResolver.requireClientId(authorization);
        return assistantService.process(conversaId, request.texto(), authorization, clientId);
    }

    @GetMapping("/tools/solicitar_troca")
    public Map<String, Object> exchangeToolDefinition() {
        return Map.of("name", SolicitarTrocaTool.NAME, "description", SolicitarTrocaTool.DESCRIPTION,
                "input_schema", SolicitarTrocaTool.schema(),
                "version", properties.model().toolsVersion());
    }
}
