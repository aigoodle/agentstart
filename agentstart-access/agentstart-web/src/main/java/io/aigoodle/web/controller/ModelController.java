package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.RemoteModel;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.service.ModelRegistration;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.ProviderCredentialService;
import io.aigoodle.model.service.ProviderDefinitionService;
import io.aigoodle.model.service.ProviderModelSettingsService;
import io.aigoodle.web.dto.ProviderCredentialRequest;
import io.aigoodle.web.dto.ProviderView;
import io.aigoodle.web.dto.model.CatalogModelView;
import io.aigoodle.web.dto.model.GroupedProviderView;
import io.aigoodle.web.dto.model.ModelParametersView;
import io.aigoodle.web.dto.model.ProviderCredentialView;
import io.aigoodle.web.dto.model.RemoteModelView;
import io.aigoodle.web.service.ModelCatalogQueryService;
import io.aigoodle.web.support.ModelViewMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST facade over {@link ModelService} + {@link ModelProviderRegistry}. Powers the
 * model-provider settings surface:
 * <ul>
 *   <li>list providers with their credential schema, predefined models, and — critically
 *       for the Dify-parity "填 key 自动拉取" flow — the tenant's own configuration state
 *       (whether credentials are saved, how many models are installed).</li>
 *   <li>save/query/clear a provider-level credential; call the vendor to list what that
 *       key unlocks; batch-register selected entries.</li>
 *   <li>the existing per-model CRUD, kept for advanced users and non-catalog providers.</li>
 * </ul>
 */
@RestController
public class ModelController {

    private final ModelService modelService;
    private final ModelProviderRegistry providerRegistry;
    private final ProviderCredentialService credentialService;
    private final ProviderDefinitionService definitionService;
    private final ProviderModelSettingsService settingsService;
    private final ModelCatalogQueryService catalogQueries;

    public ModelController(
            ModelService modelService,
            ModelProviderRegistry providerRegistry,
            ProviderCredentialService credentialService,
            ProviderDefinitionService definitionService,
            ProviderModelSettingsService settingsService,
            ModelCatalogQueryService catalogQueries) {
        this.modelService = modelService;
        this.providerRegistry = providerRegistry;
        this.credentialService = credentialService;
        this.definitionService = definitionService;
        this.settingsService = settingsService;
        this.catalogQueries = catalogQueries;
    }

    // --------------------------------------------------------------- providers

    /**
     * List every provider definition visible to {@code tenantId} — Dify-parity
     * DB-driven metadata (system-scoped built-ins seeded from Java + any tenant /
     * external rows). Falls back to the Java registry when the DB hasn't been
     * seeded yet (e.g. first boot before {@code ProviderDefinitionSeeder} fires),
     * so first-run UX still works.
     */
    @GetMapping("/model-providers")
    public ApiResponse<List<ProviderView>> listProviders() {
        return ApiResponse.ok(catalogQueries.providers(currentTenantId()));
        // Cold start fallback — first boot before seeder ran, still show Java built-ins.
    }

    @GetMapping("/model-providers/{name}")
    public ApiResponse<ProviderView> getProvider(@PathVariable String name) {
        return ApiResponse.ok(catalogQueries.provider(name, currentTenantId()));
    }

    // ------------------------------------------------ provider definition CRUD

    /**
     * Add a new provider definition. Powers the "从其他模块 / 手动扩展 supported
     * providers" flow: another Maven module (or an admin API caller) can register
     * a provider without shipping a Java bean, as long as {@code implementationKey}
     * matches an existing Java impl (usually {@code "openai_compatible"} —
     * reused for arbitrary OpenAI-compat endpoints).
     */
    @PostMapping("/model-provider-definitions")
    public ApiResponse<ProviderDefinitionEntity> createDefinition(@RequestBody Map<String, Object> body) {
        ProviderDefinitionEntity definition = ModelViewMapper.toProviderDefinition(body);
        definition.setTenantId(currentTenantId());
        if (definition.getSource() == null) {
            definition.setSource("custom");
        }
        return ApiResponse.ok(definitionService.upsert(definition));
    }

    @PutMapping("/model-provider-definitions/{id}")
    public ApiResponse<Void> updateDefinition(@PathVariable String id, @RequestBody Map<String, Object> patch) {
        definitionService.updatePartial(currentTenantId(), id, patch);
        return ApiResponse.ok();
    }

