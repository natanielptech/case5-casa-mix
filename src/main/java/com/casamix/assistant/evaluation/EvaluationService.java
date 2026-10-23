package com.casamix.assistant.evaluation;

import com.casamix.assistant.domain.Intent;
import com.casamix.assistant.model.FoundationModelClient;
import com.casamix.assistant.rag.PromptBuilder;
import com.casamix.assistant.rag.RagService;
import com.casamix.assistant.service.IntentClassifier;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
    private static final Pattern TOOL_DETAILS = Pattern.compile(
            "(?i)pedido\\s*[:#]?\\s*[\\w-]+.*?item\\s*[:#]?\\s*[\\w-]+.*?motivo\\s*[:#]?\\s*"
                    + "(defeito|avaria_transporte|arrependimento|item_errado)");
    private final IntentClassifier classifier;
    private final RagService ragService;
    private final PromptBuilder promptBuilder;
    private final FoundationModelClient modelClient;

    public EvaluationService(IntentClassifier classifier, RagService ragService, PromptBuilder promptBuilder,
                             FoundationModelClient modelClient) {
        this.classifier = classifier;
        this.ragService = ragService;
        this.promptBuilder = promptBuilder;
        this.modelClient = modelClient;
    }

    public EvaluationMetrics evaluate(List<EvaluationCase> cases) {
        int faithful = 0;
        int fidelityCases = 0;
        int correct = 0;
        int toolsCorrect = 0;
        int refusalsCorrect = 0;

        for (EvaluationCase testCase : cases) {
            Intent intent = classifier.isPromptInjection(testCase.question())
                    ? Intent.BLOCKED : classifier.classify(testCase.question());
            List<com.casamix.assistant.domain.DocumentChunk> documents = List.of();
            String answer = "";
            if (intent == Intent.POLICY) {
                documents = ragService.retrieve(testCase.question());
                answer = modelClient.complete(promptBuilder.build(documents, testCase.question())).text();
            }
            boolean sourceMatches = testCase.expectedSource() != null && !testCase.expectedSource().isBlank()
                    && documents.stream().anyMatch(document -> document.origin().equals(testCase.expectedSource()));
            if (testCase.expectedSource() != null && !testCase.expectedSource().isBlank()) {
                fidelityCases++;
                if (sourceMatches && answer.contains("[" + testCase.expectedSource() + "]")) {
                    faithful++;
                }
            }

            boolean correctAnswer = testCase.expectedAnswer() == null || testCase.expectedAnswer().isBlank()
                    ? intent.name().equals(testCase.expectedIntent())
                    : answer.toLowerCase(Locale.ROOT)
                            .contains(testCase.expectedAnswer().toLowerCase(Locale.ROOT));
            if (correctAnswer) {
                correct++;
            }
            boolean usedTool = intent == Intent.EXCHANGE && TOOL_DETAILS.matcher(testCase.question()).find();
            if (usedTool == testCase.expectedTool()) {
                toolsCorrect++;
            }
            boolean refused = intent == Intent.HUMAN || intent == Intent.BLOCKED;
            if (refused == testCase.expectedRefusal()) {
                refusalsCorrect++;
            }
        }

        return new EvaluationMetrics(
                ratio(faithful, fidelityCases),
                ratio(correct, cases.size()),
                ratio(toolsCorrect, cases.size()),
                ratio(refusalsCorrect, cases.size()),
                cases.size());
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }
}
