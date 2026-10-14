package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.web.support.UploadedDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnBean(KnowledgeService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/datasets")
public class ServletDatasetUploadController {
    private final KnowledgeService knowledgeService;

    public ServletDatasetUploadController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/{id}/documents/upload")
    public ApiResponse<KnowledgeDocumentEntity> upload(
            @PathVariable String id, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("file_required", "An uploaded file is required");
        }
        UploadedDocument upload = UploadedDocument.from(file);
        return ApiResponse.ok(knowledgeService.addFile(currentTenantId(), id, upload.filename(), upload.content()));
    }

    @PostMapping(value = "/preview-chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeService.ChunkPreview> previewChunks(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "rule", required = false) String ruleJson,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("file_required", "An uploaded file is required");
        }
        ProcessRule processRule;
        try {
            processRule = parseProcessRule(ruleJson);
        } catch (Exception exception) {
            return ApiResponse.error("rule_invalid", "'rule' is not valid ProcessRule JSON: " + exception.getMessage());
        }
        UploadedDocument upload = UploadedDocument.from(file);
        return ApiResponse.ok(knowledgeService.previewChunks(upload.content(), upload.filename(), processRule, limit));
    }

    private static ProcessRule parseProcessRule(String ruleJson) {
        return ruleJson == null || ruleJson.isBlank()
                ? ProcessRule.naive()
                : JsonUtils.parse(ruleJson, ProcessRule.class);
    }
}
