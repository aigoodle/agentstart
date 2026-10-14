package io.aigoodle.web.support;

/**
 * Host-overridable boundary for deployment-scoped runtime accounts.
 * Unlike tenant channel administration, these operations can affect accounts owned by every tenant.
 */
public interface ChannelRuntimeAdministrationPolicy {
    void requireRuntimeAdministrator();
}
