package io.aigoodle.web.support;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.entity.ProviderModelSettingEntity;
import io.aigoodle.model.entity.TenantDefaultModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.ProviderCredentialService;
import io.aigoodle.model.service.ProviderDefinitionService;
import io.aigoodle.model.service.ProviderModelSettingsService;
import io.aigoodle.web.dto.ProviderView;
import io.aigoodle.web.dto.model.CatalogModelView;
import io.aigoodle.web.dto.model.ParameterRuleView;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enriches model-provider response views with persisted catalog and tenant state.
 *
 * <p>This class is constructed by the controller from its existing collaborators,
 * so extracting presentation logic does not change the Spring bean graph or the
 * controller's public constructor.</p>
 */
public final class ModelProviderViewAssembler {

    private final ModelService modelService;
    private final ProviderCredentialService credentialService;
    private final ProviderDefinitionService definitionService;
    private final ProviderModelSettingsService settingsService;

    private record TenantSnapshot(
            List<ModelEntity> models,
            Map<String, ProviderCredentialEntity> credentials,
            Map<String, Map<String, ProviderModelSettingEntity>> settings) {}

    private final TenantSnapshot snapshot;

    public ModelProviderViewAssembler(
            ModelService modelService,
            ProviderCredentialService credentialService,
            ProviderDefinitionService definitionService,
            ProviderModelSettingsService settingsService) {
        this(modelService, credentialService, definitionService, settingsService, null);
    }

    private ModelProviderViewAssembler(
            ModelService modelService,
            ProviderCredentialService credentialService,
            ProviderDefinitionService definitionService,
            ProviderModelSettingsService settingsService,
            TenantSnapshot snapshot) {
        this.snapshot = snapshot;
        this.modelService = modelService;
        this.credentialService = credentialService;
        this.definitionService = definitionService;
        this.settingsService = settingsService;
    }

    /** Request-local snapshot: no cross-tenant cache or stale credential state. */
    public ModelProviderViewAssembler forTenant(String tenantId) {
        Map<String, ProviderCredentialEntity> credentials = new LinkedHashMap<>();
        for (var credential : credentialService.listForTenant(tenantId)) {
            credentials.putIfAbsent(credential.getProviderName(), credential);
        }
        Map<String, Map<String, ProviderModelSettingEntity>> settings = new LinkedHashMap<>();
        for (var setting : settingsService.listForTenant(tenantId)) {
            settings.computeIfAbsent(setting.getProviderName(), key -> new LinkedHashMap<>())
                    .put(setting.getModelName() + "::" + setting.getModelType().name(), setting);
        }
        return new ModelProviderViewAssembler(
                modelService,
                credentialService,
                definitionService,
                settingsService,
                new TenantSnapshot(List.copyOf(modelService.list(tenantId)), credentials, settings));
    }

    public ProviderView toProviderView(ProviderDefinitionEntity definition, String tenantId) {
        ProviderView view = new ProviderView();
        view.setId(definition.getId());
        view.setName(definition.getName());
        view.setLabel(
                "builtin".equals(definition.getSource()) && "moonshot".equals(definition.getName())
                        ? "Kimi"
                        : definition.getLabel());
        view.setDescription(definition.getDescription());
        view.setIcon(definition.getIcon());
        view.setSvgIcon(definition.getSvgIcon());
        view.setImplementationKey(definition.getImplementationKey());
        view.setDefaultBaseUrl(definition.getDefaultBaseUrl());
        view.setSource(definition.getSource());
        view.setSortOrder(definition.getSortOrder());
        view.setEnabled(definition.getEnabled());
        view.setSupportsRemoteModelListing(Boolean.TRUE.equals(definition.getSupportsRemoteModelListing()));

        Set<ModelType> supportedTypes = definitionService.deserializeModelTypes(definition.getSupportedModelTypes());
        view.setSupportedModelTypes(supportedTypes);
        List<CredentialField> credentialFields =
                definitionService.deserializeCredentialSchema(definition.getCredentialSchema());
        view.setCredentialSchema(credentialFields.stream()
                .map(ModelViewMapper::toCredentialFieldView)
                .toList());
        view.setDefaultParameterRules(defaultRulesByModelType(definition, supportedTypes));
        List<PredefinedModelEntity> predefinedModels = definitionService.listPredefined(tenantId, definition.getName());
        view.setPredefinedModels(
                predefinedModels.stream().map(this::toPredefinedModelRow).toList());

        addTenantState(
                view,
                tenantId,
                definition.getName(),
                credentialFields.stream()
                        .filter(CredentialField::isSecret)
                        .map(CredentialField::getName)
                        .toList(),
                predefinedTriples(predefinedModels));
        return view;
    }

