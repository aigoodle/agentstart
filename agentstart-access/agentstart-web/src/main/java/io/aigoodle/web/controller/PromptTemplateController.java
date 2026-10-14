package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.model.entity.PromptTemplateEntity;
import io.aigoodle.model.service.PromptTemplateDraft;
import io.aigoodle.model.service.PromptTemplatePatch;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.web.dto.PromptTemplateRequest;
import io.aigoodle.web.service.PromptReferenceScanner;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST facade over {@link PromptTemplateService}. Templates can be created, listed by
 * category and rendered inline — powers the frontend prompt library plus the "load
 * from template" affordance on the agent / workflow-node forms.
 */
@RestController
@RequestMapping("/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;
    private final PromptReferenceScanner referenceScanner;

    public PromptTemplateController(
            PromptTemplateService promptTemplateService,
            ObjectProvider<PromptReferenceScanner> referenceScannerProvider) {
        this.promptTemplateService = promptTemplateService;
        this.referenceScanner = referenceScannerProvider.getIfAvailable();
    }

    @GetMapping
    public ApiResponse<List<PromptTemplateEntity>> list(@RequestParam(required = false) String category) {
        return ApiResponse.ok(promptTemplateService.list(currentTenantId(), category));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromptTemplateEntity> get(@PathVariable String id) {
        return ApiResponse.ok(promptTemplateService.require(currentTenantId(), id));
    }

    @PostMapping
    public ApiResponse<PromptTemplateEntity> create(@RequestBody PromptTemplateRequest request) {
        return ApiResponse.ok(promptTemplateService.create(new PromptTemplateDraft(
                currentTenantId(),
                request.getName(),
                request.getCategory(),
                request.getDescription(),
                request.getContent(),
                request.getTags())));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromptTemplateEntity> update(
            @PathVariable String id, @RequestBody PromptTemplateRequest request) {
        return ApiResponse.ok(promptTemplateService.update(
                currentTenantId(),
                id,
                new PromptTemplatePatch(
                        request.getName(),
                        request.getCategory(),
                        request.getDescription(),
                        request.getContent(),
                        request.getTags())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        promptTemplateService.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    /** Variables referenced by a template — useful for previewing what needs filling in. */
    @GetMapping("/{id}/variables")
    public ApiResponse<List<String>> variables(@PathVariable String id) {
        return ApiResponse.ok(promptTemplateService.variablesOf(
                promptTemplateService.require(currentTenantId(), id).getContent()));
    }

    /** Render a template against a supplied variable map — for a "preview" button. */
    @PostMapping("/{id}/render")
    public ApiResponse<Map<String, Object>> render(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> variables) {
        String rendered = promptTemplateService.render(
                promptTemplateService.require(currentTenantId(), id).getContent(),
                variables == null ? Map.of() : variables);
        return ApiResponse.ok(Map.of("rendered", rendered));
    }

    /**
     * Which workflows reference this template? Scans every stored workflow's graph JSON
     * for {@code systemPromptTemplateId} strings equal to {@code id}. Powers the
     * "who uses me" affordance so users don't rename or delete a template out from
     * under a live workflow.
     */
    @GetMapping("/{id}/references")
    public ApiResponse<List<Map<String, Object>>> references(@PathVariable String id) {
        promptTemplateService.require(currentTenantId(), id); // 404 if template gone
        return ApiResponse.ok(
                referenceScanner == null ? List.of() : referenceScanner.findUsingTemplate(currentTenantId(), id));
    }
}
