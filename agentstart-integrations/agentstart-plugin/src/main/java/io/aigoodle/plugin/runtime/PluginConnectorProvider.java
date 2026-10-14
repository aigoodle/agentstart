package io.aigoodle.plugin.runtime;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.*;
import io.aigoodle.connector.execution.*;
import io.aigoodle.connector.provider.ConnectorProvider;
import io.aigoodle.plugin.*;
import io.aigoodle.plugin.host.PluginHostFactory;
import io.aigoodle.plugin.remote.RemotePlugin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects both runtime modes into existing installation, workflow and Agent tooling. */
public class PluginConnectorProvider implements ConnectorProvider {
    private record Registered(Plugin implementation, PluginManifest manifest, PluginSchemas schemas) {}

    private final Map<String, Registered> plugins = new LinkedHashMap<>();
    private final PluginConnectionResolver connections;
    private final PluginHostFactory hosts;

    public PluginConnectorProvider(
            List<Plugin> plugins, PluginConnectionResolver connections, PluginHostFactory hosts) {
        for (Plugin plugin : plugins) {
            PluginManifest manifest = plugin.manifest();
            for (var action : manifest.actions()) {
                if ("ASYNC".equals(action.metadata().get("executionMode"))
                        && (!(plugin instanceof AsyncPlugin) || !action.idempotent()))
                    throw new IllegalArgumentException(
                            "Async actions require AsyncPlugin and idempotent submission: " + action.id());
            }
            if (this.plugins.putIfAbsent(manifest.id(), new Registered(plugin, manifest, new PluginSchemas(manifest)))
                    != null) throw new IllegalArgumentException("Duplicate plugin: " + manifest.id());
        }
        this.connections = connections;
        this.hosts = hosts;
    }

    @Override
    public String type() {
        return "plugin";
    }

    public List<PluginManifest> manifests() {
        return plugins.values().stream().map(Registered::manifest).toList();
    }

    @Override
    public List<ConnectorDefinition> discover() {
        return plugins.values().stream()
                .map(registered -> {
                    var manifest = registered.manifest();
                    boolean remote = registered.implementation() instanceof RemotePlugin;
                    return new ConnectorDefinition(
                            new ConnectorKey(type(), manifest.id()),
                            manifest.name(),
                            manifest.description(),
                            manifest.version(),
                            remote ? ConnectorSource.REMOTE : ConnectorSource.NATIVE,
                            manifest.icon(),
                            manifest.category(),
                            manifest.configurationSchema(),
                            manifest.actions(),
                            ConnectorTrustLevel.REVIEWED,
                            null,
                            Map.of(
                                    "kind",
                                    "PLUGIN",
                                    "runtime",
                                    remote ? "REMOTE_HTTP" : "JAVA",
                                    "protocolVersion",
                                    "1",
                                    "configurationUiSchema",
                                    manifest.configurationUiSchema(),
                                    "requestedCapabilities",
                                    manifest.requestedCapabilities(),
                                    "skills",
                                    manifest.skills().stream()
                                            .map(skill -> Map.of(
                                                    "id",
                                                    skill.id(),
                                                    "name",
                                                    skill.name(),
                                                    "description",
                                                    skill.description()))
                                            .toList()));
                })
                .toList();
    }

    @Override
    public ConnectorResult execute(ConnectorExecutionRequest request) {
        if (!type().equals(request.connector().provider()))
            throw new ConnectorException("plugin_provider_mismatch", "Invalid plugin provider");
        Registered registered = plugins.get(request.connector().connectorId());
        if (registered == null) throw new ConnectorException("plugin_not_found", "Plugin not registered");
        var action = registered.manifest().actions().stream()
                .filter(item -> item.id().equals(request.actionId()))
                .findFirst()
                .orElseThrow(() -> new ConnectorException("plugin_action_not_found", "Plugin action not declared"));
        boolean asyncAction = "ASYNC".equals(action.metadata().get("executionMode"));
        if (asyncAction
                && (request.context().executionId() == null
                        || request.context().executionId().isBlank()))
            throw new ConnectorException(
                    "plugin_execution_id_required", "Async submission requires a stable execution ID");
        var connection = connections.resolve(request);
        registered.schemas().input(request.actionId(), request.arguments());
        registered.schemas().configuration(connection.configuration(), connection.credentials());
        var context = new PluginContext(
                request.context(),
                connection.configuration(),
                connection.credentials(),
                hosts.forInvocation(registered.manifest(), request.context()));
        var invocation = new PluginInvocation(request.actionId(), request.arguments());
        Object pending = request.context().attributes().get("pluginTask");
        PluginResult result;
        if (pending != null) {
            if (!asyncAction)
                throw new ConnectorException(
                        "plugin_async_unsupported", "Action does not declare asynchronous execution");
            if (!(pending instanceof Map<?, ?> taskData)
                    || !registered.manifest().version().equals(taskData.get("pluginVersion")))
                throw new ConnectorException(
                        "plugin_task_version_mismatch", "Cannot resume task with a different plugin version");
            if (!(registered.implementation() instanceof AsyncPlugin async))
                throw new ConnectorException("plugin_async_unsupported", "Plugin does not support tasks");
            var task = JsonUtils.convert(taskData.get("task"), PluginTask.class);
            if (task == null) throw new ConnectorException("plugin_task_required", "Task handle is required");
            String operation = String.valueOf(request.context().attributes().get("pluginTaskOperation"));
            result = switch (operation) {
                case "QUERY" -> async.query(invocation, task, context);
                case "CANCEL" -> async.cancel(invocation, task, context);
                default -> throw new ConnectorException("plugin_task_operation", "Unknown task operation");
            };
        } else result = registered.implementation().execute(invocation, context);
        if (result == null) throw new ConnectorException("plugin_empty_result", "Plugin returned no result");
        if (result.task() != null) {
            if (!asyncAction)
                throw new ConnectorException("plugin_async_unsupported", "Pending results require an ASYNC action");
            return new ConnectorResult(
                    true,
                    null,
                    List.of(),
                    null,
                    Map.of(
                            "pluginTask",
                            Map.of("pluginVersion", registered.manifest().version(), "task", result.task()),
                            "status",
                            "PENDING"));
        }
        if (result.result().success()
                && !"CANCEL".equals(request.context().attributes().get("pluginTaskOperation")))
            registered.schemas().output(request.actionId(), result.result().data());
        return result.result();
    }
}
