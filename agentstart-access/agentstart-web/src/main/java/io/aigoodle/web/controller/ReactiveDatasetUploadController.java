package io.aigoodle.web.controller;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.entity.KnowledgeDocumentEntity;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.web.support.UploadedDocument;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** WebFlux upload boundary; parsing and JDBC run off the event loop with scoped identity. */
@RestController
@ConditionalOnBean(KnowledgeService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@RequestMapping("/datasets")
public class ReactiveDatasetUploadController {
    private final KnowledgeService knowledgeService;
    private final int maxUploadBytes;

    public ReactiveDatasetUploadController(
            KnowledgeService knowledgeService,
            @Value("${spring-agent.web.max-upload-size:100MB}") DataSize maxUploadSize) {
        this.knowledgeService = knowledgeService;
        this.maxUploadBytes = Math.toIntExact(maxUploadSize.toBytes());
    }

    @PostMapping(value = "/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<KnowledgeDocumentEntity>> upload(
            @PathVariable String id, @RequestPart("file") FilePart file) {
        return read(file)
                .flatMap(upload -> withIdentity(() -> {
                    if (upload.content().length == 0) {
                        return ApiResponse.error("file_required", "An uploaded file is required");
                    }
                    return ApiResponse.ok(knowledgeService.addFile(
                            UserContextHolder.currentTenantId(), id, upload.filename(), upload.content()));
                }));
    }

    @PostMapping(value = "/preview-chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<KnowledgeService.ChunkPreview>> previewChunks(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "rule", required = false) String ruleJson,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return read(file)
                .flatMap(upload -> withIdentity(() -> {
                    if (upload.content().length == 0) {
                        return ApiResponse.error("file_required", "An uploaded file is required");
                    }
                    ProcessRule rule;
                    try {
                        rule = ruleJson == null || ruleJson.isBlank()
                                ? ProcessRule.naive()
                                : JsonUtils.parse(ruleJson, ProcessRule.class);
                    } catch (IllegalArgumentException exception) {
                        return ApiResponse.error("rule_invalid", "'rule' is not valid ProcessRule JSON");
                    }
                    return ApiResponse.ok(
                            knowledgeService.previewChunks(upload.content(), upload.filename(), rule, limit));
                }));
    }

    private Mono<UploadedDocument> read(FilePart file) {
        return DataBufferUtils.join(file.content(), maxUploadBytes)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return new UploadedDocument(file.filename(), bytes);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(new UploadedDocument(file.filename(), new byte[0]));
    }

    private static <T> Mono<T> withIdentity(Supplier<T> action) {
        return Mono.deferContextual(context -> {
            var principal = UserContextHolder.getReactive(context).orElse(null);
            return Mono.fromCallable(() -> UserContextHolder.callAs(principal, action))
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }
}
