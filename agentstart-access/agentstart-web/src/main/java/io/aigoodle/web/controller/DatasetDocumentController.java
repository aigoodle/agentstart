package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import io.aigoodle.knowledge.service.KnowledgeService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Document ingestion, inspection and reindexing endpoints for datasets. */
@RestController
@ConditionalOnBean(KnowledgeService.class)
@RequestMapping("/datasets")
public class DatasetDocumentController {

    private final KnowledgeService knowledgeService;

    public DatasetDocumentController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/{id}/documents")
    public ApiResponse<List<KnowledgeDocumentEntity>> listDocuments(@PathVariable String id) {
        return ApiResponse.ok(knowledgeService.listDocuments(currentTenantId(), id));
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ApiResponse<KnowledgeDocumentEntity> getDocument(@PathVariable String id, @PathVariable String documentId) {
        return ApiResponse.ok(knowledgeService.getDocument(currentTenantId(), id, documentId));
    }

    @GetMapping("/{id}/documents/{documentId}/parsed")
    public ApiResponse<ParsedDocument> getParsedDocument(@PathVariable String id, @PathVariable String documentId) {
        ParsedDocument parsed = knowledgeService.getParsedDocument(currentTenantId(), id, documentId);
        return parsed == null
                ? ApiResponse.error("parsed_document_not_found", "Parsed document not found: " + documentId)
                : ApiResponse.ok(parsed);
    }

    @PostMapping("/{id}/documents/text")
    public ApiResponse<KnowledgeDocumentEntity> addText(@PathVariable String id, @RequestBody AddTextRequest request) {
        return ApiResponse.ok(knowledgeService.addText(currentTenantId(), id, request.name(), request.text()));
    }

    @PostMapping("/{id}/documents/markdown")
    public ApiResponse<KnowledgeDocumentEntity> addMarkdown(
            @PathVariable String id, @RequestBody AddTextRequest request) {
        return ApiResponse.ok(knowledgeService.addMarkdown(currentTenantId(), id, request.name(), request.text()));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable String id, @PathVariable String documentId) {
        knowledgeService.deleteDocument(currentTenantId(), id, documentId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/documents/{documentId}/enabled")
    public ApiResponse<KnowledgeDocumentEntity> setDocumentEnabled(
            @PathVariable String id, @PathVariable String documentId, @RequestBody EnabledRequest request) {
        return ApiResponse.ok(knowledgeService.setDocumentEnabled(
                currentTenantId(), id, documentId, Boolean.TRUE.equals(request.enabled())));
    }

    @PostMapping("/{id}/documents/{documentId}/reindex")
    public ApiResponse<Map<String, Object>> reindex(@PathVariable String id, @PathVariable String documentId) {
        int segmentCount = knowledgeService.reindexDocument(currentTenantId(), id, documentId);
        return ApiResponse.ok(Map.of("segmentCount", segmentCount));
    }

    @PostMapping("/{id}/documents/{documentId}/reparse")
    public ApiResponse<KnowledgeDocumentEntity> reparse(@PathVariable String id, @PathVariable String documentId) {
        KnowledgeDocumentEntity document = knowledgeService.reparseDocument(currentTenantId(), id, documentId);
        return document == null
                ? ApiResponse.error("source_not_available", "Original source is not available for reparsing")
                : ApiResponse.ok(document);
    }

    public record AddTextRequest(String name, String text) {}

    public record EnabledRequest(Boolean enabled) {}
}
