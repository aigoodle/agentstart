package io.aigoodle.plugin;

/** A business capability. Implement as a Spring bean in an optional Maven starter. */
public interface Plugin {
    PluginManifest manifest();

    PluginResult execute(PluginInvocation invocation, PluginContext context);
}
