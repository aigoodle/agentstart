package io.aigoodle.persistence;

import java.util.function.Supplier;

/**
 * Explicit escape hatch for reviewed cross-tenant discovery/maintenance and authentication
 * bootstrap lookups where the tenant is not known until an opaque credential is resolved.
 * Never use it to bypass an already-authenticated caller's tenant boundary.
 */
public final class TenantSqlScope {
    private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);

    private TenantSqlScope() {}

    public static boolean isBypassed() {
        return BYPASS_DEPTH.get() > 0;
    }

    public static <T> T bypass(Supplier<T> action) {
        BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = BYPASS_DEPTH.get() - 1;
            if (depth <= 0) BYPASS_DEPTH.remove();
            else BYPASS_DEPTH.set(depth);
        }
    }

    public static void bypass(Runnable action) {
        bypass(() -> {
            action.run();
            return null;
        });
    }
}
