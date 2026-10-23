package com.casamix.assistant.model;

import com.casamix.assistant.config.AppProperties;
import com.casamix.assistant.domain.ExchangeToolInput;
import com.casamix.assistant.exchange.SolicitarTrocaTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleModelClient implements FoundationModelClient {
    private static final Pattern DOCUMENT_BLOCK = Pattern.compile("(?s)\\[([^\\]]+)]\\s*(.*?)(?=\\n\\[|$)");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private final AppProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleModelClient(AppProperties properties, RestClient.Builder builder,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.model().baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ToolExtraction> extractExchangeProposal(String userMessage) {
        if (properties.model().apiKey() == null || properties.model().apiKey().isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> function = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", SolicitarTrocaTool.NAME,
                        "description", SolicitarTrocaTool.DESCRIPTION,
                        "parameters", SolicitarTrocaTool.schema()));
        String prompt = """
                Você extrai uma intenção de troca. Só solicite a ferramenta quando o cliente informar
                explicitamente o pedido, o item e um motivo permitido. Nunca inclua identificador de cliente.
                A ferramenta apenas prepara uma proposta; a solicitação final depende de confirmação explícita.
                Mensagem do cliente: %s
                """.formatted(userMessage);
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + properties.model().apiKey())
                .body(Map.of(
                        "model", properties.model().chatModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "tools", List.of(function),
                        "tool_choice", "auto",
                        "max_tokens", properties.model().maxOutputTokens(),
                        "temperature", 0))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Resposta inválida do provedor ao extrair a ferramenta");
        }
        Map<?, ?> usage = response.get("usage") instanceof Map<?, ?> value ? value : Map.of();
        int inputTokens = number(usage.get("prompt_tokens"));
        int outputTokens = number(usage.get("completion_tokens"));
        if (!(message.get("tool_calls") instanceof List<?> toolCalls) || toolCalls.isEmpty()) {
            return Optional.of(new ToolExtraction(null, inputTokens, outputTokens));
        }
        if (!(toolCalls.get(0) instanceof Map<?, ?> toolCall)
                || !(toolCall.get("function") instanceof Map<?, ?> calledFunction)
                || !SolicitarTrocaTool.NAME.equals(calledFunction.get("name"))
                || !(calledFunction.get("arguments") instanceof String arguments)) {
            return Optional.empty();
        }
        try {
            ExchangeToolInput input = objectMapper.readValue(arguments, ExchangeToolInput.class);
            if (input.pedido_id() == null || input.pedido_id().isBlank()
                    || input.item_id() == null || input.item_id().isBlank() || input.motivo() == null) {
                throw new IllegalStateException("Parâmetros incompletos na proposta de troca");
            }
            return Optional.of(new ToolExtraction(input, inputTokens, outputTokens));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Parâmetros inválidos na proposta da ferramenta solicitar_troca", exception);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelResult complete(String prompt) {
        if (properties.model().apiKey() == null || properties.model().apiKey().isBlank()) {
            return offlineAnswer(prompt);
        }
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + properties.model().apiKey())
                .body(Map.of(
                        "model", properties.model().chatModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "max_tokens", properties.model().maxOutputTokens(),
                        "temperature", 0))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content)) {
            throw new IllegalStateException("Resposta inválida do provedor de modelo");
        }
        Map<?, ?> usage = response.get("usage") instanceof Map<?, ?> value ? value : Map.of();
        return new ModelResult(content, number(usage.get("prompt_tokens")), number(usage.get("completion_tokens")));
    }

    @Override
    public String modelVersion() {
        return properties.model().apiKey() == null || properties.model().apiKey().isBlank()
                ? "local-extractive-v1" : properties.model().chatModel();
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private ModelResult offlineAnswer(String prompt) {
        String documents = section(prompt, "[DOCUMENTOS]", "[CLIENTE]");
        String question = section(prompt, "[CLIENTE]", "");
        String answer;
        if (documents.isBlank() || documents.contains("Nenhum documento vigente")) {
            answer = "Não encontrei essa informação nas políticas disponíveis. Posso transferir você para um atendente.";
        } else {
            Matcher matcher = DOCUMENT_BLOCK.matcher(documents);
            String bestSource = "CasaMix";
            String bestPassage = "";
            int bestScore = -1;
            while (matcher.find()) {
                String passage = matcher.group(2).trim();
                int score = relevance(question, passage);
                if (score > bestScore) {
                    bestScore = score;
                    bestSource = matcher.group(1);
                    bestPassage = passage;
                }
            }
            String safePassage = java.util.Arrays.stream(bestPassage.split("(?<=[.!?])\\s+"))
                    .map(String::trim)
                    .filter(sentence -> !sentence.isBlank())
                    .filter(sentence -> !sentence.matches("(?i).*ignore\\s+(?:todas\\s+)?(?:as\\s+)?instru[cç][oõ]es.*"))
                    .filter(sentence -> !sentence.matches("(?i).*revele dados.*"))
                    .collect(java.util.stream.Collectors.joining(" "));
            answer = (safePassage.isBlank() ? "Consulte a política vigente pelo atendimento." : safePassage)
                    + " [" + bestSource + "]";
        }
        int inputTokens = Math.max(1, prompt.length() / 4);
        int outputTokens = Math.max(1, answer.length() / 4);
        return new ModelResult(answer, inputTokens, outputTokens);
    }

    private int relevance(String question, String passage) {
        java.util.Set<String> queryTerms = tokens(question);
        java.util.Set<String> passageTerms = tokens(passage);
        queryTerms.retainAll(passageTerms);
        return queryTerms.size();
    }

    private java.util.Set<String> tokens(String text) {
        java.util.Set<String> result = new java.util.HashSet<>();
        Matcher matcher = WORD.matcher(text.toLowerCase(java.util.Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 2 && !java.util.Set.of("qual", "para", "uma", "por", "com", "que", "posso")
                    .contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private String section(String text, String start, String end) {
        int startAt = text.indexOf(start);
        if (startAt < 0) {
            return "";
        }
        int contentStart = startAt + start.length();
        int endAt = end.isBlank() ? -1 : text.indexOf(end, contentStart);
        return (endAt < 0 ? text.substring(contentStart) : text.substring(contentStart, endAt)).trim();
    }
}
