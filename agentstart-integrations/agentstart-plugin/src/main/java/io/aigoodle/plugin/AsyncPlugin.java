package io.aigoodle.plugin;

/** execute must deduplicate submissions by context.identity.executionId for declared idempotent actions. */
public interface AsyncPlugin extends Plugin {
    PluginResult query(PluginInvocation invocation, PluginTask task, PluginContext context);

    default PluginResult cancel(PluginInvocation invocation, PluginTask task, PluginContext context) {
        return PluginResult.failure("plugin_cancel_unsupported", "Remote task cancellation is not supported", false);
    }
}
