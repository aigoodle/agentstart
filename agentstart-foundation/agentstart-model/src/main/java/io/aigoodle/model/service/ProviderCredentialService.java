package io.aigoodle.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.mapper.ProviderCredentialMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for provider-level shared credentials. Secrets are encrypted via
 * {@link CredentialCodec} before they ever touch the database.
 */
public class ProviderCredentialService {

    private static final String DEFAULT_TENANT = "default";

    private final ProviderCredentialMapper credentialMapper;
    private final CredentialCodec credentialCodec;

    public ProviderCredentialService(ProviderCredentialMapper credentialMapper, CredentialCodec credentialCodec) {
        this.credentialMapper = credentialMapper;
        this.credentialCodec = credentialCodec;
    }

    @Transactional
    public ProviderCredentialEntity save(ProviderCredentialRegistration registration) {
        ProviderCredentialEntity credential = new ProviderCredentialEntity();
        credential.setTenantId(defaultTenant(registration.tenantId()));
        credential.setProviderName(registration.providerName());
        credential.setCredentialName(defaultCredentialName(registration));
        credential.setEncryptedConfig(credentialCodec.encode(credential.getTenantId(), registration.credentials()));
        credential.setEnabled(Boolean.TRUE);
        credentialMapper.insert(credential);
        return credential;
    }

    /** @deprecated use {@link #save(ProviderCredentialRegistration)}. */
    @Deprecated
    @Transactional
    public ProviderCredentialEntity save(
            String tenantId, String providerName, String credentialName, Map<String, Object> credentials) {
        return save(new ProviderCredentialRegistration(tenantId, providerName, credentialName, credentials));
    }

    @Transactional
    public void update(String id, Map<String, Object> credentials) {
        update(UserContextHolder.currentTenantId(), id, credentials);
    }

    @Transactional
    public void update(String tenantId, String id, Map<String, Object> credentials) {
        ProviderCredentialEntity credential = require(tenantId, id);
        credential.setEncryptedConfig(credentialCodec.encode(credential.getTenantId(), credentials));
        updateOwned(credential);
    }

    public ProviderCredentialEntity get(String id) {
        return get(UserContextHolder.currentTenantId(), id);
    }

    public ProviderCredentialEntity get(String tenantId, String id) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<ProviderCredentialEntity>()
                .eq(ProviderCredentialEntity::getTenantId, defaultTenant(tenantId))
                .eq(ProviderCredentialEntity::getId, id)
                .last("limit 1"));
    }

    public ProviderCredentialEntity require(String id) {
        return require(UserContextHolder.currentTenantId(), id);
    }

    public ProviderCredentialEntity require(String tenantId, String id) {
        ProviderCredentialEntity credential = get(tenantId, id);
        if (credential == null) {
            throw new PlatformException("credential_not_found", "Provider credential not found: " + id, null);
        }
        return credential;
    }

    public Map<String, Object> decodeCredentials(String id) {
        ProviderCredentialEntity credential = require(id);
        return credentialCodec.decode(credential.getTenantId(), credential.getEncryptedConfig());
    }

    public Map<String, Object> decodeCredentials(String tenantId, String id) {
        return credentialCodec.decode(
                defaultTenant(tenantId), require(tenantId, id).getEncryptedConfig());
    }

    /** One tenant-scoped read for the provider settings page. */
    public List<ProviderCredentialEntity> listForTenant(String tenantId) {
        return credentialMapper.selectList(new LambdaQueryWrapper<ProviderCredentialEntity>()
                .eq(ProviderCredentialEntity::getTenantId, defaultTenant(tenantId))
                .orderByAsc(ProviderCredentialEntity::getCreatedAt));
    }

    public List<ProviderCredentialEntity> listByProvider(String tenantId, String providerName) {
        return credentialMapper.selectList(new LambdaQueryWrapper<ProviderCredentialEntity>()
                .eq(ProviderCredentialEntity::getTenantId, defaultTenant(tenantId))
                .eq(ProviderCredentialEntity::getProviderName, providerName)
                .orderByAsc(ProviderCredentialEntity::getCreatedAt));
    }

    @Transactional
    public void delete(String id) {
        delete(UserContextHolder.currentTenantId(), id);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        credentialMapper.delete(new LambdaQueryWrapper<ProviderCredentialEntity>()
                .eq(ProviderCredentialEntity::getTenantId, defaultTenant(tenantId))
                .eq(ProviderCredentialEntity::getId, id));
    }

    // ------------------------------------------------------- primary credential

    /**
     * Return the tenant's single primary credential for {@code providerName}, or null
     * if none exists. "Primary" is the first row for the (tenant, provider) pair — used
     * by the Dify-parity "one key per provider" flow the model settings UI drives.
     * <p>
     * Multiple named credentials per provider remain possible via
     * {@link #save(String, String, String, java.util.Map)} — they simply don't
     * participate in this shortcut.
     */
    public ProviderCredentialEntity findPrimary(String tenantId, String providerName) {
        List<ProviderCredentialEntity> rows = listByProvider(tenantId, providerName);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Upsert the tenant's primary credential for {@code providerName}. Values not
     * supplied in {@code patch} keep their previous encrypted value — same PATCH
     * semantics as {@link ModelService#updateCredentials} so the UI can rotate just
     * the api key without re-typing base url etc.
     */
    @Transactional
    public ProviderCredentialEntity upsertPrimary(String tenantId, String providerName, Map<String, Object> patch) {
        ProviderCredentialEntity existing = findPrimary(tenantId, providerName);
        if (existing == null) {
            return save(new ProviderCredentialRegistration(
                    tenantId, providerName, providerName + "-primary", CredentialPatchMerger.merge(Map.of(), patch)));
        }
        Map<String, Object> mergedCredentials = CredentialPatchMerger.merge(
                credentialCodec.decode(existing.getTenantId(), existing.getEncryptedConfig()), patch);
        existing.setEncryptedConfig(credentialCodec.encode(existing.getTenantId(), mergedCredentials));
        updateOwned(existing);
        return existing;
    }

    @Transactional
    public void deletePrimary(String tenantId, String providerName) {
        ProviderCredentialEntity existing = findPrimary(tenantId, providerName);
        if (existing != null) {
            credentialMapper.delete(new LambdaQueryWrapper<ProviderCredentialEntity>()
                    .eq(ProviderCredentialEntity::getTenantId, defaultTenant(tenantId))
                    .eq(ProviderCredentialEntity::getId, existing.getId()));
        }
    }

    /**
     * Decode a credential and mask secret fields by name. Any value under a key listed
     * in {@code secretKeys} is replaced with a Dify-style {@code sk-***abcd} preview so
     * the front-end can display the key without ever handling the plaintext.
     */
    public Map<String, Object> maskedView(ProviderCredentialEntity entity, Iterable<String> secretKeys) {
        if (entity == null) {
            return new HashMap<>();
        }
        return credentialCodec.obfuscate(
                credentialCodec.decode(entity.getTenantId(), entity.getEncryptedConfig()), secretKeys);
    }

    private static String defaultTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
    }

    private static String defaultCredentialName(ProviderCredentialRegistration registration) {
        return registration.credentialName() == null
                        || registration.credentialName().isBlank()
                ? registration.providerName()
                : registration.credentialName();
    }

    private void updateOwned(ProviderCredentialEntity credential) {
        credentialMapper.update(
                credential,
                new LambdaUpdateWrapper<ProviderCredentialEntity>()
                        .eq(ProviderCredentialEntity::getTenantId, defaultTenant(credential.getTenantId()))
                        .eq(ProviderCredentialEntity::getId, credential.getId()));
    }
}
