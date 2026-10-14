package io.aigoodle.plugin.host;

/** Sent only to the selected remote service, never exposed in the public plugin catalog. */
public record PluginHostAccess(String baseUrl, String token, long expiresAt) {
    @Override
    public String toString() {
        return "PluginHostAccess[<redacted>]";
    }
}
