package io.aigoodle.connector;

import java.util.Objects;

/** Stable identity of a connector within a provider ecosystem. */
public record ConnectorKey(String provider, String connectorId) {
    public ConnectorKey {
        provider = requirePart(provider, "provider");
        connectorId = requirePart(connectorId, "connectorId");
    }

    public String externalForm() {
        return provider + ":" + connectorId;
    }

    private static String requirePart(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
