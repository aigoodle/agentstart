package io.aigoodle.agent.service;

import io.aigoodle.agent.entity.AppModelConfigEntity;

/** Identifies the app and tenant that own a model configuration sidecar. */
public record AppModelConfigRegistration(String appId, String tenantId, AppModelConfigEntity configuration) {

    public AppModelConfigRegistration {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
    }
}
