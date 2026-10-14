package io.aigoodle.tool;

import java.util.Map;

/** Optional UI/catalog metadata without coupling the web module to a tool provider. */
public interface ToolMetadata {
    default Map<String, Object> metadata() {
        return Map.of();
    }
}
