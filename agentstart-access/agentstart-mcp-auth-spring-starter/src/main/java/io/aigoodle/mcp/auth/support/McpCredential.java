package io.aigoodle.mcp.auth.support;

/** Raw inbound authorization value plus the normalized token used for authentication. */
public record McpCredential(String headerValue, String token) {

    public McpCredential {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException("headerValue must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }

    public static McpCredential fromAuthorization(String value) {
        String header = value == null ? null : value.trim();
        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException("authorization value must not be blank");
        }
        String token = header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim()
                : header;
        return new McpCredential(header, token);
    }
}
