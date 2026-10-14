package io.aigoodle.workflow.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Deterministic bag-of-words hashing embedding for offline workflow+RAG tests. */
public class HashingEmbeddingModel implements EmbeddingModel {

    private final int dim;

    public HashingEmbeddingModel(int dim) {
        this.dim = dim;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        int i = 0;
        for (String text : request.getInstructions()) {
            out.add(new Embedding(embed(text), i++));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[dim];
        for (String token : text.toLowerCase().split("[^\\p{Alnum}]+")) {
            if (!token.isBlank()) {
                vec[Math.floorMod(token.hashCode(), dim)] += 1.0f;
            }
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
