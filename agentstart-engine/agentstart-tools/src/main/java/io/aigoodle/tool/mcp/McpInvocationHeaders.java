package io.aigoodle.tool.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;

/** Per-invocation HTTP headers propagated to an MCP transport without shared user state. */
public final class McpInvocationHeaders {
    static final String AUTHORIZATION = "authorization";
    private static final ThreadLocal<String> AUTHORIZATION_HOLDER = new ThreadLocal<>();

    private McpInvocationHeaders() {}

    public static Scope withAuthorization(Object authorization) {
        String previous = AUTHORIZATION_HOLDER.get();
        String value = authorization == null ? null : authorization.toString().trim();
        if (value == null || value.isEmpty()) AUTHORIZATION_HOLDER.remove();
        else AUTHORIZATION_HOLDER.set(value);
        return () -> {
            if (previous == null) AUTHORIZATION_HOLDER.remove();
            else AUTHORIZATION_HOLDER.set(previous);
        };
    }

    public static String currentAuthorization() {
        return AUTHORIZATION_HOLDER.get();
    }

    static McpTransportContext transportContext() {
        String authorization = AUTHORIZATION_HOLDER.get();
        return authorization == null
                ? McpTransportContext.EMPTY
                : McpTransportContext.create(Map.of(AUTHORIZATION, authorization));
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
