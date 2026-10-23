package com.casamix.assistant.service;

import com.casamix.assistant.domain.ExchangeToolInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ConversationStore {
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, Integer> clientMessages = new ConcurrentHashMap<>();
    private final Map<String, Integer> clientTokens = new ConcurrentHashMap<>();

    public Conversation get(String clientId, String conversationId) {
        return conversations.computeIfAbsent(key(clientId, conversationId), ignored -> new Conversation());
    }

    public synchronized void recordMessage(String clientId, Conversation conversation, String message,
                                           int maxConversationMessages, int maxClientMessages) {
        int clientCount = clientMessages.getOrDefault(clientId, 0);
        if (conversation.messageCount >= maxConversationMessages || clientCount >= maxClientMessages) {
            throw new ConversationLimitException("Limite de mensagens atingido");
        }
        conversation.messageCount++;
        conversation.addSummary("Cliente: " + message);
        clientMessages.put(clientId, clientCount + 1);
    }

    public synchronized void recordTokens(String clientId, int tokens, int limit) {
        int total = clientTokens.getOrDefault(clientId, 0) + tokens;
        if (total > limit) {
            throw new ConversationLimitException("Limite de tokens atingido");
        }
        clientTokens.put(clientId, total);
    }

    public String summary(Conversation conversation) {
        return conversation.summary();
    }

    private String key(String clientId, String conversationId) {
        return clientId + ":" + conversationId;
    }

    public static final class Conversation {
        private final List<String> messages = new ArrayList<>();
        private ExchangeToolInput pendingExchange;
        private int steps;
        private int messageCount;

        public synchronized ExchangeToolInput pendingExchange() {
            return pendingExchange;
        }

        public synchronized void pendingExchange(ExchangeToolInput proposal) {
            pendingExchange = proposal;
        }

        public synchronized void clearPendingExchange() {
            pendingExchange = null;
        }

        public synchronized void addSummary(String message) {
            messages.add(message);
            if (messages.size() > 8) {
                messages.remove(0);
            }
        }

        public synchronized String summary() {
            return String.join(" | ", messages);
        }

        public synchronized boolean incrementStep(int maxSteps) {
            return ++steps <= maxSteps;
        }
    }

    public static class ConversationLimitException extends RuntimeException {
        public ConversationLimitException(String message) {
            super(message);
        }
    }
}
