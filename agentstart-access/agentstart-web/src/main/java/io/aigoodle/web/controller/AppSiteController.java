package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppSiteEntity;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.agent.service.AppSiteService;
import io.aigoodle.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Published-widget config for an app (Dify parity — the "网站" panel). One
 * row per app; auto-generated on first save. GET returns a fresh defaulted
 * row when nothing has been saved yet.
 */
@RestController
@ConditionalOnBean(AppSiteService.class)
@RequestMapping("/apps/{appId}/site")
public class AppSiteController {

    private final AppSiteService service;
    private final AppService apps;

    public AppSiteController(AppSiteService service, AppService apps) {
        this.service = service;
        this.apps = apps;
    }

    @GetMapping
    public ApiResponse<AppSiteEntity> get(@PathVariable String appId) {
        apps.require(currentTenantId(), appId);
        return ApiResponse.ok(service.getByApp(currentTenantId(), appId));
    }

    @PutMapping
    public ApiResponse<AppSiteEntity> save(@PathVariable String appId, @RequestBody AppSiteEntity body) {
        apps.require(currentTenantId(), appId);
        return ApiResponse.ok(service.save(currentTenantId(), appId, body));
    }
}