    @DeleteMapping("/model-provider-definitions/{id}")
    public ApiResponse<Void> deleteDefinition(@PathVariable String id) {
        definitionService.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    /**
     * Enumerate the Java implementation bean keys — the pool a definition's
     * {@code implementationKey} can point at. Front-end drops these into a select
     * when creating a custom definition.
     */
    @GetMapping("/model-provider-impls")
    public ApiResponse<List<String>> listImplementationKeys() {
        return ApiResponse.ok(providerRegistry.implementationKeys());
    }

    // ------------------------------------------- predefined-model catalog CRUD

    /**
     * Full catalog for a provider: predefined DB rows + custom {@code agent_model}
     * rows for this tenant, each annotated with the tenant's enable/default state
     * pulled from {@code agent_provider_model_setting} + {@code agent_tenant_default_model}.
     */
    @GetMapping("/model-providers/{name}/catalog")
    public ApiResponse<List<CatalogModelView>> providerCatalog(@PathVariable String name) {
        return ApiResponse.ok(catalogQueries.catalog(name, currentTenantId()));
        // Predefined DB rows first (source=predefined), then custom rows.
    }

    /** Add a predefined model to a provider (extend the shipped catalog from admin UI). */
    @PostMapping("/model-providers/{name}/predefined-models")
    public ApiResponse<PredefinedModelEntity> addPredefinedModel(
            @PathVariable String name, @RequestBody Map<String, Object> body) {
        PredefinedModelEntity row = new PredefinedModelEntity();
        row.setTenantId(currentTenantId());
        row.setProviderName(name);
        row.setModel((String) body.get("model"));
        row.setLabel(body.get("label") == null ? (String) body.get("model") : (String) body.get("label"));
        row.setModelType(ModelType.of((String) body.get("modelType")));
        if (body.get("contextLength") instanceof Number n) row.setContextLength(n.intValue());
        if (body.get("dimensions") instanceof Number n) row.setDimensions(n.intValue());
        if (body.containsKey("features")) {
            row.setFeatures(io.aigoodle.common.util.JsonUtils.toJson(body.get("features")));
        }
        if (body.containsKey("parameterRules")) {
            row.setParameterRules(io.aigoodle.common.util.JsonUtils.toJson(body.get("parameterRules")));
        }
        if (body.get("sortOrder") instanceof Number n) row.setSortOrder(n.intValue());
        return ApiResponse.ok(definitionService.upsertPredefined(row));
    }

    @DeleteMapping("/model-providers/{name}/predefined-models/{id}")
    public ApiResponse<Void> deletePredefinedModel(@PathVariable String name, @PathVariable String id) {
        definitionService.deletePredefined(currentTenantId(), id);
        return ApiResponse.ok();
    }

    // ------------------------------------------- per-model enable / default

    /**
     * Dify-parity opt-in enable/disable by (provider, model, model_type). Writes
     * to {@code agent_provider_model_setting} — missing row = <b>disabled</b> per
     * user request. When enabling a model that isn't in the persisted catalog
     * (came from a live remote fetch), we also materialize an
     * {@code agent_predefined_model} row so the entry stays visible after refresh.
     * This delivers the user's requested design:
     *     "只想存启用的模型到数据库，全部就直接掉接口获取"
     */
    @PutMapping("/model-providers/{name}/models/{modelName}/enabled")
    public ApiResponse<Boolean> setModelEnabled(
            @PathVariable String name,
            @PathVariable String modelName,
            @RequestParam ModelType modelType,
            @RequestBody Map<String, Object> body) {
        String tenantId = currentTenantId();
        boolean enabled = body.get("enabled") instanceof Boolean b
                ? b
                : Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        settingsService.setEnabled(tenantId, name, modelName, modelType, enabled);
        if (enabled) {
            // Persist a lightweight predefined row (tenant-scoped) so this model
            // appears in the catalog on subsequent loads without needing a refresh.
            if (definitionService.findPredefined(currentTenantId(), name, modelName, modelType) == null) {
                PredefinedModelEntity row = new PredefinedModelEntity();
                row.setTenantId(tenantId);
                row.setProviderName(name);
                row.setModel(modelName);
                row.setLabel(modelName);
                row.setModelType(modelType);
                definitionService.upsertPredefined(row);
            }
        }
        return ApiResponse.ok(enabled);
    }

    /**
     * Dify-parity set-default: at most one default per (tenant, model_type). Writes
     * to {@code agent_tenant_default_model}. Legacy {@code PUT /models/{id}/default}
     * (routes to {@code agent_model.is_default}) is kept for backward compat.
     */
    @PutMapping("/model-providers/{name}/models/{modelName}/default")
    public ApiResponse<Void> setModelDefault(
            @PathVariable String name, @PathVariable String modelName, @RequestParam ModelType modelType) {
        String tenantId = currentTenantId();
        settingsService.setDefault(tenantId, name, modelName, modelType);
        // A tenant default must be invocable — make sure its enable switch is ON.
        // The dropdown only offers switched-on models, so for UI-driven calls this
        // is a no-op; it protects API callers that set a default on a model whose
        // switch was never flipped.
        settingsService.setEnabled(tenantId, name, modelName, modelType, true);
        return ApiResponse.ok();
    }

    // ------------------------------------------------- provider-level credential

    @GetMapping("/model-providers/{name}/credential")
    public ApiResponse<ProviderCredentialView> getProviderCredential(@PathVariable String name) {
        ModelProvider provider = providerRegistry.get(name);
        ProviderCredentialEntity credential = credentialService.findPrimary(currentTenantId(), provider.getName());
        return ApiResponse.ok(ModelViewMapper.toCredentialView(provider, credential));
    }

    /**
     * Save (or rotate) the tenant's primary credential for {@code name}. When the
     * provider supports remote listing, the vendor is called BEFORE the credential is
     * persisted — a bad api key throws {@code 400} and no state changes. Dify-parity
     * <em>no auto-enable</em>: no {@code agent_model} / setting rows are created
     * here; the user switches on individual models from the catalog popover, which
     * is also the gate for the default-model dropdown.
     */
    @PutMapping("/model-providers/{name}/credential")
    public ApiResponse<ProviderCredentialView> upsertProviderCredential(
            @PathVariable String name, @RequestBody ProviderCredentialRequest request) {
        ModelProvider provider = providerRegistry.get(name);
        ProviderCredentialEntity credential = modelService.saveProviderCredentialWithValidation(
                currentTenantId(), provider.getName(), request.getCredentials());
        return ApiResponse.ok(ModelViewMapper.toCredentialView(provider, credential));
    }

    /**
     * Delete the credential AND cascade every tenant-scoped row (enable
     * toggles, tenant defaults, materialized catalog entries, custom
     * {@code agent_model} rows) so re-adding the API key lands on a clean
     * slate. Without the cascade the previous enable / default state
     * resurfaces the moment the credential is saved again — the user report
     * this fix addresses.
     */
    @DeleteMapping("/model-providers/{name}/credential")
    public ApiResponse<Void> deleteProviderCredential(@PathVariable String name) {
        modelService.deleteProviderCredentialCascade(
                currentTenantId(), providerRegistry.get(name).getName());
        return ApiResponse.ok();
    }

    /**
     * Ask the vendor "what models does this key unlock?" using the tenant's saved
     * credential. Non-destructive — does not touch the catalog rows.
     */
    @GetMapping("/model-providers/{name}/remote-models")
    public ApiResponse<List<RemoteModelView>> listRemoteModels(@PathVariable String name) {
        List<RemoteModel> models = modelService.listRemoteModels(currentTenantId(), name);
        return ApiResponse.ok(
                models.stream().map(ModelViewMapper::toRemoteModelView).toList());
    }

    /**
     * "重新拉取": hit the vendor and return the raw list — transient, no DB writes.
     * The UI merges these with the persisted predefined catalog for display; only
     * models the user explicitly enables end up in {@code agent_provider_model_setting}.
     */
    @PostMapping("/model-providers/{name}/refresh-catalog")
    public ApiResponse<List<RemoteModelView>> refreshCatalog(@PathVariable String name) {
        List<RemoteModel> remote = modelService.refreshCatalog(currentTenantId(), name);
        return ApiResponse.ok(
                remote.stream().map(ModelViewMapper::toRemoteModelView).toList());
    }

    // ------------------------------------------------------------------ models

    @GetMapping("/models")
    public ApiResponse<List<ModelEntity>> listModels(@RequestParam(required = false) ModelType type) {
        String tenantId = currentTenantId();
        return ApiResponse.ok(type == null ? modelService.list(tenantId) : modelService.listByType(tenantId, type));
    }

    @GetMapping("/models/{id}")
    public ApiResponse<ModelEntity> getModel(@PathVariable String id) {
        return ApiResponse.ok(modelService.require(currentTenantId(), id));
    }

    @PostMapping("/models")
    public ApiResponse<ModelEntity> registerModel(@RequestBody ModelRegistration registration) {
        registration.setTenantId(currentTenantId());
        return ApiResponse.ok(modelService.register(registration));
    }

    @PutMapping("/models/{id}/credentials")
    public ApiResponse<ModelEntity> updateCredentials(
            @PathVariable String id, @RequestBody Map<String, Object> credentials) {
        return ApiResponse.ok(modelService.updateCredentials(currentTenantId(), id, credentials));
    }

    @PutMapping("/models/{id}/default")
    public ApiResponse<Void> markDefault(@PathVariable String id) {
        modelService.setDefault(currentTenantId(), id);
        return ApiResponse.ok();
    }

    /**
     * "开关": flip the {@code enabled} column. Disabled rows stay in the DB (metadata
     * preserved across catalog refreshes) but are filtered out of every downstream
     * chat/embedding lookup, so this is the "启用/禁用" the settings UI drives.
     */
    @PatchMapping("/models/{id}/enabled")
    public ApiResponse<ModelEntity> setEnabled(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object v = body.get("enabled");
        boolean enabled = v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
        return ApiResponse.ok(modelService.setEnabled(currentTenantId(), id, enabled));
    }

    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable String id) {
        modelService.delete(currentTenantId(), id);
        return ApiResponse.ok();
    }

