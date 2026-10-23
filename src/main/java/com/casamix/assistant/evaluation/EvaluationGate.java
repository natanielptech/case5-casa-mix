package com.casamix.assistant.evaluation;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EvaluationGate {
    public List<String> regressions(EvaluationMetrics baseline, EvaluationMetrics current, double tolerance) {
        List<String> regressions = new ArrayList<>();
        if (current.faithfulness() + tolerance < baseline.faithfulness()) {
            regressions.add("faithfulness");
        }
        if (current.correctness() + tolerance < baseline.correctness()) {
            regressions.add("correctness");
        }
        if (current.toolUsage() + tolerance < baseline.toolUsage()) {
            regressions.add("toolUsage");
        }
        if (current.appropriateRefusal() + tolerance < baseline.appropriateRefusal()) {
            regressions.add("appropriateRefusal");
        }
        return List.copyOf(regressions);
    }
}
