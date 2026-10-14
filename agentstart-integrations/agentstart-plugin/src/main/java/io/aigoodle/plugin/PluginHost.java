package io.aigoodle.plugin;

import java.util.Map;

/** Named, authorized host capabilities: internal queries, MCP tools, HTTP or Agent calls. */
@FunctionalInterface
public interface PluginHost {
    Object call(String capability, Map<String, Object> arguments);
}
