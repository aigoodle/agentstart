package io.aigoodle.completion.support;

/** Trusted result returned by a host-provided chat access policy. */
public record ChatAccessContext(String appId, String tenantId, String userId, ChatAccessMode mode) {}
