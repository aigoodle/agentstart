package io.aigoodle.web.controller;

import io.aigoodle.agent.service.AppPermissionService;
import io.aigoodle.agent.service.AppPermissionSettings;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.common.context.UserContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnBean(AppPermissionService.class)
@RequestMapping({"/apps/{appId}/permissions", "/agents/{appId}/permissions"})
public class AppPermissionController {
    private final AppPermissionService permissions;

    public AppPermissionController(AppPermissionService permissions) {
        this.permissions = permissions;
    }

    @GetMapping
    public ApiResponse<AppPermissionSettings> get(@PathVariable String appId) {
        return ApiResponse.ok(permissions.get(UserContextHolder.currentTenantId(), appId));
    }

    @PutMapping
    public ApiResponse<AppPermissionSettings> replace(
            @PathVariable String appId, @RequestBody AppPermissionSettings settings) {
        return ApiResponse.ok(permissions.replace(UserContextHolder.currentTenantId(), appId, settings));
    }
}
