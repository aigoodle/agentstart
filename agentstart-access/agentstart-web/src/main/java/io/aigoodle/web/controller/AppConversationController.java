package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppConversationService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat-session (conversation) CRUD for the console sidebar — list / rename /
 * pin / delete. The chat runtime itself does not go through this controller;
 * it drops rows into the same table via {@link AppConversationService#ensure}.
 */
@RestController
@ConditionalOnBean(AppConversationService.class)
@RequestMapping("/apps/{appId}/conversations")
public class AppConversationController {

    private final AppConversationService service;

    public AppConversationController(AppConversationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AppConversationEntity>> list(@PathVariable String appId) {
        return ApiResponse.ok(service.listByApp(currentTenantId(), appId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppConversationEntity> get(@PathVariable String appId, @PathVariable String id) {
        return ApiResponse.ok(service.require(currentTenantId(), appId, id));
    }

    @PostMapping("/{id}/rename")
    public ApiResponse<AppConversationEntity> rename(
            @PathVariable String appId, @PathVariable String id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.rename(currentTenantId(), appId, id, body.get("name")));
    }

    @PostMapping("/{id}/pin")
    public ApiResponse<AppConversationEntity> pin(
            @PathVariable String appId, @PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return ApiResponse.ok(
                service.togglePinned(currentTenantId(), appId, id, Boolean.TRUE.equals(body.get("pinned"))));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String appId, @PathVariable String id) {
        service.delete(currentTenantId(), appId, id);
        return ApiResponse.ok();
    }
}
