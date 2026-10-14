package io.aigoodle.connector;

/** Semantic surface exposed by a connector, independent from execution risk. */
public enum ConnectorCapability {
    CHANNEL_INBOUND,
    CHANNEL_OUTBOUND,
    ACTION,
    EVENT
}
