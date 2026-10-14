package io.aigoodle.knowledge.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic bag-of-words hashing embedding for offline tests: shared tokens
 * produce overlapping dimensions, so semantically similar text yields high cosine
 * similarity. No network, fully reproducible.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private final int dim;

    public HashingEmbeddingModel(int dim) {
        this.dim = dim;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(embed(text), index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[dim];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank()) continue;
            int h = Math.floorMod(token.hashCode(), dim);
            vec[h] += 1.0f;
        }
        double norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vec[i] /= (float) norm;
            }
        }
        return vec;
    }

    @Override
    public int dimensions() {
        return dim;
    }
}
