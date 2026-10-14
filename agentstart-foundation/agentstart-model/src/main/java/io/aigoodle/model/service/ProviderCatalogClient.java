package io.aigoodle.model.service;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.RemoteModel;
import io.aigoodle.model.registry.ModelProviderRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Accesses a provider's live model catalog using raw or persisted credentials. */
public class ProviderCatalogClient {

    private static final String DEFAULT_TENANT = "default";

    private final ModelProviderRegistry providerRegistry;
    private final ProviderCredentialService credentialService;
    private final CredentialCodec credentialCodec;

    public ProviderCatalogClient(
            ModelProviderRegistry providerRegistry,
            ProviderCredentialService credentialService,
            CredentialCodec credentialCodec) {
        this.providerRegistry = providerRegistry;
        this.credentialService = credentialService;
        this.credentialCodec = credentialCodec;
    }

    public ModelEndpoint endpointForSavedCredential(String tenantId, String providerName) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String missingCredentialMessage =
                "Provider '" + providerName + "' has no saved credential for tenant '" + normalizedTenantId + "'";
        return endpointForSavedCredential(normalizedTenantId, providerName, missingCredentialMessage);
    }

    private ModelEndpoint endpointForSavedCredential(
            String tenantId, String providerName, String missingCredentialMessage) {
        ProviderCredentialEntity credential = credentialService.findPrimary(tenantId, providerName);
        if (credential == null) {
            throw new PlatformException("provider_credential_missing", missingCredentialMessage, null);
        }
        Map<String, Object> credentials = credentialCodec.decode(tenantId, credential.getEncryptedConfig());
        return endpoint(tenantId, providerName, credentials);
    }

    public List<RemoteModel> preview(String providerName, Map<String, Object> credentials) {
        ModelProvider provider = providerRegistry.get(providerName);
        Map<String, Object> safeCredentials = credentials == null ? Map.of() : credentials;
        return provider.listRemoteModels(endpoint(DEFAULT_TENANT, providerName, safeCredentials));
    }

    public List<RemoteModel> list(String tenantId, String providerName) {
        ModelProvider provider = providerRegistry.get(providerName);
        return provider.listRemoteModels(endpointForSavedCredential(tenantId, providerName));
    }

    public List<RemoteModel> refresh(String tenantId, String providerName) {
        ModelProvider provider = providerRegistry.get(providerName);
        ModelEndpoint endpoint = endpointForSavedCredential(
                normalizeTenantId(tenantId), providerName, "Save a provider credential before refreshing the catalog");
        return provider.listRemoteModels(endpoint);
    }

    private ModelEndpoint endpoint(String tenantId, String providerName, Map<String, Object> credentials) {
        Map<String, Object> providerProperties = new HashMap<>(credentials);
        String apiKey = removeString(providerProperties, "apiKey");
        String baseUrl = removeString(providerProperties, "baseUrl");
        return ModelEndpoint.builder()
                .tenantId(normalizeTenantId(tenantId))
                .providerName(providerName)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .properties(providerProperties)
                .build();
    }

    private static String removeString(Map<String, Object> properties, String propertyName) {
        Object value = properties.remove(propertyName);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
    }
}
