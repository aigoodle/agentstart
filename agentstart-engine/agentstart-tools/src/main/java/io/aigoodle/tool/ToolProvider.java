package io.aigoodle.tool;

import java.util.List;

/**
 * Contributes a dynamic set of {@link ToolDefinition}s — e.g. tools fetched from an MCP
 * server, an OpenAPI spec, or a plugin jar. Published as a Spring bean and merged into
 * the {@link ToolRegistry} alongside statically declared {@code ToolDefinition} beans.
 */
public interface ToolProvider {

    List<ToolDefinition> getTools();
}
