package com.casamix.assistant.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class HashEmbeddingService implements EmbeddingService {
    private static final int DIMENSIONS = 256;

    @Override
    public List<Double> embed(String text) {
        double[] vector = new double[DIMENSIONS];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[index] += 1.0;
        }
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(DIMENSIONS);
        for (double value : vector) {
            result.add(norm == 0.0 ? 0.0 : value / norm);
        }
        return List.copyOf(result);
    }
}
