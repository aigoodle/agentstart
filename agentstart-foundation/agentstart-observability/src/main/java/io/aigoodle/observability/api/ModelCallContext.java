package io.aigoodle.observability.api;

/** Identifies the provider, model, and tenant associated with one model call. */
public record ModelCallContext(String provider, String model, String tenantId) {

    public static ModelCallContext of(String provider, String model) {
        return new ModelCallContext(provider, model, null);
    }
}
