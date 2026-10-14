package io.aigoodle.connector.registry;

import io.aigoodle.connector.ConnectorDefinition;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.ConnectorKey;
import io.aigoodle.connector.provider.ConnectorProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic, refreshable catalog snapshot assembled from all connector providers. */
public class ConnectorRegistry {
    public record Snapshot(
            Map<ConnectorKey, ConnectorDefinition> definitions,
            Map<String, ConnectorProvider> providers,
            long revision,
            Instant refreshedAt) {}

    private final List<ConnectorProvider> providerList;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public ConnectorRegistry(List<ConnectorProvider> providers) {
        this.providerList = providers == null ? List.of() : List.copyOf(providers);
        refresh();
    }

    public synchronized Snapshot refresh() {
        Map<String, ConnectorProvider> byType = new LinkedHashMap<>();
        Map<ConnectorKey, ConnectorDefinition> definitions = new LinkedHashMap<>();
        long revision = 1;
        for (ConnectorProvider provider : providerList) {
            ConnectorProvider previous = byType.putIfAbsent(provider.type(), provider);
            if (previous != null)
                throw new ConnectorException(
                        "connector_provider_duplicate", "Duplicate connector provider type '" + provider.type() + "'");
            revision = 31 * revision + provider.revision();
            for (ConnectorDefinition definition : safeDiscover(provider)) {
                ConnectorDefinition previousDefinition = definitions.putIfAbsent(definition.key(), definition);
                if (previousDefinition != null)
                    throw new ConnectorException(
                            "connector_duplicate",
                            "Duplicate connector '" + definition.key().externalForm() + "'");
            }
        }
        Snapshot next = new Snapshot(Map.copyOf(definitions), Map.copyOf(byType), revision, Instant.now());
        snapshot.set(next);
        return next;
    }

    public ConnectorDefinition get(ConnectorKey key) {
        ConnectorDefinition definition = snapshot.get().definitions().get(key);
        if (definition == null)
            throw new ConnectorException("connector_not_found", "No connector '" + key.externalForm() + "'");
        return definition;
    }

    public ConnectorProvider provider(String type) {
        ConnectorProvider provider = snapshot.get().providers().get(type);
        if (provider == null)
            throw new ConnectorException("connector_provider_not_found", "No connector provider '" + type + "'");
        return provider;
    }

    public List<ConnectorDefinition> all() {
        return new ArrayList<>(snapshot.get().definitions().values());
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    private static List<ConnectorDefinition> safeDiscover(ConnectorProvider provider) {
        List<ConnectorDefinition> definitions = provider.discover();
        return definitions == null ? List.of() : definitions;
    }
}
