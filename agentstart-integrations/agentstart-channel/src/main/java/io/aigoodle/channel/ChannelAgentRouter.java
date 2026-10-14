package io.aigoodle.channel;

/** Resolves a managed channel message without silently falling back to a transport-specific Agent. */
public class ChannelAgentRouter {
    public enum Source {
        CONNECTION,
        EMPLOYEE,
        TENANT_DEFAULT,
        TENANT_FALLBACK,
        NONE
    }

    public enum UnboundPolicy {
        SILENT
    }

    public record Result(
            String connectionId,
            String tenantId,
            String employeeId,
            String agentId,
            String agentVersionId,
            String fallbackAgentId,
            String fallbackAgentVersionId,
            Source source,
            String reason,
            long routingPolicyVersion,
            UnboundPolicy unboundPolicy,
            boolean managed) {
        public Result(
                String connectionId,
                String tenantId,
                String employeeId,
                String agentId,
                String fallbackAgentId,
                Source source,
                UnboundPolicy unboundPolicy,
                boolean managed) {
            this(
                    connectionId,
                    tenantId,
                    employeeId,
                    agentId,
                    null,
                    fallbackAgentId,
                    null,
                    source,
                    source.name().toLowerCase(),
                    1,
                    unboundPolicy,
                    managed);
        }

        public boolean routed() {
            return agentId != null && !agentId.isBlank();
        }
    }

    private final ChannelConnectionService connections;
    private final ChannelAgentBindingService bindings;

    public ChannelAgentRouter(ChannelConnectionService connections, ChannelAgentBindingService bindings) {
        this.connections = connections;
        this.bindings = bindings;
    }

    public Result route(ChannelInboundEvent event) {
        ChannelConnectionService.RoutingConnection connection = event.runtimeNodeId() == null
                ? connections.routingConnection(event.provider(), event.channelId(), event.accountId())
                : connections.routingConnection(
                        event.provider(), event.runtimeNodeId(), event.channelId(), event.accountId());
        if (connection == null || !connection.active()) return none(connection, false);
        ChannelAgentBindingService.TenantBinding tenant = bindings.tenant(connection.tenantId());
        String fallback = tenant != null && tenant.enabled() ? tenant.fallbackAgentId() : null;
        if (present(connection.explicitAgentId()))
            return result(
                    connection,
                    connection.explicitAgentId(),
                    connection.explicitAgentVersionId(),
                    fallback,
                    tenant == null ? null : tenant.fallbackAgentVersionId(),
                    Source.CONNECTION,
                    tenant == null ? 1 : tenant.routingPolicyVersion());
        ChannelAgentBindingService.EmployeeBinding employee =
                bindings.employee(connection.tenantId(), connection.ownerId());
        if (employee != null && employee.enabled() && present(employee.agentId()))
            return result(
                    connection,
                    employee.agentId(),
                    employee.agentVersionId(),
                    fallback,
                    tenant == null ? null : tenant.fallbackAgentVersionId(),
                    Source.EMPLOYEE,
                    employee.routingPolicyVersion());
        if (tenant != null && tenant.enabled() && present(tenant.defaultAgentId()))
            return result(
                    connection,
                    tenant.defaultAgentId(),
                    tenant.defaultAgentVersionId(),
                    fallback,
                    tenant.fallbackAgentVersionId(),
                    Source.TENANT_DEFAULT,
                    tenant.routingPolicyVersion());
        if (present(fallback))
            return result(
                    connection,
                    fallback,
                    tenant.fallbackAgentVersionId(),
                    null,
                    null,
                    Source.TENANT_FALLBACK,
                    tenant.routingPolicyVersion());
        return none(connection, true);
    }

    private static Result result(
            ChannelConnectionService.RoutingConnection c,
            String agentId,
            String agentVersionId,
            String fallback,
            String fallbackVersionId,
            Source source,
            long policyVersion) {
        return new Result(
                c.connectionId(),
                c.tenantId(),
                c.ownerId(),
                agentId,
                agentVersionId,
                fallback,
                fallbackVersionId,
                source,
                reason(source, agentVersionId),
                policyVersion,
                UnboundPolicy.SILENT,
                true);
    }

    private static Result none(ChannelConnectionService.RoutingConnection c, boolean managed) {
        return new Result(
                c == null ? null : c.connectionId(),
                c == null ? null : c.tenantId(),
                c == null ? null : c.ownerId(),
                null,
                null,
                null,
                null,
                Source.NONE,
                c == null ? "connection_not_found" : managed ? "no_enabled_binding" : "connection_inactive",
                1,
                UnboundPolicy.SILENT,
                managed);
    }

    private static String reason(Source source, String versionId) {
        return source.name().toLowerCase() + (present(versionId) ? ":pinned_version" : ":latest_active_version");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
