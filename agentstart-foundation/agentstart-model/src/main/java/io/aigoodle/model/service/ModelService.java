package io.aigoodle.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.entity.TenantDefaultModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.mapper.ModelMapper;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.provider.RemoteModel;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.runtime.ModelInstance;
import io.aigoodle.model.runtime.ModelInstanceFactory;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.transaction.annotation.Transactional;

/**
 * The primary entry point of the model module: register models, resolve their
 * (decrypted, merged) endpoints and obtain cached runtime handles that other
 * modules (knowledge, workflow, agent) invoke.
 */
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);
    private static final String DEFAULT_TENANT = "default";

    private final ModelMapper modelMapper;
    private final ProviderCredentialService credentialService;
    private final CredentialCodec credentialCodec;
    private final ModelProviderRegistry providerRegistry;
    private final ModelInstanceFactory instanceFactory;
    private final ProviderDefinitionService definitionService;
    private final ProviderModelSettingsService settingsService;
    private final ModelConnectionTester connectionTester;
    private final ProviderCatalogClient providerCatalogClient;

    public ModelService(
            ModelMapper modelMapper,
            ProviderCredentialService credentialService,
            CredentialCodec credentialCodec,
            ModelProviderRegistry providerRegistry,
            ModelInstanceFactory instanceFactory,
            ProviderDefinitionService definitionService,
            ProviderModelSettingsService settingsService,
            ModelConnectionTester connectionTester,
            ProviderCatalogClient providerCatalogClient) {
        this.modelMapper = modelMapper;
        this.credentialService = credentialService;
        this.credentialCodec = credentialCodec;
        this.providerRegistry = providerRegistry;
        this.instanceFactory = instanceFactory;
        this.definitionService = definitionService;
        this.settingsService = settingsService;
        this.connectionTester = connectionTester;
        this.providerCatalogClient = providerCatalogClient;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public ModelEntity register(ModelRegistration registration) {
        ModelProvider provider = providerRegistry.get(registration.getProviderName());
        if (!provider.supports(registration.getModelType())) {
            throw new PlatformException(
                    "model_type_unsupported",
                    "Provider '" + provider.getName() + "' does not support " + registration.getModelType(),
                    null);
        }
        String tenantId = normalizeTenantId(registration.getTenantId());

        String encryptedConfiguration = credentialCodec.encode(tenantId, registration.getCredentials());
        ModelEntity registeredModel = ModelEntityFactory.registered(registration, tenantId, encryptedConfiguration);
        modelMapper.insert(registeredModel);

        if (registration.isAsDefault()) {
            clearOtherDefaults(tenantId, registration.getModelType(), registeredModel.getId());
        }
        log.info(
                "Registered model id={} provider={} model={} type={}",
                registeredModel.getId(),
                registration.getProviderName(),
                registration.getModelName(),
                registration.getModelType());
        return registeredModel;
    }

    /**
     * Merge {@code patch} into the existing decoded credentials, then re-encrypt. Fields
     * absent from {@code patch} keep their old value — so the frontend can PATCH a
     * single rotated API key without having to re-type the base URL, etc.
     * <p>
     * Passing {@code null} or an empty map is a no-op. Use {@link #replaceCredentials}
     * if you actually want the full-overwrite semantic (rare — currently only useful
     * when moving providers).
     */
    @Transactional
    public ModelEntity updateCredentials(String id, Map<String, Object> patch) {
        ModelEntity entity = require(id);
        if (patch == null || patch.isEmpty()) {
            return entity;
        }
        Map<String, Object> updatedConfiguration = decodeConfiguration(entity);
        updatedConfiguration.putAll(patch);
        return saveConfiguration(entity, updatedConfiguration);
    }

    @Transactional
    public ModelEntity updateCredentials(String tenantId, String id, Map<String, Object> patch) {
        ModelEntity entity = require(tenantId, id);
        if (patch == null || patch.isEmpty()) return entity;
        Map<String, Object> updatedConfiguration = decodeConfiguration(entity);
        updatedConfiguration.putAll(patch);
        return saveConfiguration(entity, updatedConfiguration);
    }

    /** Overwrite every credential field. Rarely what you want — see {@link #updateCredentials}. */
    @Transactional
    public ModelEntity replaceCredentials(String id, Map<String, Object> credentials) {
        ModelEntity entity = require(id);
        return saveConfiguration(entity, credentials);
    }

    /**
     * Return the decoded per-model overrides map. Both model parameters
     * (temperature, maxTokens, …) and any model-level credential overrides live in
     * the same {@code encryptedConfig} JSON — same storage, same encryption. The
     * caller separates them by rule name when rendering.
     */
    public Map<String, Object> getModelProperties(String id) {
        ModelEntity entity = require(id);
        return decodeConfiguration(entity);
    }

    public Map<String, Object> getModelProperties(String tenantId, String id) {
        return decodeConfiguration(require(tenantId, id));
    }

    /**
     * Merge model parameter values (from the Dify-style parameter drawer) into the
     * existing config. Fields absent from {@code parameters} keep their old value —
     * the UI can save a single slider change without re-typing everything.
     * <p>
     * Passing an empty map is a no-op. A value of {@code null} explicitly
     * <em>removes</em> a key so the model falls back to the provider's default
     * (whatever OpenAiChatOptions.builder() would produce).
     */
    @Transactional
    public ModelEntity updateParameters(String id, Map<String, Object> parameters) {
        ModelEntity entity = require(id);
        if (parameters == null || parameters.isEmpty()) {
            return entity;
        }
        Map<String, Object> updatedConfiguration = decodeConfiguration(entity);
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            if (parameter.getValue() == null) {
                updatedConfiguration.remove(parameter.getKey());
            } else {
                updatedConfiguration.put(parameter.getKey(), parameter.getValue());
            }
        }
        return saveConfiguration(entity, updatedConfiguration);
    }

    @Transactional
    public ModelEntity updateParameters(String tenantId, String id, Map<String, Object> parameters) {
        ModelEntity entity = require(tenantId, id);
        if (parameters == null || parameters.isEmpty()) return entity;
        Map<String, Object> updatedConfiguration = decodeConfiguration(entity);
        parameters.forEach((key, value) -> {
            if (value == null) updatedConfiguration.remove(key);
            else updatedConfiguration.put(key, value);
        });
        return saveConfiguration(entity, updatedConfiguration);
    }

    /**
     * Resolve the parameter rules the UI should render for this model — either the
     * PredefinedModel-level override or the provider-wide default per model type.
     * Returns an empty list for unknown models.
     */
    public List<ModelParameterRule> parameterRulesFor(String id) {
        ModelEntity entity = require(id);
        ModelProvider provider = providerRegistry.get(entity.getProviderName());
        for (PredefinedModel predefinedModel : provider.predefinedModels()) {
            if (predefinedModel.getModel().equalsIgnoreCase(entity.getModelName())
                    && predefinedModel.getModelType() == entity.getModelType()
                    && predefinedModel.getParameterRules() != null
                    && !predefinedModel.getParameterRules().isEmpty()) {
                return predefinedModel.getParameterRules();
            }
        }
        return provider.defaultParameterRules(entity.getModelType());
    }

    public List<ModelParameterRule> parameterRulesFor(String tenantId, String id) {
        return parameterRulesFor(require(tenantId, id));
    }

    private List<ModelParameterRule> parameterRulesFor(ModelEntity entity) {
        ModelProvider provider = providerRegistry.get(entity.getProviderName());
        for (PredefinedModel predefinedModel : provider.predefinedModels()) {
            if (predefinedModel.getModel().equalsIgnoreCase(entity.getModelName())
                    && predefinedModel.getModelType() == entity.getModelType()
                    && predefinedModel.getParameterRules() != null
                    && !predefinedModel.getParameterRules().isEmpty()) return predefinedModel.getParameterRules();
        }
        return provider.defaultParameterRules(entity.getModelType());
    }

    /**
     * Group each tenant's default models by {@link ModelType}. Feeds the Dify-style
     * "系统默认模型" panel at the top of the provider hub so the user can see
     * "LLM 用什么 / EMBEDDING 用什么 / RERANK 用什么" in a single glance and
     * one-click reassign.
     */
    public Map<ModelType, ModelEntity> listDefaults(String tenantId) {
        Map<ModelType, ModelEntity> defaultsByType = new EnumMap<>(ModelType.class);
        for (ModelType modelType : ModelType.values()) {
            ModelEntity defaultModel = getDefault(tenantId, modelType);
            if (defaultModel != null) {
                defaultsByType.put(modelType, defaultModel);
            }
        }
        return defaultsByType;
    }

    @Transactional
    public void setDefault(String id) {
        ModelEntity entity = require(id);
        entity.setIsDefault(Boolean.TRUE);
        updateOwned(entity);
        clearOtherDefaults(entity.getTenantId(), entity.getModelType(), id);
    }

    @Transactional
    public void setDefault(String tenantId, String id) {
        ModelEntity entity = require(tenantId, id);
        entity.setIsDefault(Boolean.TRUE);
        updateOwned(entity);
        clearOtherDefaults(entity.getTenantId(), entity.getModelType(), id);
    }

    @Transactional
    public void delete(String id) {
        delete(UserContextHolder.currentTenantId(), id);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        require(tenantId, id);
        modelMapper.delete(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getId, id));
        instanceFactory.evict(normalizeTenantId(tenantId), id);
    }

    public ModelEntity get(String id) {
        return get(UserContextHolder.currentTenantId(), id);
    }

    public ModelEntity get(String tenantId, String id) {
        return modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getId, id)
                .last("limit 1"));
    }

    public ModelEntity require(String id) {
        ModelEntity entity = get(UserContextHolder.currentTenantId(), id);
        if (entity == null) {
            throw new PlatformException("model_not_found", "Model not found: " + id, null);
        }
        return entity;
    }

    public ModelEntity require(String tenantId, String id) {
        ModelEntity entity = modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getId, id)
                .last("limit 1"));
        if (entity == null) throw new PlatformException("model_not_found", "Model not found: " + id, null);
        return entity;
    }

    public List<ModelEntity> list(String tenantId) {
        return modelMapper.selectList(
                new LambdaQueryWrapper<ModelEntity>().eq(ModelEntity::getTenantId, normalizeTenantId(tenantId)));
    }

    public List<ModelEntity> listByType(String tenantId, ModelType type) {
        return modelMapper.selectList(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getModelType, type)
                .eq(ModelEntity::getEnabled, true));
    }

    /** Custom-registered models for a given (tenant, provider). Used by the catalog view. */
    public List<ModelEntity> listByProvider(String tenantId, String providerName) {
        return modelMapper.selectList(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getProviderName, providerName));
    }

    /**
     * Resolve the tenant default in three layers:
     * <ol>
     *   <li>Dify-parity {@code agent_tenant_default_model} — if a row exists,
     *       resolve (provider, model, type) into an ephemeral {@link ModelEntity}
     *       (materialized on-the-fly if no custom row exists yet).</li>
     *   <li>Legacy {@code agent_model.is_default} — backward compat for tenants
     *       migrated from the old design.</li>
     *   <li>Any enabled model of the type — last-resort fallback.</li>
     * </ol>
     */
    public ModelEntity getDefault(String tenantId, ModelType type) {
        // 1. New Dify-parity default table
        if (settingsService != null) {
            TenantDefaultModelEntity configuredDefault = settingsService.getDefault(tenantId, type);
            if (configuredDefault != null) {
                ModelEntity defaultModel = findOrMaterialize(
                        normalizeTenantId(tenantId),
                        configuredDefault.getProviderName(),
                        configuredDefault.getModelName(),
                        type);
                if (defaultModel != null) {
                    return defaultModel;
                }
            }
        }
        // 2. Legacy is_default flag
        ModelEntity legacy = modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getModelType, type)
                .eq(ModelEntity::getIsDefault, true)
                .last("limit 1"));
        if (legacy != null) {
            return legacy;
        }
        // 3. Any enabled model
        return modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, normalizeTenantId(tenantId))
                .eq(ModelEntity::getModelType, type)
                .eq(ModelEntity::getEnabled, true)
                .last("limit 1"));
    }

    /**
     * Return the existing custom {@link ModelEntity} for the triple, or materialize
     * a lightweight one that inherits the provider credential. This is how a
     * "predefined" tenant default becomes a usable runtime handle without forcing
     * the user to explicitly {@code POST /models} first — the row is created
     * lazily the first time the runtime asks for it.
     */
    @Transactional
    public ModelEntity findOrMaterialize(String tenantId, String providerName, String modelName, ModelType modelType) {
        ModelEntity existing = modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, tenantId)
                .eq(ModelEntity::getProviderName, providerName)
                .eq(ModelEntity::getModelName, modelName)
                .eq(ModelEntity::getModelType, modelType)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        ProviderCredentialEntity providerCredential = credentialService.findPrimary(tenantId, providerName);
        ProviderModelReference modelReference = new ProviderModelReference(providerName, modelName, modelType);
        String credentialId = providerCredential == null ? null : providerCredential.getId();
        ModelEntity materializedModel = modelReference.newMaterializedModel(tenantId, credentialId);
        modelMapper.insert(materializedModel);
        log.info(
                "Materialized agent_model row on default-lookup for tenant={} provider={} model={} type={}",
                tenantId,
                providerName,
                modelName,
                modelType);
        return materializedModel;
    }

    // ------------------------------------------------------------- resolution

    /** Merge provider-level + model-level credentials into a runnable endpoint. */
    public ModelEndpoint resolveEndpoint(ModelEntity entity) {
        Map<String, Object> endpointProperties = new HashMap<>();
        if (entity.getCredentialId() != null) {
            endpointProperties.putAll(
                    credentialService.decodeCredentials(entity.getTenantId(), entity.getCredentialId()));
        }
        endpointProperties.putAll(decodeConfiguration(entity));

        Object apiKey = endpointProperties.remove("apiKey");
        Object baseUrl = endpointProperties.remove("baseUrl");
        return ModelEndpoint.builder()
                .tenantId(entity.getTenantId())
                .id(entity.getId())
                .providerName(entity.getProviderName())
                .modelName(entity.getModelName())
                .modelType(entity.getModelType())
                .apiKey(apiKey == null ? null : String.valueOf(apiKey))
                .baseUrl(baseUrl == null ? null : String.valueOf(baseUrl))
                .properties(endpointProperties)
                .build();
    }

    public ModelEndpoint resolveEndpoint(String id) {
        return resolveEndpoint(require(id));
    }

    public ModelEndpoint resolveEndpoint(String tenantId, String id) {
        return resolveEndpoint(require(tenantId, id));
    }

    // -------------------------------------------------------------- runtime

    public ModelInstance getModelInstance(String id) {
        return instanceFactory.getOrCreate(resolveEndpoint(id));
    }

    public ModelInstance getModelInstance(String tenantId, String id) {
        return instanceFactory.getOrCreate(resolveEndpoint(tenantId, id));
    }

    public ChatClient getChatClient(String id) {
        return getModelInstance(id).getChatClient();
    }

    public ChatClient getChatClient(String tenantId, String id) {
        return getModelInstance(tenantId, id).getChatClient();
    }

    /**
     * Resolve a {@link ChatClient} by (tenant, provider, model name) — the console
     * saves apps with the plain model name + provider key rather than a synthetic
     * uuid, so runtime lookups go through {@link #findOrMaterialize} to hydrate the
     * matching {@code agent_model} row (creating one if the vendor catalog was
     * only registered as predefined).
     */
    public ChatClient getChatClient(String tenantId, String providerName, String modelName) {
        ModelEntity entity = findOrMaterialize(normalizeTenantId(tenantId), providerName, modelName, ModelType.LLM);
        return getChatClient(tenantId, entity.getId());
    }

    public ChatModel getChatModel(String id) {
        return getModelInstance(id).getChatModel();
    }

    public ChatModel getChatModel(String tenantId, String id) {
        return getModelInstance(tenantId, id).getChatModel();
    }

    public EmbeddingModel getEmbeddingModel(String id) {
        return getModelInstance(id).getEmbeddingModel();
    }

    public EmbeddingModel getEmbeddingModel(String tenantId, String id) {
        return getModelInstance(tenantId, id).getEmbeddingModel();
    }

    public ModelInstance getDefaultInstance(String tenantId, ModelType type) {
        ModelEntity defaultModel = getDefault(tenantId, type);
        if (defaultModel == null) {
            throw new PlatformException(
                    "no_default_model",
                    "No " + type + " model configured for tenant '" + normalizeTenantId(tenantId) + "'",
                    null);
        }
        return getModelInstance(normalizeTenantId(tenantId), defaultModel.getId());
    }

    /**
     * Build the model once (without caching) to surface configuration errors early.
     * Does not make a network call; provider construction validates required fields.
     */
    public void validate(ModelRegistration registration) {
        ModelEndpoint endpoint = ModelEndpoint.builder()
                .tenantId(normalizeTenantId(registration.getTenantId()))
                .id("validation")
                .providerName(registration.getProviderName())
                .modelName(registration.getModelName())
                .modelType(registration.getModelType())
                .apiKey(asString(registration.getCredentials().get("apiKey")))
                .baseUrl(asString(registration.getCredentials().get("baseUrl")))
                .properties(new HashMap<>(registration.getCredentials()))
                .build();
        instanceFactory.build(endpoint);
    }

    // ---------------------------------------------------- provider-key flow

    /**
     * Build a "list only" endpoint from the tenant's saved provider credential — no
     * ModelEntity involved. Used by the model-provider-hub UI to call
     * {@link ModelProvider#listRemoteModels} against an already-saved credential.
     */
    public ModelEndpoint resolveProviderEndpoint(String tenantId, String providerName) {
        return providerCatalogClient.endpointForSavedCredential(tenantId, providerName);
    }

    /**
     * Validate raw credentials against the vendor without persisting anything. Used by
     * PUT /credential to reject a bad API key before it touches the DB. Returns the
     * fetched catalog so the caller can reuse it for the initial seed — saving one
     * round-trip.
     */
    public List<RemoteModel> previewRemoteModels(String providerName, Map<String, Object> credentials) {
        return providerCatalogClient.preview(providerName, credentials);
    }

    /** Ask the provider for its live catalog against the tenant's saved credential. */
    public List<RemoteModel> listRemoteModels(String tenantId, String providerName) {
        return providerCatalogClient.list(tenantId, providerName);
    }

    /**
     * Refresh the live catalog and persist newly discovered models for this tenant.
     * Catalog discovery does not enable models or change existing model metadata,
     * settings, defaults, or the shared system catalog.
     */
    @Transactional
    public List<RemoteModel> refreshCatalog(String tenantId, String providerName) {
        String tenant = normalizeTenantId(tenantId);
        List<RemoteModel> remote = providerCatalogClient.refresh(tenant, providerName);
        var known = new HashSet<String>();
        for (PredefinedModelEntity row : definitionService.listPredefined(tenant, providerName)) {
            known.add(row.getModel() + "::" + row.getModelType());
        }
        for (RemoteModel model : remote) {
            if (model == null
                    || model.getModelId() == null
                    || model.getModelId().isBlank()
                    || model.getModelType() == null) continue;
            if (!known.add(model.getModelId() + "::" + model.getModelType())) continue;
            PredefinedModelEntity row = new PredefinedModelEntity();
            row.setTenantId(tenant);
            row.setProviderName(providerName);
            row.setModel(model.getModelId());
            row.setLabel(model.getLabel() == null ? model.getModelId() : model.getLabel());
            row.setModelType(model.getModelType());
            row.setFeatures(JsonUtils.toJson(model.getFeatures()));
            row.setContextLength(model.getContextLength());
            row.setDimensions(model.getDimensions());
            definitionService.upsertPredefined(row);
        }
        return remote;
    }

    /**
     * Delete the tenant's provider credential AND every tenant-scoped row that
     * hung off it — the enable-settings, the tenant-default rows, materialized
     * catalog entries, and the tenant's custom {@code agent_model} rows for that
     * provider.
     * <p>
     * Without this cascade, deleting an API key leaves stale state behind so
     * re-adding the same provider immediately resurrects every previously
     * enabled model + default — matching the user report "删除的时候没删干净,
     * 再次添加还是之前的".
     * <p>
     * The system-tenant predefined catalog (the provider's shipped defaults)
     * and the provider definition itself are left alone — they are metadata,
     * not tenant state.
     */
    @Transactional
    public void deleteProviderCredentialCascade(String tenantId, String providerName) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        // 1. Enable toggles + tenant defaults for this provider.
        if (settingsService != null) {
            settingsService.clearProviderSettings(normalizedTenantId, providerName);
            settingsService.clearProviderDefaults(normalizedTenantId, providerName);
        }
        // 2. Custom agent_model rows for this (tenant, provider). Evict runtime
        // caches per id so pooled ChatClients don't outlive their credentials.
        List<ModelEntity> providerModels = listByProvider(normalizedTenantId, providerName);
        for (ModelEntity model : providerModels) {
            modelMapper.delete(new LambdaQueryWrapper<ModelEntity>()
                    .eq(ModelEntity::getTenantId, normalizedTenantId)
                    .eq(ModelEntity::getId, model.getId()));
            instanceFactory.evict(normalizedTenantId, model.getId());
        }
        // 3. Materialized (tenant-scoped) predefined catalog rows.
        if (definitionService != null) {
            definitionService.deleteTenantPredefined(normalizedTenantId, providerName);
        }
        // 4. Finally the credential itself.
        credentialService.deletePrimary(normalizedTenantId, providerName);
        log.info(
                "Cascade-deleted provider credential tenant={} provider={} (custom-models={})",
                normalizedTenantId,
                providerName,
                providerModels.size());
    }

    /**
     * Save the tenant's provider credential. Dify-parity: <em>no auto-enable</em>.
     * No {@code agent_model} rows and no {@code agent_provider_model_setting}
     * rows are created — the user picks which models to enable via the settings
     * UI. Returns just the persisted credential.
     */
    @Transactional
    public ProviderCredentialEntity saveProviderCredentialWithValidation(
            String tenantId, String providerName, Map<String, Object> credentials) {
        // Probe remote listing when supported. A transient provider outage must not
        // prevent credential storage; operators can retry catalog refresh later.
        ModelProvider provider = providerRegistry.get(providerName);
        Map<String, Object> effective = new java.util.HashMap<>();
        var existing = credentialService.findPrimary(tenantId, providerName);
        if (existing != null) effective.putAll(credentialService.decodeCredentials(tenantId, existing.getId()));
        effective.putAll(credentials);
        for (var field : provider.credentialSchema().fields()) {
            Object value = effective.getOrDefault(field.getName(), field.getDefaultValue());
            if (field.isRequired() && (value == null || String.valueOf(value).isBlank())) {
                throw new PlatformException("credential_field_required", "Please configure " + field.getLabel(), null);
            }
        }
        new ProviderDefinitionSeeder(providerRegistry, definitionService).seedProvider(providerName);
        if (provider.supportsRemoteModelListing()) {
            try {
                previewRemoteModels(providerName, effective);
            } catch (RuntimeException catalogFailure) {
                log.warn(
                        "Remote listing failed for provider {} during credential save: {}",
                        providerName,
                        catalogFailure.getMessage());
            }
        }
        return credentialService.upsertPrimary(tenantId, providerName, credentials);
    }

    /**
     * Flip the {@code enabled} column on a single model row. This IS the "开关" — a
     * disabled row still lives in {@code agent_model} (so its metadata is preserved
     * across refreshes) but is filtered out by every downstream {@link #listByType}
     * call, so chat/knowledge/workflow only see enabled models.
     */
    @Transactional
    public ModelEntity setEnabled(String id, boolean enabled) {
        ModelEntity entity = require(id);
        entity.setEnabled(enabled);
        updateOwned(entity);
        instanceFactory.evict(entity.getTenantId(), id);
        return entity;
    }

    @Transactional
    public ModelEntity setEnabled(String tenantId, String id, boolean enabled) {
        ModelEntity entity = require(tenantId, id);
        entity.setEnabled(enabled);
        updateOwned(entity);
        instanceFactory.evict(entity.getTenantId(), id);
        return entity;
    }

    /**
     * Actually hit the remote provider: a small ping for LLM, an embed for embedding
     * model. Returns a summary of latency + a snippet of the response so the UI can
     * show something more useful than "ok" — good for diagnosing key rotation.
     * <p>
     * Uses a fresh (non-cached) instance so we can attach a fast-fail retry policy
     * — Spring AI's default is {@code maxAttempts=10, initial=2s, multiplier=5,
     * max=180s} which turns a bad URL or 404 into a 30+ minute silent hang. For a
     * user-facing "点击测试" this is unusable; we cap the wait at a couple of seconds
     * and let the real error surface. Production traffic still uses the default
     * cached instance with the full retry policy.
     */
    public Map<String, Object> testConnection(String id) {
        ModelEntity entity = require(id);
        return connectionTester.test(entity, resolveEndpoint(entity));
    }

    public Map<String, Object> testConnection(String tenantId, String id) {
        ModelEntity entity = require(tenantId, id);
        return connectionTester.test(entity, resolveEndpoint(entity));
    }

    // --------------------------------------------------------------- helpers

    private Map<String, Object> decodeConfiguration(ModelEntity model) {
        return new HashMap<>(credentialCodec.decode(model.getTenantId(), model.getEncryptedConfig()));
    }

    private ModelEntity saveConfiguration(ModelEntity model, Map<String, Object> configuration) {
        model.setEncryptedConfig(credentialCodec.encode(model.getTenantId(), configuration));
        updateOwned(model);
        instanceFactory.evict(model.getTenantId(), model.getId());
        return model;
    }

    private void clearOtherDefaults(String tenantId, ModelType type, String keepId) {
        ModelEntity patch = new ModelEntity();
        patch.setIsDefault(Boolean.FALSE);
        modelMapper.update(
                patch,
                Wrappers.<ModelEntity>lambdaUpdate()
                        .eq(ModelEntity::getTenantId, tenantId)
                        .eq(ModelEntity::getModelType, type)
                        .ne(ModelEntity::getId, keepId));
    }

    private void updateOwned(ModelEntity model) {
        modelMapper.update(
                model,
                new LambdaUpdateWrapper<ModelEntity>()
                        .eq(ModelEntity::getTenantId, normalizeTenantId(model.getTenantId()))
                        .eq(ModelEntity::getId, model.getId()));
    }

    private static String normalizeTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
