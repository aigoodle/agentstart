package io.aigoodle.plugin.host;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.ConnectorException;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.plugin.PluginManifest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Short-lived invocation-bound bearer capability; signing key is owned by the host. */
public final class PluginHostTokenService {
    public record Claims(
            String audience,
            String id,
            String pluginId,
            String pluginVersion,
            ConnectorExecutionContext identity,
            List<String> capabilities,
            long expiresAt) {}

    private final byte[] secret;
    private final String baseUrl;
    private final Clock clock;

    public PluginHostTokenService(String secret, String baseUrl) {
        this(secret, baseUrl, Clock.systemUTC());
    }

    public PluginHostTokenService(String secret, String baseUrl, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("Plugin host signing secret must contain at least 32 bytes");
        var uri = java.net.URI.create(baseUrl);
        if (uri.getHost() == null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null)
            throw new IllegalArgumentException("Plugin host base URL must be HTTP(S)");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    public PluginHostAccess issue(PluginManifest manifest, ConnectorExecutionContext identity, Duration timeout) {
        long expires = clock.instant()
                .plusSeconds(Math.max(1, Math.min(300, timeout.toSeconds())))
                .getEpochSecond();
        var claims = new Claims(
                "agentstart-plugin-host-v1",
                UUID.randomUUID().toString(),
                manifest.id(),
                manifest.version(),
                identity,
                manifest.requestedCapabilities(),
                expires);
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(JsonUtils.toJson(claims).getBytes(StandardCharsets.UTF_8));
        return new PluginHostAccess(
                baseUrl,
                payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload)),
                expires);
    }

    public Claims verify(String token) {
        try {
            if (token == null || token.length() > 32768) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2
                    || !MessageDigest.isEqual(
                            sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            Claims claims = JsonUtils.parse(
                    new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8), Claims.class);
            if (claims == null
                    || !"agentstart-plugin-host-v1".equals(claims.audience())
                    || claims.identity() == null
                    || claims.capabilities() == null
                    || claims.expiresAt() <= clock.instant().getEpochSecond()) throw new IllegalArgumentException();
            return claims;
        } catch (RuntimeException exception) {
            throw new ConnectorException("plugin_host_unauthorized", "Invalid or expired plugin host token");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
