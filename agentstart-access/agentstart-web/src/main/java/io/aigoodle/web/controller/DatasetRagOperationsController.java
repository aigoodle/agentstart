package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.knowledge.entity.IndexVersionEntity;
import io.aigoodle.knowledge.eval.RetrievalEvaluationCase;
import io.aigoodle.knowledge.eval.RetrievalEvaluationDataset;
import io.aigoodle.knowledge.eval.RetrievalEvaluationReport;
import io.aigoodle.knowledge.eval.RetrievalEvaluator;
import io.aigoodle.knowledge.index.IndexVersionService;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.spi.RagPipeline;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.knowledge.service.KnowledgeService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

/** Operational RAG APIs consumed by the embeddable knowledge-hub component. */
@RestController
@ConditionalOnBean(KnowledgeService.class)
@RequestMapping("/datasets/{datasetId}/rag")
public class DatasetRagOperationsController {
    private final KnowledgeService knowledgeService;
    private final IndexVersionService versions;
    private final RetrievalEvaluator evaluator;
    private final RagPipeline pipeline;
    private final DatasetService datasetService;

    public DatasetRagOperationsController(
            KnowledgeService knowledgeService, IndexVersionService versions, RetrievalEvaluator evaluator,
            RagPipeline pipeline, DatasetService datasetService) {
        this.knowledgeService = knowledgeService;
        this.versions = versions;
        this.evaluator = evaluator;
        this.pipeline = pipeline;
        this.datasetService = datasetService;
    }

    @GetMapping("/index-versions")
    public ApiResponse<List<IndexVersionEntity>> listVersions(@PathVariable String datasetId) {
        knowledgeService.requireDataset(currentTenantId(), datasetId);
        return ApiResponse.ok(versions.list(currentTenantId(), datasetId));
    }

    @PostMapping("/index-versions")
    public ApiResponse<IndexVersionEntity> beginVersion(
            @PathVariable String datasetId, @RequestBody BeginVersionRequest request) {
        knowledgeService.requireDataset(currentTenantId(), datasetId);
        return ApiResponse.ok(versions.beginRebuild(
                currentTenantId(),
                datasetId,
                request.version(),
                request.embeddingModelVersion(),
                request.chunkingRuleVersion(),
                request.contentChecksum()));
    }

    @PostMapping("/index-versions/{versionId}/activate")
    public ApiResponse<Void> activate(@PathVariable String datasetId, @PathVariable String versionId) {
        versions.activate(currentTenantId(), datasetId, versionId);
        return ApiResponse.ok();
    }

    @PostMapping("/evaluate")
    public ApiResponse<RetrievalEvaluationReport> evaluate(
            @PathVariable String datasetId, @RequestBody EvaluationRequest request) {
        knowledgeService.requireDataset(currentTenantId(), datasetId);
        RagPolicy policy = request.policy() != null ? request.policy()
                : datasetService.retrievalConfig(datasetService.require(currentTenantId(), datasetId)).getRagPolicy();
        RagPolicy effective = policy == null ? RagPolicy.defaults() : policy;
        int topK = request.topK() == null ? effective.rerank().topN() : request.topK();
        List<RetrievalEvaluationCase> cases = request.cases() == null
                ? List.of()
                : request.cases().stream()
                        .map(c -> new RetrievalEvaluationCase(
                                c.id(),
                                c.query(),
                                c.relevantSegmentIds() == null ? Set.of() : c.relevantSegmentIds(),
                                c.noAnswerExpected(),
                                c.citedSegmentIds()))
                        .toList();
        RetrievalEvaluationDataset dataset = new RetrievalEvaluationDataset(request.name(), request.version(), cases);
        RetrievalEvaluationReport report = evaluator.evaluate(
                dataset, request.experimentName(), topK, request.configuration(), query -> pipeline
                        .retrieve(new RagRequest(currentTenantId(), List.of(datasetId), query, null, effective, null))
                        .evidence().stream().limit(topK).map(hit -> hit.segmentId()).toList());
        return ApiResponse.ok(report);
    }

    public record BeginVersionRequest(
            String version, String embeddingModelVersion, String chunkingRuleVersion, String contentChecksum) {}

    public record EvaluationCaseRequest(
            String id,
            String query,
            Set<String> relevantSegmentIds,
            boolean noAnswerExpected,
            List<String> citedSegmentIds) {}

    public record EvaluationRequest(
            String name,
            String version,
            String experimentName,
            Integer topK,
            List<EvaluationCaseRequest> cases,
            Map<String, String> configuration,
            RagPolicy policy) {}
}
