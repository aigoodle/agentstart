package io.aigoodle.web.support;

/** Host-overridable authorization boundary for employee-owned channel accounts. */
public interface ChannelOwnershipPolicy {
    /** Returns the only owner visible to the caller, or {@code null} for an administrator's tenant-wide view. */
    String visibleOwnerId(String requestedOwnerId);

    /** Requires the caller to own the target or hold tenant-wide channel administration permission. */
    void requireAccess(String ownerType, String ownerId);
}
