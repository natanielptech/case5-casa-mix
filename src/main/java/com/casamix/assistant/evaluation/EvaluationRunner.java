package com.casamix.assistant.evaluation;

import com.casamix.assistant.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.evaluation", name = "enabled", havingValue = "true")
public class EvaluationRunner implements ApplicationRunner, ExitCodeGenerator {
    private final EvaluationService evaluationService;
    private final EvaluationGate gate;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private volatile boolean passed;

    public EvaluationRunner(EvaluationService evaluationService, EvaluationGate gate,
                            AppProperties properties, ObjectMapper objectMapper) {
        this.evaluationService = evaluationService;
        this.gate = gate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<EvaluationCase> cases = readCases("eval-set.json");
        EvaluationMetrics baseline = readBaseline("eval-baseline.json");
        EvaluationMetrics current = evaluationService.evaluate(cases);
        List<String> regressions = gate.regressions(baseline, current, properties.evaluation().tolerance());
        passed = regressions.isEmpty();
        System.out.println(objectMapper.writeValueAsString(new EvaluationReport(
                "casamix-pipeline-v1", current, passed, regressions)));
        if (!passed) {
            throw new IllegalStateException("Gate de avaliação bloqueou a promoção: " + regressions);
        }
    }

    @Override
    public int getExitCode() {
        return passed ? 0 : 1;
    }

    private List<EvaluationCase> readCases(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/" + path)) {
            if (stream == null) {
                throw new IOException("Arquivo de avaliação não encontrado: " + path);
            }
            return objectMapper.readValue(stream, new TypeReference<>() {});
        }
    }

    private EvaluationMetrics readBaseline(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/" + path)) {
            if (stream == null) {
                throw new IOException("Baseline de avaliação não encontrado: " + path);
            }
            return objectMapper.readValue(stream, EvaluationMetrics.class);
        }
    }

    private record EvaluationReport(String pipelineVersion, EvaluationMetrics metrics,
                                    boolean promotionAllowed, List<String> regressions) {}
}
