package io.aigoodle.mcp.auth.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.PrincipalType;
import io.aigoodle.mcp.auth.config.McpAuthProperties;
import io.aigoodle.mcp.auth.exception.McpAuthenticationException;
import io.aigoodle.mcp.auth.spi.McpTokenAuthenticator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Dependency-light HS256/384/512 JWT validation with time, issuer and audience checks. */
public final class HmacJwtAuthenticator implements McpTokenAuthenticator {

    private static final Map<String, String> MAC_ALGORITHMS =
            Map.of("HS256", "HmacSHA256", "HS384", "HmacSHA384", "HS512", "HmacSHA512");

    private final McpAuthProperties.Jwt properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;

    public HmacJwtAuthenticator(McpAuthProperties.Jwt properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    HmacJwtAuthenticator(McpAuthProperties.Jwt properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("spring-agent.mcp.auth.jwt.secret must contain at least 32 UTF-8 bytes");
        }
    }

    @Override
    public CurrentUser authenticate(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3) throw invalid("JWT must have three sections");
            Map<String, Object> header = json(parts[0]);
            String algorithm = text(header.get("alg"));
            String macAlgorithm = MAC_ALGORITHMS.get(algorithm);
            if (macAlgorithm == null) throw invalid("Only HS256, HS384 and HS512 JWTs are supported");
            verifySignature(parts[0] + "." + parts[1], parts[2], macAlgorithm);
            Map<String, Object> claims = json(parts[1]);
            validateClaims(claims);
            String userId = text(claims.get(properties.getUserIdClaim()));
            if (userId == null) throw invalid("JWT is missing user id claim " + properties.getUserIdClaim());
            return CurrentUser.builder()
                    .userId(userId)
                    .username(text(claims.get(properties.getUsernameClaim())))
                    .tenantId(text(claims.get(properties.getTenantIdClaim())))
                    .appId(text(claims.get(properties.getAppIdClaim())))
                    .principalType(PrincipalType.USER)
                    .roles(values(claims.get(properties.getRolesClaim()), false))
                    .scopes(values(claims.get(properties.getScopesClaim()), true))
                    .extra(Map.of("jwtClaims", Map.copyOf(claims)))
                    .build();
        } catch (McpAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new McpAuthenticationException("Invalid MCP JWT", exception);
        }
    }

    private Map<String, Object> json(String encoded) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readValue(bytes, new TypeReference<>() {});
    }

    private void verifySignature(String content, String signature, String algorithm) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret, algorithm));
        byte[] expected = mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
        byte[] actual = Base64.getUrlDecoder().decode(signature);
        if (!MessageDigest.isEqual(expected, actual)) throw invalid("JWT signature is invalid");
    }

    private void validateClaims(Map<String, Object> claims) {
        long now = clock.instant().getEpochSecond();
        long skew = properties.getClockSkew() == null
                ? 0
                : properties.getClockSkew().toSeconds();
        Long expiresAt = number(claims.get("exp"));
        if (expiresAt != null && now - skew >= expiresAt) throw invalid("JWT has expired");
        Long notBefore = number(claims.get("nbf"));
        if (notBefore != null && now + skew < notBefore) throw invalid("JWT is not active yet");
        if (properties.getIssuer() != null
                && !properties.getIssuer().isBlank()
                && !properties.getIssuer().equals(text(claims.get("iss")))) throw invalid("JWT issuer is invalid");
        if (properties.getAudience() != null
                && !properties.getAudience().isBlank()
                && !values(claims.get("aud"), false).contains(properties.getAudience()))
            throw invalid("JWT audience is invalid");
    }

    private Set<String> values(Object value, boolean splitSpaces) {
        if (value == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.stream().map(this::text).filter(v -> v != null).forEach(result::add);
        } else {
            String text = text(value);
            if (text != null) {
                if (splitSpaces)
                    Arrays.stream(text.split("\\s+")).filter(v -> !v.isBlank()).forEach(result::add);
                else result.add(text);
            }
        }
        return Set.copyOf(result);
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw invalid("JWT time claim is invalid");
        }
    }

    private String text(Object value) {
        if (value == null) return null;
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private McpAuthenticationException invalid(String message) {
        return new McpAuthenticationException(message);
    }
}
