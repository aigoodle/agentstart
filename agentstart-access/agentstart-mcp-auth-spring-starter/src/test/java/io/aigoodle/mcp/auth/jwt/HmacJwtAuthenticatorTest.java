package io.aigoodle.mcp.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.mcp.auth.config.McpAuthProperties;
import io.aigoodle.mcp.auth.exception.McpAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacJwtAuthenticatorTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(2_000_000), ZoneOffset.UTC);

    @Test
    void validatesJwtAndMapsCallerClaims() throws Exception {
        McpAuthProperties.Jwt properties = properties();
        HmacJwtAuthenticator authenticator = new HmacJwtAuthenticator(properties, mapper, clock);
        String token = token(Map.of(
                "sub",
                "u-7",
                "preferred_username",
                "alice",
                "tenant_id",
                "factory-a",
                "roles",
                List.of("ADMIN"),
                "scope",
                "mes:read mes:write",
                "iss",
                "issuer-a",
                "aud",
                List.of("other", "mes-mcp"),
                "exp",
                2_000_100));

        var caller = authenticator.authenticate(token);

        assertThat(caller.getUserId()).isEqualTo("u-7");
        assertThat(caller.getTenantId()).isEqualTo("factory-a");
        assertThat(caller.getRoles()).containsExactly("ADMIN");
        assertThat(caller.getScopes()).containsExactlyInAnyOrder("mes:read", "mes:write");
    }

    @Test
    void rejectsExpiredOrTamperedJwt() throws Exception {
        HmacJwtAuthenticator authenticator = new HmacJwtAuthenticator(properties(), mapper, clock);
        String expired = token(Map.of("sub", "u-7", "iss", "issuer-a", "aud", "mes-mcp", "exp", 1_999_900));
        assertThatThrownBy(() -> authenticator.authenticate(expired))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessageContaining("expired");

        String valid = token(Map.of("sub", "u-7", "iss", "issuer-a", "aud", "mes-mcp", "exp", 2_000_100));
        String tampered = valid.substring(0, valid.length() - 1) + (valid.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> authenticator.authenticate(tampered))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessageContaining("signature");
    }

    private McpAuthProperties.Jwt properties() {
        McpAuthProperties.Jwt properties = new McpAuthProperties.Jwt();
        properties.setSecret(SECRET);
        properties.setIssuer("issuer-a");
        properties.setAudience("mes-mcp");
        properties.setClockSkew(java.time.Duration.ZERO);
        return properties;
    }

    private String token(Map<String, Object> claims) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
        String payload = encoder.encodeToString(mapper.writeValueAsBytes(claims));
        String content = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return content + "." + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
    }
}
