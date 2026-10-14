package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.knowledge.entity.SegmentEntity;
import io.aigoodle.knowledge.service.KnowledgeService;
import java.util.List;
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

/** Segment inspection and editing endpoints for knowledge documents. */
@RestController
@ConditionalOnBean(KnowledgeService.class)
@RequestMapping("/datasets")
public class DatasetSegmentController {

    private final KnowledgeService knowledgeService;

    public DatasetSegmentController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/{id}/documents/{documentId}/segments")
    public ApiResponse<List<SegmentEntity>> listSegments(
            @PathVariable String id,
            @PathVariable String documentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.ok(knowledgeService.listSegments(currentTenantId(), id, documentId, page, pageSize));
    }

    @PostMapping("/{id}/documents/{documentId}/segments")
    public ApiResponse<SegmentEntity> appendSegment(
            @PathVariable String id, @PathVariable String documentId, @RequestBody UpdateSegmentRequest request) {
        SegmentEntity segment = knowledgeService.appendSegment(currentTenantId(), id, documentId, request.content());
        return segment == null
                ? ApiResponse.error("document_not_found", "Document not found: " + documentId)
                : ApiResponse.ok(segment);
    }

    @PutMapping("/{id}/documents/{documentId}/segments/{segmentId}")
    public ApiResponse<SegmentEntity> updateSegment(
            @PathVariable String id,
            @PathVariable String documentId,
            @PathVariable String segmentId,
            @RequestBody UpdateSegmentRequest request) {
        SegmentEntity segment =
                knowledgeService.updateSegment(currentTenantId(), id, documentId, segmentId, request.content());
        return segment == null
                ? ApiResponse.error("segment_not_found", "Segment not found: " + segmentId)
                : ApiResponse.ok(segment);
    }

    @DeleteMapping("/{id}/documents/{documentId}/segments/{segmentId}")
    public ApiResponse<Void> deleteSegment(
            @PathVariable String id, @PathVariable String documentId, @PathVariable String segmentId) {
        knowledgeService.deleteSegment(currentTenantId(), id, documentId, segmentId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/documents/{documentId}/segments/{segmentId}/enabled")
    public ApiResponse<SegmentEntity> setSegmentEnabled(
            @PathVariable String id,
            @PathVariable String documentId,
            @PathVariable String segmentId,
            @RequestBody EnabledRequest request) {
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        SegmentEntity segment =
                knowledgeService.setSegmentEnabled(currentTenantId(), id, documentId, segmentId, enabled);
        return segment == null
                ? ApiResponse.error("segment_not_found", "Segment not found: " + segmentId)
                : ApiResponse.ok(segment);
    }

    public record UpdateSegmentRequest(String content) {}

    public record EnabledRequest(Boolean enabled) {}
}
