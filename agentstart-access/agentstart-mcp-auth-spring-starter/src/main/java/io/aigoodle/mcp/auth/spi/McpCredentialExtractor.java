package io.aigoodle.mcp.auth.spi;

import io.aigoodle.mcp.auth.support.McpCredential;
import java.util.Optional;

/** Finds the per-request HTTP credential in MCP tool invocation arguments. */
@FunctionalInterface
public interface McpCredentialExtractor {

    Optional<McpCredential> extract(Object[] invocationArguments);
}
