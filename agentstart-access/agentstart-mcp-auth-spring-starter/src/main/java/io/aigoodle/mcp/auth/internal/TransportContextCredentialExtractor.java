package io.aigoodle.mcp.auth.internal;

import io.aigoodle.mcp.auth.exception.McpAuthenticationException;
import io.aigoodle.mcp.auth.spi.McpCredentialExtractor;
import io.aigoodle.mcp.auth.support.McpCredential;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Spring-AI-version-neutral extraction from Mcp*RequestContext.transportContext(). */
public final class TransportContextCredentialExtractor implements McpCredentialExtractor {

    private final List<String> contextKeys;

    public TransportContextCredentialExtractor(List<String> contextKeys) {
        this.contextKeys = contextKeys == null || contextKeys.isEmpty()
                ? List.of("authorization", "Authorization")
                : List.copyOf(contextKeys);
    }

    @Override
    public Optional<McpCredential> extract(Object[] invocationArguments) {
        if (invocationArguments == null) return Optional.empty();
        for (Object argument : invocationArguments) {
            Object context = transportContext(argument);
            if (context == null) continue;
            for (String key : contextKeys) {
                Object value = value(context, key);
                if (value != null && !value.toString().isBlank()) {
                    return Optional.of(McpCredential.fromAuthorization(value.toString()));
                }
            }
        }
        return Optional.empty();
    }

    private Object transportContext(Object argument) {
        if (argument == null) return null;
        try {
            Method method = argument.getClass().getMethod("transportContext");
            return method.invoke(argument);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new McpAuthenticationException("Cannot read MCP transport context", exception);
        }
    }

    private Object value(Object context, String key) {
        if (context instanceof Map<?, ?> map) return map.get(key);
        try {
            Method getter = context.getClass().getMethod("get", String.class);
            return getter.invoke(context, key);
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new McpAuthenticationException("Cannot read credential from MCP transport context", exception);
        }
    }
}
