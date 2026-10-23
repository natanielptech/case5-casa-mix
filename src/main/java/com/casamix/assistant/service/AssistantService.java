package com.casamix.assistant.service;

import com.casamix.assistant.config.AppProperties;
import com.casamix.assistant.domain.ChatResponse;
import com.casamix.assistant.domain.DocumentChunk;
import com.casamix.assistant.domain.ExchangeReason;
import com.casamix.assistant.domain.ExchangeToolInput;
import com.casamix.assistant.domain.Intent;
import com.casamix.assistant.exchange.SolicitarTrocaTool;
import com.casamix.assistant.model.FoundationModelClient;
import com.casamix.assistant.model.ModelResult;
import com.casamix.assistant.observability.AssistantMetrics;
import com.casamix.assistant.observability.ObservedOperation;
import com.casamix.assistant.orders.OrdersService;
import com.casamix.assistant.rag.PromptBuilder;
import com.casamix.assistant.rag.RagService;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantService {
    private static final Pattern EXCHANGE_DETAILS = Pattern.compile(
            "(?i)pedido\\s*[:#]?\\s*([\\w-]+).*?item\\s*[:#]?\\s*([\\w-]+).*?motivo\\s*[:#]?\\s*"
                    + "(defeito|avaria_transporte|arrependimento|item_errado)");
    private static final Pattern ORDER_ID = Pattern.compile("(?i)pedido\\s*[:#]?\\s*([\\w-]+)");
    private static final Pattern AFFIRMATIVE = Pattern.compile("(?i)^\\s*(sim|confirmo|pode abrir|confirmar)\\b");
    private static final Pattern NEGATIVE = Pattern.compile("(?i)^\\s*(n[aã]o|cancelar|desistir)\\b");
    private static final String AI_NOTICE = "Sou um assistente de IA da CasaMix. ";

    private final AppProperties properties;
    private final IntentClassifier classifier;
    private final InputRedactor inputRedactor;
    private final OutputGuardrail outputGuardrail;
    private final ConversationStore conversations;
    private final RagService ragService;
    private final PromptBuilder promptBuilder;
    private final FoundationModelClient modelClient;
    private final OrdersService ordersService;
    private final SolicitarTrocaTool exchangeTool;
    private final AssistantMetrics metrics;
    private final ObservationRegistry observationRegistry;

    public AssistantService(AppProperties properties, IntentClassifier classifier, InputRedactor inputRedactor,
                            OutputGuardrail outputGuardrail, ConversationStore conversations, RagService ragService,
                            PromptBuilder promptBuilder, FoundationModelClient modelClient,
                            OrdersService ordersService, SolicitarTrocaTool exchangeTool,
                            AssistantMetrics metrics, ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.classifier = classifier;
        this.inputRedactor = inputRedactor;
        this.outputGuardrail = outputGuardrail;
        this.conversations = conversations;
        this.ragService = ragService;
        this.promptBuilder = promptBuilder;
        this.modelClient = modelClient;
        this.ordersService = ordersService;
        this.exchangeTool = exchangeTool;
        this.metrics = metrics;
        this.observationRegistry = observationRegistry;
    }

    public ChatResponse process(String conversationId, String message, String authorization, String clientId) {
        if (message.length() > properties.limits().maxInputCharacters()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensagem excede o limite permitido");
        }
        ConversationStore.Conversation conversation = conversations.get(clientId, conversationId);
        conversations.recordMessage(clientId, conversation, inputRedactor.redact(message),
                properties.limits().messagesPerConversation(), properties.limits().messagesPerClient());
        if (!conversation.incrementStep(properties.limits().maxStepsPerConversation())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de etapas da conversa atingido");
        }

        try (ObservedOperation conversationSpan = new ObservedOperation(observationRegistry, "assistente.conversa")) {
            conversationSpan.tag("conversation.id", conversationId);
            if (classifier.isPromptInjection(message)) {
                metrics.blockedInput();
                return response(conversation, Intent.BLOCKED, "Não posso atender a essa instrução. "
                        + "Posso ajudar com políticas CasaMix ou transferir você para um atendente.",
                        false, List.of(), 0, 0);
            }

            Intent intent;
            try (ObservedOperation classification = new ObservedOperation(observationRegistry,
                    "assistente.classificacao")) {
                intent = conversation.pendingExchange() != null
                        && (AFFIRMATIVE.matcher(message).find() || NEGATIVE.matcher(message).find())
                        ? Intent.EXCHANGE : classifier.classify(message);
            }
            return switch (intent) {
                case HUMAN -> transfer(conversation);
                case POLICY -> answerPolicy(conversation, message, clientId);
                case EXCHANGE -> handleExchange(conversation, message, authorization, clientId);
                case BLOCKED -> throw new IllegalStateException("Intenção bloqueada não esperada");
                case OUT_OF_SCOPE -> outOfScope(conversation);
            };
        }
    }

    private ChatResponse answerPolicy(ConversationStore.Conversation conversation, String message, String clientId) {
        List<DocumentChunk> documents;
        try (ObservedOperation ignored = new ObservedOperation(observationRegistry, "assistente.recuperacao")) {
            documents = ragService.retrieve(message);
        }
        String prompt = promptBuilder.build(documents, inputRedactor.redact(message));
        int estimatedInputTokens = Math.max(1, prompt.length() / 4);
        conversations.recordTokens(clientId, estimatedInputTokens, properties.limits().estimatedTokensPerClient());
        ModelResult result;
        try (ObservedOperation modelSpan = new ObservedOperation(observationRegistry, "assistente.modelo")) {
            result = modelClient.complete(prompt);
            modelSpan.tag("input.tokens", Integer.toString(result.inputTokens()));
            modelSpan.tag("output.tokens", Integer.toString(result.outputTokens()));
            modelSpan.tag("model.version", modelClient.modelVersion());
        }
        conversations.recordTokens(clientId, result.outputTokens(), properties.limits().estimatedTokensPerClient());
        metrics.modelUsage(result.inputTokens(), result.outputTokens());
        if (result.text().toLowerCase(Locale.ROOT).contains("não encontrei")
                || result.text().toLowerCase(Locale.ROOT).contains("não sei")) {
            metrics.notKnown();
        }
        String answer = outputGuardrail.sanitize(result.text());
        List<String> sources = documents.stream().map(DocumentChunk::origin).distinct().toList();
        if (!sources.isEmpty() && !answer.contains("[")) {
            answer += " [" + sources.get(0) + "]";
        }
        return response(conversation, Intent.POLICY, answer, false, sources,
                result.inputTokens(), result.outputTokens());
    }

    private ChatResponse handleExchange(ConversationStore.Conversation conversation, String message,
                                       String authorization, String clientId) {
        ExchangeToolInput pending = conversation.pendingExchange();
        if (pending != null && AFFIRMATIVE.matcher(message).find()) {
            try (ObservedOperation ignored = new ObservedOperation(observationRegistry, "assistente.ferramenta.troca")) {
                conversation.clearPendingExchange();
                var receipt = exchangeTool.execute(pending, authorization);
                return response(conversation, Intent.EXCHANGE,
                        "Solicitação de troca " + receipt.solicitacao_id() + " aberta com status "
                                + receipt.status() + ".", false, List.of(), 0, 0);
            }
        }
        if (pending != null && NEGATIVE.matcher(message).find()) {
            conversation.clearPendingExchange();
            return response(conversation, Intent.EXCHANGE, "Tudo bem, não abri a solicitação de troca.",
                    false, List.of(), 0, 0);
        }

        Matcher details = EXCHANGE_DETAILS.matcher(message);
        if (details.find()) {
            ExchangeToolInput proposal;
            try {
                proposal = new ExchangeToolInput(details.group(1), details.group(2),
                        ExchangeReason.valueOf(details.group(3).toLowerCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return response(conversation, Intent.EXCHANGE,
                        "Motivo inválido. Use defeito, avaria_transporte, arrependimento ou item_errado.",
                        false, List.of(), 0, 0);
            }
            try (ObservedOperation ignored = new ObservedOperation(observationRegistry, "assistente.pedidos.validacao")) {
                ordersService.validateItem(proposal.pedido_id(), proposal.item_id(), clientId);
            }
            conversation.pendingExchange(proposal);
            return response(conversation, Intent.EXCHANGE, "Confirma a troca do item " + proposal.item_id()
                    + " do pedido " + proposal.pedido_id() + " pelo motivo " + proposal.motivo()
                    + "? Responda sim para abrir ou não para cancelar.", false, List.of(), 0, 0);
        }

        Matcher order = ORDER_ID.matcher(message);
        if (message.toLowerCase(Locale.ROOT).contains("status") && order.find()) {
            try (ObservedOperation ignored = new ObservedOperation(observationRegistry, "assistente.ferramenta.pedido")) {
                var found = ordersService.getOrder(order.group(1), clientId);
                return response(conversation, Intent.EXCHANGE,
                        "O pedido " + found.id() + " está " + found.status() + ".", false, List.of(), 0, 0);
            }
        }

        int extractionInputTokens = 0;
        int extractionOutputTokens = 0;
        if (properties.model().apiKey() != null && !properties.model().apiKey().isBlank()) {
            conversations.recordTokens(clientId, Math.max(1, message.length() / 4),
                    properties.limits().estimatedTokensPerClient());
            java.util.Optional<com.casamix.assistant.model.ToolExtraction> extraction;
            try (ObservedOperation modelSpan = new ObservedOperation(
                    observationRegistry, "assistente.modelo.extracao_ferramenta")) {
                extraction = modelClient.extractExchangeProposal(inputRedactor.redact(message));
                if (extraction.isPresent()) {
                    extractionInputTokens = extraction.get().inputTokens();
                    extractionOutputTokens = extraction.get().outputTokens();
                    modelSpan.tag("input.tokens", Integer.toString(extractionInputTokens));
                    modelSpan.tag("output.tokens", Integer.toString(extractionOutputTokens));
                    modelSpan.tag("model.version", modelClient.modelVersion());
                    conversations.recordTokens(clientId, extractionOutputTokens,
                            properties.limits().estimatedTokensPerClient());
                    metrics.modelUsage(extractionInputTokens, extractionOutputTokens);
                }
            }
            if (extraction.isPresent() && extraction.get().input() != null) {
                ExchangeToolInput proposal = extraction.get().input();
                try (ObservedOperation ignored = new ObservedOperation(
                        observationRegistry, "assistente.pedidos.validacao")) {
                    ordersService.validateItem(proposal.pedido_id(), proposal.item_id(), clientId);
                }
                conversation.pendingExchange(proposal);
                return response(conversation, Intent.EXCHANGE, "Confirma a troca do item " + proposal.item_id()
                        + " do pedido " + proposal.pedido_id() + " pelo motivo " + proposal.motivo()
                        + "? Responda sim para abrir ou não para cancelar.", false, List.of(),
                        extractionInputTokens, extractionOutputTokens);
            }
        }
        return response(conversation, Intent.EXCHANGE,
                "Para consultar um pedido, informe seu número. Para solicitar troca, envie pedido, item e motivo "
                        + "(defeito, avaria_transporte, arrependimento ou item_errado). "
                        + "Vou pedir sua confirmação antes de abrir a solicitação.",
                false, List.of(), extractionInputTokens, extractionOutputTokens);
    }

    private ChatResponse transfer(ConversationStore.Conversation conversation) {
        metrics.handoff();
        return response(conversation, Intent.HUMAN,
                "Vou transferir você para um atendente humano. Resumo: " + conversations.summary(conversation),
                true, List.of(), 0, 0);
    }

    private ChatResponse outOfScope(ConversationStore.Conversation conversation) {
        metrics.notKnown();
        return response(conversation, Intent.OUT_OF_SCOPE,
                "Não sei responder com as informações disponíveis. Posso transferir você para um atendente.",
                false, List.of(), 0, 0);
    }

    private ChatResponse response(ConversationStore.Conversation conversation, Intent intent, String answer,
                                  boolean handoff, List<String> sources, int inputTokens, int outputTokens) {
        String safeAnswer = outputGuardrail.sanitize(answer);
        conversation.addSummary("Assistente: " + safeAnswer);
        return new ChatResponse(AI_NOTICE + safeAnswer, intent.name(), handoff,
                handoff ? conversations.summary(conversation) : null, sources,
                properties.model().promptVersion(), modelClient.modelVersion(),
                properties.model().toolsVersion(), inputTokens, outputTokens);
    }
}
