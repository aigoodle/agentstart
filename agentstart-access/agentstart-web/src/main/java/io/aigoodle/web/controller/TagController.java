package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.TagBindingEntity;
import io.aigoodle.agent.entity.TagEntity;
import io.aigoodle.agent.service.TagService;
import io.aigoodle.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped organisational tags for apps + datasets (Dify parity — the
 * sidebar filter chips). One tag row + many bindings.
 */
@RestController
@ConditionalOnBean(TagService.class)
@RequestMapping("/tags")
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TagEntity>> list(@RequestParam(required = false) String type) {
        return ApiResponse.ok(service.list(currentTenantId(), type));
    }

    @PostMapping
    public ApiResponse<TagEntity> create(@RequestBody TagEntity body) {
        body.setTenantId(currentTenantId());
        return ApiResponse.ok(service.create(body));
    }

    @PostMapping("/{id}/rename")
    public ApiResponse<TagEntity> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.rename(currentTenantId(), id, body.get("name")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    /** List bindings for a specific target (app id or dataset id). */
    @GetMapping("/bindings")
    public ApiResponse<List<TagBindingEntity>> bindings(
            @RequestParam String targetId, @RequestParam(required = false) String targetType) {
        return ApiResponse.ok(service.bindings(currentTenantId(), targetId, targetType));
    }

    @PostMapping("/bindings")
    public ApiResponse<Void> bind(@RequestBody Map<String, String> body) {
        service.bind(
                currentTenantId(), body.get("tagId"), body.get("targetId"), body.getOrDefault("targetType", "app"));
        return ApiResponse.ok();
    }

    @DeleteMapping("/bindings")
    public ApiResponse<Void> unbind(@RequestParam String tagId, @RequestParam String targetId) {
        service.unbind(currentTenantId(), tagId, targetId);
        return ApiResponse.ok();
    }
}
