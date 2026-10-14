package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppAnnotationEntity;
import io.aigoodle.agent.service.AppAnnotationService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.api.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for application-owned answer annotations. */
@RestController
@ConditionalOnBean(AppAnnotationService.class)
@RequestMapping("/apps/{appId}/annotations")
public class AppAnnotationController {

    private final AppAnnotationService annotationService;
    private final AppService apps;

    public AppAnnotationController(AppAnnotationService annotationService, AppService apps) {
        this.annotationService = annotationService;
        this.apps = apps;
    }

    @GetMapping
    public ApiResponse<List<AppAnnotationEntity>> list(@PathVariable String appId) {
        requireOwned(appId);
        return ApiResponse.ok(annotationService.list(currentTenantId(), appId));
    }

    @PostMapping
    public ApiResponse<AppAnnotationEntity> create(
            @PathVariable String appId, @RequestBody AppAnnotationEntity annotation) {
        requireOwned(appId);
        return ApiResponse.ok(annotationService.create(currentTenantId(), appId, annotation));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppAnnotationEntity> update(
            @PathVariable String appId, @PathVariable String id, @RequestBody AppAnnotationEntity updates) {
        requireOwned(appId);
        return ApiResponse.ok(annotationService.update(currentTenantId(), appId, id, updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String appId, @PathVariable String id) {
        requireOwned(appId);
        annotationService.delete(currentTenantId(), appId, id);
        return ApiResponse.ok();
    }

    /** Records a manually triggered annotation hit. */
    @PostMapping("/{id}/hit")
    public ApiResponse<Void> hit(@PathVariable String appId, @PathVariable String id) {
        requireOwned(appId);
        annotationService.recordHit(currentTenantId(), appId, id);
        return ApiResponse.ok();
    }

    private void requireOwned(String appId) {
        apps.require(currentTenantId(), appId);
    }
}
