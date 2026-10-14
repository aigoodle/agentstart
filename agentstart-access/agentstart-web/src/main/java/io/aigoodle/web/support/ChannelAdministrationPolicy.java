package io.aigoodle.web.support;

/** Host-overridable authorization boundary for tenant-wide channel administration. */
public interface ChannelAdministrationPolicy {
    void requireAdministrator();
}
