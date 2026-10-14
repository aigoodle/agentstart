package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.HitTestingLogEntity;
import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.knowledge.service.CreateDatasetRequest;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.knowledge.service.UpdateDatasetRequest;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RagResult;
import io.aigoodle.rag.spi.FusionStrategy;
import io.aigoodle.rag.spi.QueryPlanner;
import io.aigoodle.rag.spi.RagPipeline;
import io.aigoodle.rag.spi.RagReranker;
import io.aigoodle.rag.spi.Retriever;
import io.aigoodle.web.dto.RagRetrieveRequestDto;
import io.aigoodle.web.dto.RetrieveRequestDto;
import io.aigoodle.web.support.RetrievalRequestMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dataset metadata and retrieval endpoints. */
@RestController
@ConditionalOnBean(KnowledgeService.class)
@RequestMapping("/datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final KnowledgeService knowledgeService;
    private final RagPipeline ragPipeline;
    private final List<QueryPlanner> queryPlanners;
    private final List<Retriever> ragRetrievers;
    private final List<FusionStrategy> fusionStrategies;
    private final List<RagReranker> ragRerankers;

    public DatasetController(
            DatasetService datasetService,
            KnowledgeService knowledgeService,
            RagPipeline ragPipeline,
            ObjectProvider<QueryPlanner> queryPlanners,
            ObjectProvider<Retriever> ragRetrievers,
            ObjectProvider<FusionStrategy> fusionStrategies,
            ObjectProvider<RagReranker> ragRerankers) {
        this.datasetService = datasetService;
        this.knowledgeService = knowledgeService;
        this.ragPipeline = ragPipeline;
        this.queryPlanners = queryPlanners.orderedStream().toList();
        this.ragRetrievers = ragRetrievers.orderedStream().toList();
        this.fusionStrategies = fusionStrategies.orderedStream().toList();
        this.ragRerankers = ragRerankers.orderedStream().toList();
    }

    @GetMapping
    public ApiResponse<List<DatasetEntity>> list() {
        return ApiResponse.ok(datasetService.list(currentTenantId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasetEntity> get(@PathVariable String id) {
        return ApiResponse.ok(datasetService.require(currentTenantId(), id));
    }

    @PostMapping
    public ApiResponse<DatasetEntity> create(@RequestBody CreateDatasetRequest request) {
        request.setTenantId(currentTenantId());
        return ApiResponse.ok(datasetService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DatasetEntity> update(@PathVariable String id, @RequestBody UpdateDatasetRequest request) {
        return ApiResponse.ok(datasetService.update(currentTenantId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        datasetService.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/hit-testing/history")
    public ApiResponse<List<HitTestingLogEntity>> hitTestingHistory(
            @PathVariable String id, @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(knowledgeService.listHitTestingHistory(currentTenantId(), id, limit));
    }

    @PostMapping("/{id}/retrieve")
    public ApiResponse<List<RetrievedSegment>> retrieve(
            @PathVariable String id, @RequestBody RetrieveRequestDto request) {
        return ApiResponse.ok(knowledgeService.retrieve(currentTenantId(), id, RetrievalRequestMapper.from(request)));
    }

    @PostMapping("/{id}/rag/retrieve")
    public ApiResponse<RagResult> ragRetrieve(@PathVariable String id, @RequestBody RagRetrieveRequestDto request) {
        DatasetEntity dataset = datasetService.require(currentTenantId(), id);
        RagPolicy policy = request.policy() != null
                ? request.policy()
                : datasetService.retrievalConfig(dataset).getRagPolicy();
        if (policy == null) policy = RagPolicy.defaults();
        RagResult result = ragPipeline.retrieve(new RagRequest(
                currentTenantId(),
                List.of(id),
                request.query(),
                request.conversationId(),
                policy,
                request.metadataFilter()));
        knowledgeService.recordRagHitTest(dataset, request.query(), policy, result);
        return ApiResponse.ok(result);
    }

    @GetMapping("/rag/capabilities")
    public ApiResponse<Map<String, Object>> ragCapabilities() {
        List<RagPolicy.FusionMethod> fusionMethods = new java.util.ArrayList<>();
        fusionMethods.add(RagPolicy.FusionMethod.NONE);
        fusionStrategies.stream()
                .map(FusionStrategy::getMethod)
                .distinct()
                .filter(method -> method != RagPolicy.FusionMethod.NONE)
                .forEach(fusionMethods::add);
        return ApiResponse.ok(Map.of(
                "queryStrategies",
                queryPlanners.stream().anyMatch(p -> p.getName().equalsIgnoreCase("MODEL"))
                        ? RagPolicy.QueryStrategy.values()
                        : new RagPolicy.QueryStrategy[] {RagPolicy.QueryStrategy.DIRECT},
                "retrievalChannels",
                ragRetrievers.stream().map(Retriever::getChannel).distinct().toList(),
                "fusionMethods",
                fusionMethods,
                "rerankers",
                ragRerankers.stream().map(RagReranker::getName).toList()));
    }
}