    /** Dry-run the credentials without persisting: builds the provider once. */
    @PostMapping("/models/validate")
    public ApiResponse<Void> validate(@RequestBody ModelRegistration registration) {
        registration.setTenantId(currentTenantId());
        modelService.validate(registration);
        return ApiResponse.ok();
    }

    /**
     * Live-hit an already-saved model. Returns {ok, latencyMs, kind, snippet|dimensions|error}.
     * The UI wires this into a "测试连接" button so operators can verify a key still works
     * after rotation without redeploying.
     */
    @PostMapping("/models/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable String id) {
        return ApiResponse.ok(modelService.testConnection(currentTenantId(), id));
    }

    // -------------------------------------------------------- model parameters

    /**
     * Read the per-model parameter values + the rule set the UI should render.
     * Returns the whole payload the parameter drawer needs in one round-trip.
     * Filters out entries whose key does not match a rule name so credential
     * overrides ({@code apiKey}/{@code baseUrl}) don't leak into the view.
     */
    @GetMapping("/models/{id}/parameters")
    public ApiResponse<ModelParametersView> getModelParameters(@PathVariable String id) {
        return ApiResponse.ok(catalogQueries.parameters(currentTenantId(), id));
    }

    /**
     * Save a partial parameter update. Payload {@code {temperature: 0.9}} merges
     * into whatever is stored; {@code {temperature: null}} removes the override so
     * the provider falls back to its own default.
     */
    @PutMapping("/models/{id}/parameters")
    public ApiResponse<ModelParametersView> updateModelParameters(
            @PathVariable String id, @RequestBody Map<String, Object> parameters) {
        modelService.updateParameters(currentTenantId(), id, parameters);
        return getModelParameters(id);
    }

