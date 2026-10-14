package io.aigoodle.agent.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppMode;
import java.util.function.Consumer;

/** Applies catalog-facing request fields to an application's persisted catalog row. */
final class AppCatalogUpdater {

    private static final String DEFAULT_STATUS = "normal";
    private static final String DEFAULT_VISIBILITY = "PRIVATE";

    void applyRequest(SaveAppRequest request, AppEntity agent) {
        applyWhenPresent(request.getName(), agent::setName);
        applyWhenPresent(request.getAppCode(), value -> agent.setAppCode(normalizeCode(value)));
        applyWhenPresent(request.getDescription(), agent::setDescription);
        applyWhenPresent(request.getIcon(), agent::setIcon);
        applyWhenPresent(request.getIconBackground(), agent::setIconBackground);
        applyWhenPresent(request.getIconType(), agent::setIconType);
        applyWhenPresent(request.getUseIconAsAnswerIcon(), agent::setUseIconAsAnswerIcon);
        applyWhenPresent(request.getIsPublic(), agent::setIsPublic);
        applyWhenPresent(request.getEnableSite(), agent::setEnableSite);
        applyWhenPresent(request.getEnableApi(), agent::setEnableApi);
        applyWhenPresent(request.getApiRpm(), agent::setApiRpm);
        applyWhenPresent(request.getApiRph(), agent::setApiRph);
        applyWhenPresent(request.getModelName(), agent::setModelName);
        applyWhenPresent(request.getModelProvider(), agent::setModelProvider);

        agent.setMode(
                AppMode.from(requestedOrCurrentOrDefault(request.getMode(), agent.getMode(), AppMode.AGENT.value()))
                        .value());
        agent.setStatus(requestedOrCurrentOrDefault(request.getStatus(), agent.getStatus(), DEFAULT_STATUS));
        agent.setVisibility(requestedOrCurrentOrDefault(
                normalizeVisibility(request.getVisibility()), agent.getVisibility(), DEFAULT_VISIBILITY));
        agent.setPublished(request.isPublished());
    }

    private static String normalizeCode(String value) {
        String normalized = value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized != null && !normalized.matches("[a-z0-9][a-z0-9_-]{1,99}")) {
            throw new IllegalArgumentException("appCode must contain 2-100 lowercase letters, digits, '_' or '-'");
        }
        return normalized;
    }

    private static String normalizeVisibility(String value) {
        if (!hasText(value)) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PRIVATE", "TENANT_LIST", "GLOBAL").contains(normalized)) {
            throw new IllegalArgumentException("visibility must be PRIVATE, TENANT_LIST or GLOBAL");
        }
        return normalized;
    }

    private static String requestedOrCurrentOrDefault(String requestedValue, String currentValue, String defaultValue) {
        if (hasText(requestedValue)) {
            return requestedValue;
        }
        return hasText(currentValue) ? currentValue : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> void applyWhenPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
