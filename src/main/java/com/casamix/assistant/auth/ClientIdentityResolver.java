package com.casamix.assistant.auth;

import com.casamix.assistant.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ClientIdentityResolver {
    private final AppProperties properties;

    public ClientIdentityResolver(AppProperties properties) {
        this.properties = properties;
    }

    public String requireClientId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação obrigatória");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        String clientId = properties.auth().demoTokens().get(token);
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }
        return clientId;
    }
}
