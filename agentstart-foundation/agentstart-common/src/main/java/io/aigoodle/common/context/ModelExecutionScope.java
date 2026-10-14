package io.aigoodle.common.context;

/** Trusted runtime attribution for model calls; independent of resource-tenant lookups. */
public final class ModelExecutionScope implements AutoCloseable {
    public record Attribution(String tenantId, String appId) {}

    private static final ThreadLocal<Attribution> CURRENT = new ThreadLocal<>();
    private final Attribution previous;

    private ModelExecutionScope(String tenantId, String appId) {
        previous = CURRENT.get();
        CURRENT.set(new Attribution(tenantId, appId));
    }

    public static ModelExecutionScope open(String tenantId, String appId) {
        return new ModelExecutionScope(tenantId, appId);
    }

    public static Attribution current() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
