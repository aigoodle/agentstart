package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppAnnotationSettingEntity;
import io.aigoodle.agent.service.AppAnnotationSettingService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.api.ApiResponse;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for an application's annotation-retrieval settings. */
@RestController
@ConditionalOnBean(AppAnnotationSettingService.class)
@RequestMapping("/apps/{appId}/annotation-settings")
public class AppAnnotationSettingController {

    private final AppAnnotationSettingService settingService;
    private final AppService apps;

    public AppAnnotationSettingController(AppAnnotationSettingService settingService, AppService apps) {
        this.settingService = settingService;
        this.apps = apps;
    }

    @GetMapping
    public ApiResponse<AppAnnotationSettingEntity> get(@PathVariable String appId) {
        requireOwned(appId);
        return ApiResponse.ok(settingService.getByApp(currentTenantId(), appId));
    }

    @PutMapping
    public ApiResponse<AppAnnotationSettingEntity> save(
            @PathVariable String appId, @RequestBody AppAnnotationSettingEntity updates) {
        requireOwned(appId);
        return ApiResponse.ok(settingService.save(currentTenantId(), appId, updates));
    }

    @PostMapping("/status")
    public ApiResponse<AppAnnotationSettingEntity> setStatus(
            @PathVariable String appId, @RequestBody Map<String, Boolean> status) {
        requireOwned(appId);
        boolean enabled = Boolean.TRUE.equals(status.get("enabled"));
        return ApiResponse.ok(settingService.setEnabled(currentTenantId(), appId, enabled));
    }

    private void requireOwned(String appId) {
        apps.require(currentTenantId(), appId);
    }
}
