package io.aigoodle.mcp.auth.support;

import java.util.Map;
import java.util.Optional;

/** Request-scoped credential access for calls forwarded by a synchronous MCP tool. */
public final class McpCredentialContext {

    private static final ThreadLocal<McpCredential> HOLDER = new ThreadLocal<>();

    private McpCredentialContext() {}

    public static Optional<McpCredential> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static String currentAuthorization() {
        McpCredential credential = HOLDER.get();
        return credential == null ? null : credential.headerValue();
    }

    /** Adds the inbound value to downstream headers without logging or reformatting it. */
    public static void forwardTo(Map<String, String> headers, String headerName) {
        if (headers == null) throw new IllegalArgumentException("headers must not be null");
        String authorization = currentAuthorization();
        if (authorization != null) {
            headers.put(headerName == null || headerName.isBlank() ? "Authorization" : headerName, authorization);
        }
    }

    public static Scope open(McpCredential credential) {
        McpCredential previous = HOLDER.get();
        if (credential == null) HOLDER.remove();
        else HOLDER.set(credential);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final McpCredential previous;
        private boolean closed;

        private Scope(McpCredential previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                if (previous == null) HOLDER.remove();
                else HOLDER.set(previous);
                closed = true;
            }
        }
    }
}
