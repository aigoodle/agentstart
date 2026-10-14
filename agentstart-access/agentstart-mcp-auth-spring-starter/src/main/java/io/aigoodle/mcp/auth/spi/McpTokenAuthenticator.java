package io.aigoodle.mcp.auth.spi;

import io.aigoodle.common.context.CurrentUser;

/** Converts one already-extracted bearer token into a trusted caller. */
@FunctionalInterface
public interface McpTokenAuthenticator {

    CurrentUser authenticate(String token);
}
