package io.aigoodle.connector;

/** Where a connector definition and its executable implementation originate. */
public enum ConnectorSource {
    NATIVE,
    DECLARATIVE,
    OPENAPI,
    MCP,
    OPENCLAW,
    N8N,
    DIFY,
    REMOTE
}