    /**
     * The tenant's default model per {@link ModelType} — feeds the "系统默认模型"
     * summary section so LLM / EMBEDDING / RERANK / TTS defaults are visible in a
     * single glance instead of scattered across every model row.
     */
    @GetMapping("/models/defaults")
    public ApiResponse<Map<String, ModelEntity>> listDefaults() {
        Map<String, ModelEntity> out = new LinkedHashMap<>();
        modelService.listDefaults(currentTenantId()).forEach((type, entity) -> out.put(type.name(), entity));
        return ApiResponse.ok(out);
    }

    /**
     * Enabled models grouped by {@link ModelType}, then by provider — the shape
     * the "系统默认模型" panel (and the shared model picker) binds against
     * (opt-groups per provider, options are individual models). Only providers
     * with saved credentials participate, and a model is listed <b>only when its
     * switch is ON</b> — predefined rows via {@code agent_provider_model_setting},
     * custom {@code agent_model} rows via their {@code enabled} column. This is
     * the "拉取模型之后只有开关打开的才允许在默认模型下拉中选择" rule that keeps
     * the dropdown from flooding with the provider's whole catalog. Duplicates
     * are impossible: a materialized {@code agent_model} copy of a predefined
     * model is skipped (see {@code ModelCatalogQueryService#groupedModelsByType}).
     *
     * <p>Response shape:
     * <pre>{
     *   "LLM": [{
     *     "id": "openai",
     *     "provider": "openai",
     *     "description": "...",
     *     "declaration": { "icon": "openai-svg" },
     *     "modelList": [{
     *       "id": "openai::gpt-4o::LLM",
     *       "modelName": "gpt-4o",
     *       "modelType": "LLM",
     *       "providerName": "openai"
     *     }, ...]
     *   }, ...],
     *   "TEXT_EMBEDDING": [...], ...
     * }</pre>
     * Model {@code id} is a composite key ({@code provider::model::type}) so the
     * frontend can round-trip it straight to
     * {@code PUT /model-providers/{name}/models/{modelName}/default}.
     */
    @GetMapping("/models/grouped-by-type")
    public ApiResponse<Map<String, List<GroupedProviderView>>> listModelsGroupedByType() {
        return ApiResponse.ok(catalogQueries.groupedModelsByType(currentTenantId()));
    }
}
