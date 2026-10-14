package io.aigoodle.knowledge.rag;

import io.aigoodle.rag.api.Evidence;
import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RagResult;
import io.aigoodle.rag.api.RetrievalCandidate;
import io.aigoodle.rag.spi.ContextProcessor;
import io.aigoodle.rag.spi.FusionStrategy;
import io.aigoodle.rag.spi.QueryPlanner;
import io.aigoodle.rag.spi.RagPipeline;
import io.aigoodle.rag.spi.RagReranker;
import io.aigoodle.rag.spi.Retriever;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed, observable RAG execution pipeline. Agent orchestration can call this as a tool. */
public final class DefaultRagPipeline implements RagPipeline {
    private final List<QueryPlanner> planners;
    private final List<Retriever> retrievers;
    private final List<FusionStrategy> fusionStrategies;
    private final List<RagReranker> rerankers;
    private final List<ContextProcessor> contextProcessors;

    public DefaultRagPipeline(
            List<QueryPlanner> planners,
            List<Retriever> retrievers,
            List<FusionStrategy> fusionStrategies,
            List<RagReranker> rerankers,
            List<ContextProcessor> contextProcessors) {
        this.planners = planners;
        this.retrievers = retrievers;
        this.fusionStrategies = fusionStrategies;
        this.rerankers = rerankers;
        this.contextProcessors = contextProcessors;
    }

    @Override
    public RagResult retrieve(RagRequest request) {
        long started = System.nanoTime();
        QueryPlan plan = planner(request).plan(request);
        long planned = System.nanoTime();
        List<List<RetrievalCandidate>> resultSets = new ArrayList<>();
        List<RagPolicy.Channel> channels = request.policy().retrieval().channels();
        plan.queries().stream()
                .limit(Math.max(1, request.policy().budget().maxQueries()))
                .forEach(query -> retrievers.stream()
                        .filter(r -> channels.contains(r.getChannel()) && r.available(request))
                        .forEach(r -> resultSets.add(r.retrieve(query, request))));
        long retrieved = System.nanoTime();
        List<RetrievalCandidate> candidates = fuse(resultSets, request.policy().fusion());
        int maxCandidates = Math.max(1, request.policy().budget().maxCandidates());
        if (candidates.size() > maxCandidates) candidates = new ArrayList<>(candidates.subList(0, maxCandidates));
        if (request.policy().rerank().enabled()) candidates = rerank(request, candidates);
        long ranked = System.nanoTime();
        List<Evidence> evidence = candidates.stream()
                .limit(Math.max(1, request.policy().budget().maxEvidenceItems()))
                .map(DefaultRagPipeline::evidence)
                .toList();
        for (ContextProcessor processor : contextProcessors.stream()
                .sorted(Comparator.comparingInt(ContextProcessor::getOrder))
                .toList()) {
            evidence = processor.process(request, evidence, request.policy().context());
        }
        long finished = System.nanoTime();
        return new RagResult(
                request.query(),
                plan,
                evidence,
                Map.of(
                        "planning",
                        millis(started, planned),
                        "retrieval",
                        millis(planned, retrieved),
                        "ranking",
                        millis(retrieved, ranked),
                        "context",
                        millis(ranked, finished)));
    }

    private QueryPlanner planner(RagRequest request) {
        boolean direct = request.policy().queryPlanning().strategy() == RagPolicy.QueryStrategy.DIRECT;
        return planners.stream()
                .filter(p -> p.getName().equalsIgnoreCase(direct ? "DIRECT" : "MODEL"))
                .findFirst()
                .orElseGet(() -> planners.stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No QueryPlanner is available")));
    }

    private List<RetrievalCandidate> fuse(List<List<RetrievalCandidate>> sets, RagPolicy.Fusion policy) {
        if (sets.isEmpty()) return List.of();
        if (sets.size() == 1 || policy.method() == RagPolicy.FusionMethod.NONE) {
            Map<String, RetrievalCandidate> unique = new LinkedHashMap<>();
            sets.stream()
                    .flatMap(List::stream)
                    .sorted(Comparator.comparingDouble(RetrievalCandidate::score)
                            .reversed())
                    .forEach(c -> unique.putIfAbsent(c.datasetId() + ':' + c.segmentId(), c));
            return new ArrayList<>(unique.values());
        }
        return fusionStrategies.stream()
                .filter(f -> f.getMethod() == policy.method())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fusion strategy unavailable: " + policy.method()))
                .fuse(sets, policy);
    }

    private List<RetrievalCandidate> rerank(RagRequest request, List<RetrievalCandidate> candidates) {
        String provider = request.policy().rerank().provider();
        RagReranker selected = rerankers.stream()
                .filter(r -> r.getName().equalsIgnoreCase(provider == null ? "score" : provider))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            if (request.policy().rerank().failOpen()) return candidates;
            throw new IllegalStateException("Reranker unavailable: " + provider);
        }
        return selected.rerank(request.query(), candidates, request.policy().rerank());
    }

    private static Evidence evidence(RetrievalCandidate c) {
        return new Evidence(
                c.datasetId(),
                c.documentId(),
                c.segmentId(),
                c.position(),
                c.content(),
                c.parentContent(),
                c.score(),
                c.score(),
                null,
                List.of(c.queryId()),
                c.metadata());
    }

    private static long millis(long start, long end) {
        return (end - start) / 1_000_000;
    }
}
