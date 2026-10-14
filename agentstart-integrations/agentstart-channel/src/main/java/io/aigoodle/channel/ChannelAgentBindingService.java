package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.channel.persistence.EmployeeAgentBindingEntity;
import io.aigoodle.channel.persistence.EmployeeAgentBindingMapper;
import io.aigoodle.channel.persistence.TenantAgentBindingEntity;
import io.aigoodle.channel.persistence.TenantAgentBindingMapper;

/** Tenant and employee Agent defaults are independent from any individual channel. */
public class ChannelAgentBindingService {
    public record TenantBinding(
            String id,
            String tenantId,
            String defaultAgentId,
            String defaultAgentVersionId,
            String fallbackAgentId,
            String fallbackAgentVersionId,
            long routingPolicyVersion,
            boolean enabled) {
        public TenantBinding(
                String id, String tenantId, String defaultAgentId, String fallbackAgentId, boolean enabled) {
            this(id, tenantId, defaultAgentId, null, fallbackAgentId, null, 1, enabled);
        }
    }

    public record EmployeeBinding(
            String id,
            String tenantId,
            String employeeId,
            String agentId,
            String agentVersionId,
            long routingPolicyVersion,
            boolean enabled) {
        public EmployeeBinding(String id, String tenantId, String employeeId, String agentId, boolean enabled) {
            this(id, tenantId, employeeId, agentId, null, 1, enabled);
        }
    }

    private final TenantAgentBindingMapper tenants;
    private final EmployeeAgentBindingMapper employees;

    public ChannelAgentBindingService(TenantAgentBindingMapper tenants, EmployeeAgentBindingMapper employees) {
        this.tenants = tenants;
        this.employees = employees;
    }

    public TenantBinding saveTenant(String tenantId, String defaultAgentId, String fallbackAgentId, boolean enabled) {
        return saveTenant(tenantId, defaultAgentId, null, fallbackAgentId, null, enabled);
    }

    public TenantBinding saveTenant(
            String tenantId,
            String defaultAgentId,
            String defaultAgentVersionId,
            String fallbackAgentId,
            String fallbackAgentVersionId,
            boolean enabled) {
        TenantAgentBindingEntity entity = tenantEntity(tenantId);
        if (entity == null) {
            entity = new TenantAgentBindingEntity();
            entity.setTenantId(required(tenantId, "tenantId"));
        }
        entity.setDefaultAgentId(required(defaultAgentId, "defaultAgentId"));
        entity.setDefaultAgentVersionId(trim(defaultAgentVersionId));
        entity.setFallbackAgentId(trim(fallbackAgentId));
        entity.setFallbackAgentVersionId(trim(fallbackAgentVersionId));
        entity.setRoutingPolicyVersion(
                entity.getRoutingPolicyVersion() == null ? 1 : entity.getRoutingPolicyVersion() + 1);
        entity.setEnabled(enabled);
        if (entity.getId() == null) tenants.insert(entity);
        else
            tenants.update(
                    entity,
                    new LambdaUpdateWrapper<TenantAgentBindingEntity>()
                            .eq(TenantAgentBindingEntity::getTenantId, entity.getTenantId())
                            .eq(TenantAgentBindingEntity::getId, entity.getId()));
        return tenantView(entity);
    }

    public TenantBinding tenant(String tenantId) {
        TenantAgentBindingEntity entity = tenantEntity(tenantId);
        return entity == null ? null : tenantView(entity);
    }

    public EmployeeBinding saveEmployee(String tenantId, String employeeId, String agentId, boolean enabled) {
        return saveEmployee(tenantId, employeeId, agentId, null, enabled);
    }

    public EmployeeBinding saveEmployee(
            String tenantId, String employeeId, String agentId, String agentVersionId, boolean enabled) {
        EmployeeAgentBindingEntity entity = employeeEntity(tenantId, employeeId);
        if (entity == null) {
            entity = new EmployeeAgentBindingEntity();
            entity.setTenantId(required(tenantId, "tenantId"));
            entity.setEmployeeId(required(employeeId, "employeeId"));
        }
        entity.setAgentId(required(agentId, "agentId"));
        entity.setAgentVersionId(trim(agentVersionId));
        entity.setRoutingPolicyVersion(
                entity.getRoutingPolicyVersion() == null ? 1 : entity.getRoutingPolicyVersion() + 1);
        entity.setEnabled(enabled);
        if (entity.getId() == null) employees.insert(entity);
        else
            employees.update(
                    entity,
                    new LambdaUpdateWrapper<EmployeeAgentBindingEntity>()
                            .eq(EmployeeAgentBindingEntity::getTenantId, entity.getTenantId())
                            .eq(EmployeeAgentBindingEntity::getId, entity.getId()));
        return employeeView(entity);
    }

    public EmployeeBinding employee(String tenantId, String employeeId) {
        EmployeeAgentBindingEntity entity = employeeEntity(tenantId, employeeId);
        return entity == null ? null : employeeView(entity);
    }

    public void deleteEmployee(String tenantId, String employeeId) {
        EmployeeAgentBindingEntity entity = employeeEntity(tenantId, employeeId);
        if (entity != null)
            employees.delete(new LambdaQueryWrapper<EmployeeAgentBindingEntity>()
                    .eq(EmployeeAgentBindingEntity::getTenantId, entity.getTenantId())
                    .eq(EmployeeAgentBindingEntity::getId, entity.getId()));
    }

    private TenantAgentBindingEntity tenantEntity(String tenantId) {
        return tenants.selectOne(new LambdaQueryWrapper<TenantAgentBindingEntity>()
                .eq(TenantAgentBindingEntity::getTenantId, required(tenantId, "tenantId"))
                .last("LIMIT 1"));
    }

    private EmployeeAgentBindingEntity employeeEntity(String tenantId, String employeeId) {
        return employees.selectOne(new LambdaQueryWrapper<EmployeeAgentBindingEntity>()
                .eq(EmployeeAgentBindingEntity::getTenantId, required(tenantId, "tenantId"))
                .eq(EmployeeAgentBindingEntity::getEmployeeId, required(employeeId, "employeeId"))
                .last("LIMIT 1"));
    }

    private static TenantBinding tenantView(TenantAgentBindingEntity e) {
        return new TenantBinding(
                e.getId(),
                e.getTenantId(),
                e.getDefaultAgentId(),
                e.getDefaultAgentVersionId(),
                e.getFallbackAgentId(),
                e.getFallbackAgentVersionId(),
                e.getRoutingPolicyVersion() == null ? 1 : e.getRoutingPolicyVersion(),
                Boolean.TRUE.equals(e.getEnabled()));
    }

    private static EmployeeBinding employeeView(EmployeeAgentBindingEntity e) {
        return new EmployeeBinding(
                e.getId(),
                e.getTenantId(),
                e.getEmployeeId(),
                e.getAgentId(),
                e.getAgentVersionId(),
                e.getRoutingPolicyVersion() == null ? 1 : e.getRoutingPolicyVersion(),
                Boolean.TRUE.equals(e.getEnabled()));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
