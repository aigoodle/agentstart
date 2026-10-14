package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppApiTokenEntity;
import io.aigoodle.agent.service.AppApiTokenService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.api.ApiResponse;
import java.time.LocalDateTime;
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
 * Manage the per-app API tokens shown on the "API 访问" tab. Values are minted
 * server-side and returned once — subsequent list/get calls show the full
 * value again because this is an internal console (matches Dify's UX).
 */
@RestController
@ConditionalOnBean(AppApiTokenService.class)
@RequestMapping("/apps/{appId}/api-tokens")
public class AppApiTokenController {

    public record TokenView(
            String id,
            String appId,
            String name,
            String type,
            String token,
            LocalDateTime createdAt,
            LocalDateTime lastUsedAt) {
        static TokenView of(AppApiTokenEntity row, boolean reveal) {
            String value = row.getToken();
            String visible = reveal ? value : value == null ? null : "app-..." + AppApiTokenService.tokenHint(value);
            return new TokenView(
                    row.getId(),
                    row.getAppId(),
                    row.getName(),
                    row.getType(),
                    visible,
                    row.getCreatedAt(),
                    row.getLastUsedAt());
        }
    }

    private final AppApiTokenService service;
    private final AppService apps;

    public AppApiTokenController(AppApiTokenService service, AppService apps) {
        this.service = service;
        this.apps = apps;
    }

    @GetMapping
    public ApiResponse<List<TokenView>> list(@PathVariable String appId) {
        apps.requireWritable(currentTenantId(), appId);
        return ApiResponse.ok(service.listByApp(currentTenantId(), appId).stream()
                .map(row -> TokenView.of(row, false))
                .toList());
    }

    @PostMapping
    public ApiResponse<TokenView> create(
            @PathVariable String appId, @RequestBody(required = false) Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        String type = body == null ? null : body.get("type");
        apps.requireWritable(currentTenantId(), appId);
        return ApiResponse.ok(TokenView.of(service.create(appId, currentTenantId(), name, type), true));
    }

    @PostMapping("/{id}/rename")
    public ApiResponse<TokenView> rename(
            @PathVariable String appId, @PathVariable String id, @RequestBody Map<String, String> body) {
        apps.requireWritable(currentTenantId(), appId);
        return ApiResponse.ok(TokenView.of(service.rename(currentTenantId(), appId, id, body.get("name")), false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String appId, @PathVariable String id) {
        apps.requireWritable(currentTenantId(), appId);
        service.delete(currentTenantId(), appId, id);
        return ApiResponse.ok();
    }
}
