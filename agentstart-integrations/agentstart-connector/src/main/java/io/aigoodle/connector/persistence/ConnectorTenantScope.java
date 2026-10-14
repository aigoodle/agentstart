package io.aigoodle.connector.persistence;

import io.aigoodle.persistence.TenantSqlScope;
import java.util.function.Supplier;

/** @deprecated Use {@link TenantSqlScope}; retained for source compatibility. */
@Deprecated(forRemoval = false)
public final class ConnectorTenantScope {
    private ConnectorTenantScope() {}

    public static boolean isBypassed() {
        return TenantSqlScope.isBypassed();
    }

    public static <T> T bypass(Supplier<T> action) {
        return TenantSqlScope.bypass(action);
    }

    public static void bypass(Runnable action) {
        TenantSqlScope.bypass(action);
    }
}
