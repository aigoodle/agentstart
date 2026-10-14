package io.aigoodle.knowledge.rag;

import io.aigoodle.rag.api.Evidence;
import io.aigoodle.knowledge.chunk.TokenCounter;
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
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("A retrieval query is required");
        }
        if (request.policy().retrieval().channels() == null || request.policy().retrieval().channels().isEmpty()) {
            throw new IllegalArgumentException("At least one retrieval channel is required");
        }
        for (RagPolicy.Channel channel : request.policy().retrieval().channels()) {
            if (retrievers.stream().noneMatch(r -> r.getChannel() == channel && r.available(request))) {
                throw new IllegalArgumentException("Retrieval channel unavailable: " + channel);
            }
        }
        long started = System.nanoTime();
        QueryPlan generated = planner(request).plan(request);
        int queryLimit = Math.max(1, Math.min(request.policy().queryPlanning().maxQueries(), request.policy().budget().maxQueries()));
        QueryPlan plan = new QueryPlan(generated.originalQuery(), generated.queries().stream().limit(queryLimit).toList());
        long planned = System.nanoTime();
        List<List<RetrievalCandidate>> resultSets = new ArrayList<>();
        List<RagPolicy.Channel> channels = request.policy().retrieval().channels();
        plan.queries().stream()
                .forEach(query -> retrievers.stream()
                        .filter(r -> channels.contains(r.getChannel()) && r.available(request))
                        .forEach(r -> resultSets.add(r.retrieve(query, request))));
        long retrieved = System.nanoTime();
        List<RetrievalCandidate> candidates = fuse(resultSets, request.policy().fusion());
        int maxCandidates = Math.max(1, request.policy().budget().maxCandidates());
        if (candidates.size() > maxCandidates) candidates = new ArrayList<>(candidates.subList(0, maxCandidates));
        Map<String, Double> fusionScores = new LinkedHashMap<>();
        candidates.forEach(c -> fusionScores.put(key(c), c.score()));
        if (request.policy().rerank().enabled()) candidates = rerank(request, candidates);
        long ranked = System.nanoTime();
        int topK = Math.max(1, Math.min(request.policy().rerank().topN(), request.policy().budget().maxEvidenceItems()));
        int perDocumentLimit = request.policy().retrieval().maxChunksPerDocument() == null
                ? 0 : Math.max(0, request.policy().retrieval().maxChunksPerDocument());
        Map<String, Integer> perDocument = new LinkedHashMap<>();
        List<Evidence> evidence = new ArrayList<>();
        for (RetrievalCandidate candidate : candidates) {
            String documentKey = candidate.datasetId() + ':' + candidate.documentId();
            if (perDocumentLimit > 0 && perDocument.getOrDefault(documentKey, 0) >= perDocumentLimit) continue;
            List<RetrievalCandidate> matches = resultSets.stream().flatMap(List::stream)
                    .filter(c -> key(c).equals(key(candidate))).toList();
            double retrievalScore = matches.stream().mapToDouble(RetrievalCandidate::score).max().orElse(candidate.score());
            Map<String, Object> metadata = new LinkedHashMap<>(candidate.metadata());
            Map<String, Double> channelScores = new LinkedHashMap<>();
            matches.forEach(c -> channelScores.merge(c.channel().name(), c.score(), Math::max));
            metadata.put("ragChannelScores", channelScores);
            evidence.add(new Evidence(
                    candidate.datasetId(), candidate.documentId(), candidate.segmentId(), candidate.position(),
                    candidate.content(), candidate.parentContent(), retrievalScore,
                    request.policy().fusion().method() == RagPolicy.FusionMethod.NONE ? null : fusionScores.get(key(candidate)),
                    request.policy().rerank().enabled() ? candidate.score() : null,
                    matches.stream().map(RetrievalCandidate::queryId).distinct().toList(), metadata));
            perDocument.merge(documentKey, 1, Integer::sum);
            if (evidence.size() >= topK) break;
        }
        for (ContextProcessor processor : contextProcessors.stream()
                .sorted(Comparator.comparingInt(ContextProcessor::getOrder))
                .toList()) {
            evidence = processor.process(request, evidence, request.policy().context());
        }
        evidence = boundContext(evidence, request.policy().context());
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
        int limit = Math.max(1, request.policy().rerank().candidateLimit());
        return selected.rerank(request.query(), candidates.subList(0, Math.min(limit, candidates.size())), request.policy().rerank());
    }

    private static String key(RetrievalCandidate candidate) {
        return candidate.datasetId() + ':' + candidate.segmentId();
    }

    /** Preserve source chunks for inspection while budgeting the assembled LLM context. */
    private static List<Evidence> boundContext(List<Evidence> evidence, RagPolicy.Context policy) {
        int remaining = Math.max(1, policy.maxTokens());
        List<Evidence> bounded = new ArrayList<>();
        for (Evidence item : evidence) {
            if (remaining <= 0) break;
            String expanded = policy.parentExpansion() || policy.neighborWindow() > 0 ? item.context() : null;
            String context = expanded == null || expanded.isBlank() ? item.content() : expanded;
            if (context == null) context = "";
            if (TokenCounter.count(context) > remaining) {
                int low = 0;
                int high = context.length();
                while (low < high) {
                    int mid = (low + high + 1) / 2;
                    if (TokenCounter.count(context.substring(0, mid)) <= remaining) low = mid;
                    else high = mid - 1;
                }
                if (low > 0 && low < context.length() && Character.isHighSurrogate(context.charAt(low - 1))) low--;
                context = context.substring(0, low);
            }
            remaining -= TokenCounter.count(context);
            bounded.add(new Evidence(item.datasetId(), item.documentId(), item.segmentId(), item.position(),
                    item.content(), context, item.retrievalScore(), item.fusionScore(), item.rerankScore(),
                    item.matchedQueryIds(), item.metadata()));
        }
        return bounded;
    }

    private static long millis(long start, long end) {
        return (end - start) / 1_000_000;
    }
}