    public ProviderView toProviderView(ModelProvider provider, String tenantId) {
        ProviderView view = new ProviderView();
        view.setName(provider.getName());
        view.setLabel(provider.getLabel());
        view.setImplementationKey(provider.implementationKey());
        view.setSource("builtin");
        view.setSupportedModelTypes(provider.supportedModelTypes());
        view.setCredentialSchema(provider.credentialSchema().fields().stream()
                .map(ModelViewMapper::toCredentialFieldView)
                .toList());
        view.setPredefinedModels(provider.predefinedModels().stream()
                .map(ModelViewMapper::toPredefinedModelView)
                .toList());

        Map<String, List<ParameterRuleView>> defaultRules = new LinkedHashMap<>();
        for (ModelType modelType : provider.supportedModelTypes()) {
            defaultRules.put(
                    modelType.name(),
                    provider.defaultParameterRules(modelType).stream()
                            .map(ModelViewMapper::toParameterRuleView)
                            .toList());
        }
        view.setDefaultParameterRules(defaultRules);
        view.setSupportsRemoteModelListing(provider.supportsRemoteModelListing());
        Set<String> triples = new HashSet<>();
        for (PredefinedModel predefinedModel : provider.predefinedModels()) {
            triples.add(predefinedModel.getModel() + "::"
                    + predefinedModel.getModelType().name());
        }
        addTenantState(
                view, tenantId, provider.getName(), provider.credentialSchema().secretFieldNames(), triples);
        return view;
    }

    public CatalogModelView toCatalogRow(
            PredefinedModelEntity predefinedModel,
            Map<String, ProviderModelSettingEntity> settings,
            Map<ModelType, TenantDefaultModelEntity> defaults) {
        CatalogModelView row = toPredefinedModelRow(predefinedModel);
        ProviderModelSettingEntity setting = settings.get(predefinedModel.getModel() + "::"
                + predefinedModel.getModelType().name());
        row.setEnabled(setting != null && Boolean.TRUE.equals(setting.getEnabled()));
        row.setLoadBalancingEnabled(setting != null && Boolean.TRUE.equals(setting.getLoadBalancingEnabled()));
        row.setIsDefault(isDefault(
                predefinedModel.getProviderName(),
                predefinedModel.getModel(),
                defaults.get(predefinedModel.getModelType())));
        return row;
    }

    public CatalogModelView toCatalogRow(ModelEntity model, Map<ModelType, TenantDefaultModelEntity> defaults) {
        CatalogModelView row = new CatalogModelView();
        row.setId(model.getId());
        row.setModel(model.getModelName());
        row.setLabel(model.getModelName());
        row.setModelType(model.getModelType());
        row.setCredentialId(model.getCredentialId());
        row.setEnabled(model.getEnabled());
        row.setSource("custom");
        row.setIsDefault(isDefault(model.getProviderName(), model.getModelName(), defaults.get(model.getModelType())));
        return row;
    }

