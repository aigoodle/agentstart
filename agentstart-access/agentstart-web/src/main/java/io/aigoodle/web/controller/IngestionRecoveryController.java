package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.knowledge.async.DocumentIngestQueueEntity;
import io.aigoodle.knowledge.async.IngestionJobCoordinator;
import io.aigoodle.knowledge.async.IngestionRecoveryService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnBean(IngestionRecoveryService.class)
@RequestMapping("/datasets/{datasetId}/ingestion-jobs")
public class IngestionRecoveryController {
    private final IngestionJobCoordinator coordinator;
    private final IngestionRecoveryService recovery;

    public IngestionRecoveryController(IngestionJobCoordinator coordinator, IngestionRecoveryService recovery) {
        this.coordinator = coordinator;
        this.recovery = recovery;
    }

    @GetMapping("/poisoned")
    public ApiResponse<List<DocumentIngestQueueEntity>> poisoned(@PathVariable String datasetId) {
        return ApiResponse.ok(coordinator.listPoisoned(currentTenantId(), datasetId));
    }

    @PostMapping("/{documentId}/replay")
    public ApiResponse<Boolean> replay(@PathVariable String datasetId, @PathVariable String documentId) {
        DocumentIngestQueueEntity job = coordinator.find(currentTenantId(), documentId);
        if (job == null || !datasetId.equals(job.getDatasetId())) return ApiResponse.ok(false);
        return ApiResponse.ok(recovery.replay(currentTenantId(), documentId));
    }
}
