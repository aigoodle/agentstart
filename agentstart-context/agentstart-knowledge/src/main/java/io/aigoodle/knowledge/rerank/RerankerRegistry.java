package io.aigoodle.knowledge.rerank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every {@link Reranker} discovered in the application context. The first bean
 * registered for a given name wins, so third-party rerankers published as beans can
 * transparently replace defaults.
 */
public class RerankerRegistry {

    private final Map<String, Reranker> rerankers = new LinkedHashMap<>();
    private final Reranker fallback;

    public RerankerRegistry(List<Reranker> rerankerBeans, Reranker fallback) {
        this.fallback = fallback == null ? new NoopReranker() : fallback;
        if (rerankerBeans != null) {
            for (Reranker reranker : rerankerBeans) {
                rerankers.putIfAbsent(reranker.getName().toLowerCase(), reranker);
            }
        }
        rerankers.putIfAbsent(this.fallback.getName().toLowerCase(), this.fallback);
    }

    public Reranker get(String name) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return rerankers.getOrDefault(name.toLowerCase(), fallback);
    }

    public Reranker fallback() {
        return fallback;
    }

    public List<String> names() {
        return List.copyOf(rerankers.keySet());
    }
}
