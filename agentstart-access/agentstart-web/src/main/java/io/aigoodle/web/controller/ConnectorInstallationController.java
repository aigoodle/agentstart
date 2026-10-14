package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.connector.installation.ConnectorInstallationService;
import io.aigoodle.connector.installation.ConnectorInstallationService.InstallationView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(ConnectorInstallationService.class)
@RequestMapping("/connector-installations")
public class ConnectorInstallationController {
    private final ConnectorInstallationService installations;

    public ConnectorInstallationController(ConnectorInstallationService installations) {
        this.installations = installations;
    }

    @GetMapping
    public ApiResponse<List<InstallationView>> list() {
        return ApiResponse.ok(installations.list(currentTenantId()));
    }

    @PostMapping("/synchronize")
    public ApiResponse<List<InstallationView>> synchronize() {
        return ApiResponse.ok(installations.synchronize(currentTenantId()));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<InstallationView> enable(@PathVariable String id) {
        return ApiResponse.ok(installations.setEnabled(id, currentTenantId(), true));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<InstallationView> disable(@PathVariable String id) {
        return ApiResponse.ok(installations.setEnabled(id, currentTenantId(), false));
    }
}
