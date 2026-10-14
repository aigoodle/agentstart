package io.aigoodle.channel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves channel runtime providers without coupling callers to a concrete transport implementation. */
public class ChannelRuntimeRegistry {
    private final Map<String, Map<String, ChannelRuntimeProvider>> providers;

    public ChannelRuntimeRegistry(List<ChannelRuntimeProvider> providers) {
        Map<String, Map<String, ChannelRuntimeProvider>> indexed = new LinkedHashMap<>();
        for (ChannelRuntimeProvider provider : providers) {
            String nodeId = provider.nodeId() == null || provider.nodeId().isBlank()
                    ? provider.type() + "-default"
                    : provider.nodeId();
            indexed.computeIfAbsent(provider.type(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(nodeId, provider);
        }
        indexed.replaceAll((key, value) -> java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value)));
        this.providers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    public List<ChannelDefinition> discover() {
        return discover(null);
    }

    /**
     * Discovers one runtime node across providers. A blank node keeps the historical
     * aggregate behavior (the default node of every provider).
     */
    public List<ChannelDefinition> discover(String nodeId) {
        java.util.ArrayList<ChannelDefinition> channels = new java.util.ArrayList<>();
        providers.values().stream()
                .map(nodes -> nodeId == null || nodeId.isBlank()
                        ? nodes.values().iterator().next()
                        : nodes.get(nodeId))
                .filter(java.util.Objects::nonNull)
                .forEach(provider -> {
                    try {
                        channels.addAll(provider.discoverChannels());
                    } catch (RuntimeException ignored) {
                        /* one unavailable runtime must not hide healthy providers */
                    }
                });
        return List.copyOf(channels);
    }

    public ChannelRuntimeProvider require(String provider) {
        Map<String, ChannelRuntimeProvider> nodes = providers.get(provider);
        ChannelRuntimeProvider runtime = nodes == null || nodes.isEmpty()
                ? null
                : nodes.values().iterator().next();
        if (runtime == null)
            throw new ChannelException("channel_runtime_not_found", "Channel runtime provider not found: " + provider);
        return runtime;
    }

    public ChannelRuntimeProvider require(String provider, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return require(provider);
        Map<String, ChannelRuntimeProvider> nodes = providers.get(provider);
        ChannelRuntimeProvider runtime = nodes == null ? null : nodes.get(nodeId);
        if (runtime == null)
            throw new ChannelException(
                    "channel_runtime_node_not_found", "Channel runtime node not found: " + provider + "/" + nodeId);
        return runtime;
    }

    public List<String> nodeIds(String provider) {
        Map<String, ChannelRuntimeProvider> nodes = providers.get(provider);
        return nodes == null ? List.of() : List.copyOf(nodes.keySet());
    }

    public Map<String, List<String>> nodes() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        providers.forEach((provider, nodes) -> result.put(provider, List.copyOf(nodes.keySet())));
        return Map.copyOf(result);
    }
}
