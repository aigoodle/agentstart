package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.entity.AppApiTokenEntity;
import io.aigoodle.agent.mapper.AppApiTokenMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.persistence.TenantSqlScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for per-app API tokens. The token value is generated server-side on
 * create (opaque URL-safe base64) — the frontend never supplies it.
 */
public class AppApiTokenService {

    private static final Logger logger = LoggerFactory.getLogger(AppApiTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "app-";
    private static final String HASH_PREFIX = "sha256$";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String DEFAULT_TOKEN_NAME = "default";
    private static final String DEFAULT_TOKEN_TYPE = "app";

    private final AppApiTokenMapper tokenMapper;

    public AppApiTokenService(AppApiTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    public List<AppApiTokenEntity> listByApp(String appId) {
        return listByApp(UserContextHolder.currentTenantId(), appId);
    }

    public List<AppApiTokenEntity> listByApp(String tenantId, String appId) {
        return tokenMapper.selectList(new LambdaQueryWrapper<AppApiTokenEntity>()
                .eq(AppApiTokenEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                .eq(AppApiTokenEntity::getAppId, appId)
                .orderByDesc(AppApiTokenEntity::getCreatedAt));
    }

    public AppApiTokenEntity require(String tokenId) {
        AppApiTokenEntity apiToken = tokenMapper.selectOne(new LambdaQueryWrapper<AppApiTokenEntity>()
                .eq(AppApiTokenEntity::getTenantId, UserContextHolder.currentTenantId())
                .eq(AppApiTokenEntity::getId, tokenId)
                .last("LIMIT 1"));
        if (apiToken == null) {
            throw new PlatformException("api_token_not_found", "API token not found: " + tokenId, null);
        }
        return apiToken;
    }

    public AppApiTokenEntity require(String tenantId, String appId, String tokenId) {
        AppApiTokenEntity token = tokenMapper.selectOne(new LambdaQueryWrapper<AppApiTokenEntity>()
                .eq(AppApiTokenEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                .eq(AppApiTokenEntity::getAppId, appId)
                .eq(AppApiTokenEntity::getId, tokenId)
                .last("LIMIT 1"));
        if (token == null) throw new PlatformException("api_token_not_found", "API token not found", null);
        return token;
    }

    /**
     * Resolve a token value to its owning row. Returns {@code null} when the
     * token is missing or unknown — callers decide whether that maps to 401.
     * Backed by {@code idx_api_token_value} so lookup is O(log n).
     */
    public AppApiTokenEntity findByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String raw = token.trim();
        String encoded = encodedToken(raw);
        return TenantSqlScope.bypass(() -> {
            AppApiTokenEntity found = tokenMapper.selectOne(new LambdaQueryWrapper<AppApiTokenEntity>()
                    .eq(AppApiTokenEntity::getToken, encoded)
                    .last("LIMIT 1"));
            if (found != null) return found;
            // Compatibility migration for tokens minted before one-way storage was introduced.
            AppApiTokenEntity legacy = tokenMapper.selectOne(new LambdaQueryWrapper<AppApiTokenEntity>()
                    .eq(AppApiTokenEntity::getToken, raw)
                    .last("LIMIT 1"));
            if (legacy != null && !isEncoded(legacy.getToken())) {
                AppApiTokenEntity patch = new AppApiTokenEntity();
                patch.setToken(encoded);
                tokenMapper.update(
                        patch,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppApiTokenEntity>()
                                .eq(AppApiTokenEntity::getTenantId, legacy.getTenantId())
                                .eq(AppApiTokenEntity::getId, legacy.getId())
                                .eq(AppApiTokenEntity::getToken, raw));
                legacy.setToken(encoded);
            }
            return legacy;
        });
    }

    /**
     * Bump {@code last_used_at} to now. Called from the chat request path; the
     * frontend uses this to show a "最后使用" column matching Dify's UX.
     * Deliberately non-transactional and swallows failures — a hiccup here must
     * not fail the enclosing chat call.
     */
    public void touchLastUsed(String tokenId) {
        touchLastUsed(UserContextHolder.currentTenantId(), tokenId);
    }

    public void touchLastUsed(String tenantId, String tokenId) {
        try {
            AppApiTokenEntity usageUpdate = new AppApiTokenEntity();
            usageUpdate.setLastUsedAt(LocalDateTime.now());
            tokenMapper.update(
                    usageUpdate,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppApiTokenEntity>()
                            .eq(AppApiTokenEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                            .eq(AppApiTokenEntity::getId, tokenId));
        } catch (RuntimeException updateFailure) {
            // Best effort: chat continues even when usage metadata cannot be updated.
            logger.debug("Unable to update last-used time for API token {}: {}", tokenId, updateFailure.getMessage());
        }
    }

    @Transactional
    public AppApiTokenEntity create(String appId, String tenantId, String name, String type) {
        AppApiTokenEntity apiToken = new AppApiTokenEntity();
        apiToken.setAppId(appId);
        apiToken.setTenantId(valueOrDefault(tenantId, DEFAULT_TENANT_ID));
        apiToken.setName(valueOrDefault(name, DEFAULT_TOKEN_NAME));
        apiToken.setType(valueOrDefault(type, DEFAULT_TOKEN_TYPE));
        String rawToken = generateToken();
        apiToken.setToken(encodedToken(rawToken));
        tokenMapper.insert(apiToken);
        // The raw secret exists only in the create response object; it is never persisted.
        apiToken.setToken(rawToken);
        return apiToken;
    }

    @Transactional
    public AppApiTokenEntity rename(String tokenId, String name) {
        AppApiTokenEntity apiToken = require(tokenId);
        if (name != null) {
            apiToken.setName(name);
        }
        tokenMapper.update(
                apiToken,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppApiTokenEntity>()
                        .eq(AppApiTokenEntity::getTenantId, apiToken.getTenantId())
                        .eq(AppApiTokenEntity::getId, apiToken.getId()));
        return apiToken;
    }

    @Transactional
    public AppApiTokenEntity rename(String tenantId, String appId, String tokenId, String name) {
        AppApiTokenEntity token = require(tenantId, appId, tokenId);
        if (name != null) token.setName(name);
        tokenMapper.update(
                token,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppApiTokenEntity>()
                        .eq(AppApiTokenEntity::getTenantId, token.getTenantId())
                        .eq(AppApiTokenEntity::getAppId, token.getAppId())
                        .eq(AppApiTokenEntity::getId, token.getId()));
        return token;
    }

    @Transactional
    public void delete(String tokenId) {
        AppApiTokenEntity token = require(tokenId);
        tokenMapper.delete(new LambdaQueryWrapper<AppApiTokenEntity>()
                .eq(AppApiTokenEntity::getTenantId, token.getTenantId())
                .eq(AppApiTokenEntity::getId, tokenId));
    }

    @Transactional
    public void delete(String tenantId, String appId, String tokenId) {
        AppApiTokenEntity token = require(tenantId, appId, tokenId);
        tokenMapper.delete(new LambdaQueryWrapper<AppApiTokenEntity>()
                .eq(AppApiTokenEntity::getTenantId, token.getTenantId())
                .eq(AppApiTokenEntity::getAppId, token.getAppId())
                .eq(AppApiTokenEntity::getId, token.getId()));
    }

    private static String generateToken() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String tokenHint(String stored) {
        if (stored == null) return null;
        if (isEncoded(stored)) {
            int separator = stored.indexOf('$', HASH_PREFIX.length());
            return separator > HASH_PREFIX.length() ? stored.substring(HASH_PREFIX.length(), separator) : "????";
        }
        return stored.substring(Math.max(0, stored.length() - 4));
    }

    static boolean isEncoded(String value) {
        return value != null && value.startsWith(HASH_PREFIX);
    }

    private static String encodedToken(String raw) {
        try {
            String digest = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
            String hint = raw.substring(Math.max(0, raw.length() - 4));
            return HASH_PREFIX + hint + '$' + digest;
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
