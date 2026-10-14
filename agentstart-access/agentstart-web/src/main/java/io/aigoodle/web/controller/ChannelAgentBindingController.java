package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.service.AgentVersionService;
import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAgentBindingService;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Tenant and employee defaults shared by every message channel. */
@RestController
@ConditionalOnBean(ChannelAgentBindingService.class)
public class ChannelAgentBindingController {
    public record TenantRequest(
            String defaultAgentId,
            String defaultAgentVersionId,
            String fallbackAgentId,
            String fallbackAgentVersionId,
            Boolean enabled) {}

    public record EmployeeRequest(String agentId, String agentVersionId, Boolean enabled) {}

    private final ChannelAgentBindingService bindings;
    private final ChannelAdministrationPolicy administration;
    private final ChannelAuditService audits;
    private final AgentVersionService versions;

    public ChannelAgentBindingController(
            ChannelAgentBindingService bindings,
            ChannelAdministrationPolicy administration,
            ChannelAuditService audits) {
        this(bindings, administration, audits, null);
    }

    @Autowired
    public ChannelAgentBindingController(
            ChannelAgentBindingService bindings,
            ChannelAdministrationPolicy administration,
            ChannelAuditService audits,
            AgentVersionService versions) {
        this.bindings = bindings;
        this.administration = administration;
        this.audits = audits;
        this.versions = versions;
    }

    /** Preferred embedded-SDK endpoint: tenant identity comes only from the trusted host context. */
    @GetMapping("/tenant-agent-bindings/current")
    public ApiResponse<ChannelAgentBindingService.TenantBinding> currentTenant() {
        return ApiResponse.ok(bindings.tenant(currentTenantId()));
    }
    /** Preferred embedded-SDK endpoint: no caller-selected tenant appears in the URL or body. */
    @PutMapping("/tenant-agent-bindings/current")
    @Transactional
    public ApiResponse<ChannelAgentBindingService.TenantBinding> saveCurrentTenant(@RequestBody TenantRequest request) {
        administration.requireAdministrator();
        validate(request.defaultAgentId(), request.defaultAgentVersionId());
        validate(request.fallbackAgentId(), request.fallbackAgentVersionId());
        ChannelAgentBindingService.TenantBinding saved = bindings.saveTenant(
                currentTenantId(),
                request.defaultAgentId(),
                request.defaultAgentVersionId(),
                request.fallbackAgentId(),
                request.fallbackAgentVersionId(),
                !Boolean.FALSE.equals(request.enabled()));
        audits.success(
                "TENANT_AGENT_BINDING_SAVE",
                "TENANT_AGENT_BINDING",
                currentTenantId(),
                bindingDetails(
                        saved.defaultAgentId(),
                        saved.defaultAgentVersionId(),
                        saved.fallbackAgentId(),
                        saved.fallbackAgentVersionId(),
                        saved.routingPolicyVersion()));
        return ApiResponse.ok(saved);
    }

    @GetMapping("/employee-agent-bindings/{employeeId}")
    public ApiResponse<ChannelAgentBindingService.EmployeeBinding> employee(@PathVariable String employeeId) {
        return ApiResponse.ok(bindings.employee(currentTenantId(), employeeId));
    }

    @PutMapping("/employee-agent-bindings/{employeeId}")
    @Transactional
    public ApiResponse<ChannelAgentBindingService.EmployeeBinding> saveEmployee(
            @PathVariable String employeeId, @RequestBody EmployeeRequest request) {
        administration.requireAdministrator();
        validate(request.agentId(), request.agentVersionId());
        ChannelAgentBindingService.EmployeeBinding saved = bindings.saveEmployee(
                currentTenantId(),
                employeeId,
                request.agentId(),
                request.agentVersionId(),
                !Boolean.FALSE.equals(request.enabled()));
        audits.success(
                "EMPLOYEE_AGENT_BINDING_SAVE",
                "EMPLOYEE_AGENT_BINDING",
                employeeId,
                bindingDetails(saved.agentId(), saved.agentVersionId(), null, null, saved.routingPolicyVersion()));
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/employee-agent-bindings/{employeeId}")
    @Transactional
    public ApiResponse<Void> deleteEmployee(@PathVariable String employeeId) {
        administration.requireAdministrator();
        bindings.deleteEmployee(currentTenantId(), employeeId);
        audits.success("EMPLOYEE_AGENT_BINDING_DELETE", "EMPLOYEE_AGENT_BINDING", employeeId, Map.of());
        return ApiResponse.ok(null);
    }

    private static Map<String, Object> bindingDetails(
            String agentId, String versionId, String fallbackAgentId, String fallbackVersionId, long policyVersion) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (agentId != null) details.put("agentId", agentId);
        if (versionId != null) details.put("agentVersionId", versionId);
        if (fallbackAgentId != null) details.put("fallbackAgentId", fallbackAgentId);
        if (fallbackVersionId != null) details.put("fallbackAgentVersionId", fallbackVersionId);
        details.put("routingPolicyVersion", policyVersion);
        return details;
    }

    private void validate(String agentId, String versionId) {
        String id = text(agentId);
        String pinned = text(versionId);
        if (id == null) {
            if (pinned != null) throw new IllegalArgumentException("agentId is required with agentVersionId");
            return;
        }
        if (versions == null) throw new IllegalStateException("Agent version service is unavailable");
        versions.requireRunnable(currentTenantId(), id, pinned);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
