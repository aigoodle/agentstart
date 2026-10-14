package io.aigoodle.model.service;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.CredentialSchema;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.registry.ModelProviderRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Explicit catalog maintenance; never performs database work on application startup. */
public class ProviderDefinitionSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProviderDefinitionSeeder.class);

    private final ModelProviderRegistry registry;
    private final ProviderDefinitionService definitionService;

    public ProviderDefinitionSeeder(ModelProviderRegistry registry, ProviderDefinitionService definitionService) {
        this.registry = registry;
        this.definitionService = definitionService;
    }

    /** Public so tests can trigger the seed synchronously. */
    public int seed() {
        return seed(registry.all());
    }

    /** Persist only the provider being configured, without loading unrelated catalogs. */
    public int seedProvider(String providerName) {
        return seed(List.of(registry.get(providerName)));
    }

    private int seed(java.util.Collection<ModelProvider> providers) {
        int insertedProviders = 0;
        int insertedModels = 0;
        Set<String> existingProviders = new HashSet<>();
        for (ProviderDefinitionEntity entity :
                definitionService.listOwnedDefinitions(ProviderDefinitionService.SYSTEM_TENANT)) {
            existingProviders.add(entity.getName());
        }
        Set<ModelKey> existingModels = new HashSet<>();
        for (PredefinedModelEntity entity :
                definitionService.listOwnedPredefined(ProviderDefinitionService.SYSTEM_TENANT)) {
            existingModels.add(new ModelKey(entity.getProviderName(), entity.getModel(), entity.getModelType()));
        }
        int sortOrder = 0;
        for (ModelProvider provider : providers) {
            ProviderDefinitionEntity def = toDefinition(provider, sortOrder++);
            if (existingProviders.add(provider.getName())) {
                definitionService.upsert(def);
                insertedProviders++;
            }

            int mSort = 0;
            if (provider.supports(ModelType.VIDEO))
                definitionService.addBuiltinModelType(provider.getName(), ModelType.VIDEO);
            for (PredefinedModel m : provider.predefinedModels()) {
                PredefinedModelEntity row = definitionService.fromMemory(provider.getName(), m, mSort++);
                if (existingModels.add(new ModelKey(provider.getName(), m.getModel(), m.getModelType()))) {
                    definitionService.upsertPredefined(row);
                    insertedModels++;
                }
            }
        }
        if (insertedProviders > 0 || insertedModels > 0) {
            log.info(
                    "Inserted {} missing builtin provider definitions ({} predefined model entries)",
                    insertedProviders,
                    insertedModels);
        }
        return insertedProviders;
    }

    private record ModelKey(String provider, String model, ModelType type) {}

    private ProviderDefinitionEntity toDefinition(ModelProvider provider, int sortOrder) {
        ProviderDefinitionEntity def = new ProviderDefinitionEntity();
        def.setTenantId(ProviderDefinitionService.SYSTEM_TENANT);
        def.setName(provider.getName());
        def.setLabel(provider.getLabel());
        def.setSource("builtin");
        def.setSortOrder(sortOrder);
        def.setEnabled(Boolean.TRUE);
        def.setImplementationKey(provider.implementationKey());
        def.setSupportsRemoteModelListing(provider.supportsRemoteModelListing());

        // Supported model types → JSON array of names.
        Set<ModelType> types = provider.supportedModelTypes();
        List<String> typeNames = new ArrayList<>();
        for (ModelType t : types) typeNames.add(t.name());
        def.setSupportedModelTypes(JsonUtils.toJson(typeNames));

        // Credential schema → JSON array of CredentialField.
        CredentialSchema schema = provider.credentialSchema();
        def.setCredentialSchema(JsonUtils.toJson(schema == null ? List.of() : schema.fields()));

        // Default parameter rules → JSON map<modelType, rules[]>.
        Map<String, List<ModelParameterRule>> rulesByType = new LinkedHashMap<>();
        for (ModelType t : types) {
            List<ModelParameterRule> rules = provider.defaultParameterRules(t);
            if (rules != null && !rules.isEmpty()) {
                rulesByType.put(t.name(), rules);
            }
        }
        if (!rulesByType.isEmpty()) {
            def.setDefaultParameterRules(JsonUtils.toJson(rulesByType));
        }

        // Try to salvage a defaultBaseUrl for OpenAI-compat providers by
        // introspecting the credential schema — the field's defaultValue is
        // typically the vendor's base url.
        if (schema != null) {
            for (var f : schema.fields()) {
                if ("baseUrl".equals(f.getName()) && f.getDefaultValue() != null) {
                    def.setDefaultBaseUrl(String.valueOf(f.getDefaultValue()));
                    break;
                }
            }
        }
        return def;
    }
}
