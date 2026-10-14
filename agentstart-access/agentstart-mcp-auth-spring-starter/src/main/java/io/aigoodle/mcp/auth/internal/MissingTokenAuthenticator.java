package io.aigoodle.mcp.auth.internal;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.mcp.auth.exception.McpAuthenticationException;
import io.aigoodle.mcp.auth.spi.McpTokenAuthenticator;

public final class MissingTokenAuthenticator implements McpTokenAuthenticator {

    @Override
    public CurrentUser authenticate(String token) {
        throw new McpAuthenticationException(
                "MCP authentication is not configured: publish an McpTokenAuthenticator bean or enable spring-agent.mcp.auth.jwt");
    }
}