    private Map<String, List<ParameterRuleView>> defaultRulesByModelType(
            ProviderDefinitionEntity definition, Set<ModelType> supportedTypes) {
        Map<ModelType, List<ModelParameterRule>> rulesByType =
                definitionService.deserializeParameterRules(definition.getDefaultParameterRules());
        Map<String, List<ParameterRuleView>> viewsByType = new LinkedHashMap<>();
        for (ModelType modelType : supportedTypes) {
            viewsByType.put(
                    modelType.name(),
                    rulesByType.getOrDefault(modelType, List.of()).stream()
                            .map(ModelViewMapper::toParameterRuleView)
                            .toList());
        }
        return viewsByType;
    }

    private CatalogModelView toPredefinedModelRow(PredefinedModelEntity model) {
        CatalogModelView view = new CatalogModelView();
        view.setId(model.getId());
        view.setModel(model.getModel());
        view.setLabel(model.getLabel());
        view.setModelType(model.getModelType());
        view.setContextLength(model.getContextLength());
        view.setDimensions(model.getDimensions());
        view.setFeatures(definitionService.deserializeFeatures(model.getFeatures()));
        view.setSource("predefined");
        if (model.getParameterRules() != null) {
            view.setParameterRules(definitionService.deserializeRuleList(model.getParameterRules()).stream()
                    .map(ModelViewMapper::toParameterRuleView)
                    .toList());
        }
        return view;
    }

    private void addTenantState(
            ProviderView view,
            String tenantId,
            String providerName,
            List<String> secretFieldNames,
            Set<String> predefinedTriples) {
        ProviderCredentialEntity credential = snapshot == null
                ? credentialService.findPrimary(tenantId, providerName)
                : snapshot.credentials().get(providerName);
        view.setCredentialConfigured(credential != null);
        if (credential != null) {
            view.setCredentialId(credential.getId());
            view.setCredentialMasked(credentialService.maskedView(credential, secretFieldNames));
        }
        // Count only genuinely custom models. A materialized copy of a predefined
        // model (created lazily by ModelService#findOrMaterialize when a default is
        // resolved) is not an extra installed model — including it would inflate the
        // count and contradict the deduplicated catalog the popover shows.
        List<ModelEntity> customModels = (snapshot == null ? modelService.list(tenantId) : snapshot.models())
                .stream()
                        .filter(model -> providerName.equalsIgnoreCase(model.getProviderName()))
                        .filter(model -> !predefinedTriples.contains(model.getModelName() + "::"
                                + model.getModelType().name()))
                        .toList();
        view.setInstalledModelCount(customModels.size());

        // How many models currently have their switch ON — the same gate the
        // default-model dropdown applies. Predefined rows count when the settings
        // table says enabled (missing row = disabled); custom rows when their own
        // enabled flag is true. Lets the provider card show a truthful
        // "N 已启用 / M 个模型" without opening the popover.
        Map<String, ProviderModelSettingEntity> settings = snapshot == null
                ? settingsService.settingIndex(tenantId, providerName)
                : snapshot.settings().getOrDefault(providerName, Map.of());
        long enabledCount = customModels.stream()
                .filter(model -> Boolean.TRUE.equals(model.getEnabled()))
                .count();
        for (String triple : predefinedTriples) {
            ProviderModelSettingEntity setting = settings.get(triple);
            if (setting != null && Boolean.TRUE.equals(setting.getEnabled())) {
                enabledCount++;
            }
        }
        view.setEnabledModelCount(Math.toIntExact(enabledCount));
    }

    private static Set<String> predefinedTriples(List<PredefinedModelEntity> predefinedModels) {
        Set<String> triples = new HashSet<>();
        for (PredefinedModelEntity predefinedModel : predefinedModels) {
            triples.add(predefinedModel.getModel() + "::"
                    + predefinedModel.getModelType().name());
        }
        return triples;
    }

    private static boolean isDefault(String providerName, String modelName, TenantDefaultModelEntity defaultModel) {
        return defaultModel != null
                && modelName.equalsIgnoreCase(defaultModel.getModelName())
                && providerName.equalsIgnoreCase(defaultModel.getProviderName());
    }
}
