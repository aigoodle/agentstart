package io.aigoodle.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.entity.PredefinedModelEntity;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.entity.ProviderModelSettingEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.registry.ModelProviderRegistry;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.ProviderCredentialService;
import io.aigoodle.model.service.ProviderDefinitionService;
import io.aigoodle.model.service.ProviderModelSettingsService;
import io.aigoodle.web.dto.model.CatalogModelView;
import io.aigoodle.web.dto.model.GroupedModelView;
import io.aigoodle.web.dto.model.GroupedProviderView;
import io.aigoodle.web.dto.model.ModelParametersView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelCatalogQueryServiceTest {

    @Test
    void partialPersistedCatalogIncludesNewPresetsAndReadsTenantStateOnce() {
        ModelService models = mock(ModelService.class);
        ProviderCredentialService credentials = mock(ProviderCredentialService.class);
        ProviderModelSettingsService settings = mock(ProviderModelSettingsService.class);
        ProviderDefinitionService definitions = mock(ProviderDefinitionService.class);
        var existing = new ProviderDefinitionEntity();
        existing.setName("openai");
        existing.setLabel("OpenAI");
        when(definitions.list("tenant-a")).thenReturn(List.of(existing));
        var registry = new ModelProviderRegistry(List.of(
                io.aigoodle.model.provider.builtin.BuiltinModelProviders.openai(),
                io.aigoodle.model.provider.builtin.BuiltinModelProviders.custom()));
        var queries = new ModelCatalogQueryService(models, registry, credentials, definitions, settings);
        assertThat(queries.providers("tenant-a"))
                .extracting(io.aigoodle.web.dto.ProviderView::getName)
                .containsExactly("openai", "openai-compatible");
        org.mockito.Mockito.verify(models).list("tenant-a");
        org.mockito.Mockito.verify(credentials).listForTenant("tenant-a");
        org.mockito.Mockito.verify(settings).listForTenant("tenant-a");
        org.mockito.Mockito.verify(credentials, org.mockito.Mockito.never())
                .findPrimary(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(settings, org.mockito.Mockito.never())
                .settingIndex(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void exposesOnlyPropertiesDeclaredByParameterRules() {
        ModelService modelService = mock(ModelService.class);
        ModelParameterRule temperature = ModelParameterRule.builder()
                .name("temperature")
                .label("Temperature")
                .type(ModelParameterRule.Type.FLOAT)
                .build();
        when(modelService.parameterRulesFor("tenant-a", "model-1")).thenReturn(List.of(temperature));
        when(modelService.getModelProperties("tenant-a", "model-1"))
                .thenReturn(Map.of("temperature", 0.7, "apiKey", "must-not-leak"));
        ModelCatalogQueryService queryService = new ModelCatalogQueryService(
                modelService,
                mock(ModelProviderRegistry.class),
                mock(ProviderCredentialService.class),
                mock(ProviderDefinitionService.class),
                mock(ProviderModelSettingsService.class));

        ModelParametersView parameterView = queryService.parameters("tenant-a", "model-1");

        assertThat(parameterView.getParameters()).isEqualTo(Map.of("temperature", 0.7));
    }

    /**
     * The "系统默认模型" dropdown must list only switched-on models and never the
     * same model twice — a materialized {@code agent_model} copy of a predefined
     * model (created by ModelService#findOrMaterialize on default resolution)
     * used to surface as a duplicate option.
     */
    @Test
    void groupedModelsByTypeListsOnlyEnabledModelsAndDedupesMaterializedRows() {
        ModelService modelService = mock(ModelService.class);
        ProviderCredentialService credentialService = mock(ProviderCredentialService.class);
        ProviderDefinitionService definitionService = mock(ProviderDefinitionService.class);
        ProviderModelSettingsService settingsService = mock(ProviderModelSettingsService.class);

        ProviderDefinitionEntity openai = new ProviderDefinitionEntity();
        openai.setName("openai");
        openai.setLabel("OpenAI");

        when(definitionService.list("t1")).thenReturn(List.of(openai));
        when(credentialService.findPrimary("t1", "openai")).thenReturn(new ProviderCredentialEntity());

        // Switch state: gpt-4o ON; gpt-4o-mini has no setting row (= disabled).
        ProviderModelSettingEntity enabledSetting = new ProviderModelSettingEntity();
        enabledSetting.setEnabled(Boolean.TRUE);
        when(settingsService.settingIndex("t1", "openai")).thenReturn(Map.of("gpt-4o::LLM", enabledSetting));

        when(definitionService.listPredefined("t1", "openai"))
                .thenReturn(List.of(
                        predefined("openai", "gpt-4o", ModelType.LLM),
                        predefined("openai", "gpt-4o-mini", ModelType.LLM)));

        // Materialized copy of gpt-4o (enabled=true) must NOT re-add the model;
        // the switched-off custom row must stay hidden.
        when(modelService.listByProvider("t1", "openai"))
                .thenReturn(List.of(
                        agentModel("openai", "gpt-4o", ModelType.LLM, true),
                        agentModel("openai", "my-custom-llm", ModelType.LLM, true),
                        agentModel("openai", "switched-off", ModelType.LLM, false)));

        ModelCatalogQueryService queryService = new ModelCatalogQueryService(
                modelService, mock(ModelProviderRegistry.class), credentialService, definitionService, settingsService);

        Map<String, List<GroupedProviderView>> grouped = queryService.groupedModelsByType("t1");

        List<GroupedProviderView> llmProviders = grouped.get("LLM");
        assertThat(llmProviders).hasSize(1);
        List<GroupedModelView> modelList = llmProviders.get(0).getModelList();
        assertThat(modelList).extracting(GroupedModelView::getModelName).containsExactly("gpt-4o", "my-custom-llm");
        // Types without candidates keep their empty buckets.
        assertThat(grouped.get("TEXT_EMBEDDING")).isEmpty();
    }

    /** Providers without a saved credential offer no candidates at all. */
    @Test
    void groupedModelsByTypeSkipsProvidersWithoutCredential() {
        ModelService modelService = mock(ModelService.class);
        ProviderCredentialService credentialService = mock(ProviderCredentialService.class);
        ProviderDefinitionService definitionService = mock(ProviderDefinitionService.class);
        ProviderModelSettingsService settingsService = mock(ProviderModelSettingsService.class);

        ProviderDefinitionEntity openai = new ProviderDefinitionEntity();
        openai.setName("openai");
        when(definitionService.list("t1")).thenReturn(List.of(openai));
        when(credentialService.findPrimary("t1", "openai")).thenReturn(null);

        ModelCatalogQueryService queryService = new ModelCatalogQueryService(
                modelService, mock(ModelProviderRegistry.class), credentialService, definitionService, settingsService);

        Map<String, List<GroupedProviderView>> grouped = queryService.groupedModelsByType("t1");

        assertThat(grouped.values()).allSatisfy(List::isEmpty);
    }

    /**
     * The catalog popover must show one row per model: a materialized
     * {@code agent_model} copy of a predefined model is dropped so users don't
     * see two entries (with two conflicting switches) for the same model.
     */
    @Test
    void catalogHidesMaterializedCopiesOfPredefinedModels() {
        ModelService modelService = mock(ModelService.class);
        ProviderDefinitionService definitionService = mock(ProviderDefinitionService.class);
        ProviderModelSettingsService settingsService = mock(ProviderModelSettingsService.class);

        ProviderModelSettingEntity enabledSetting = new ProviderModelSettingEntity();
        enabledSetting.setEnabled(Boolean.TRUE);
        when(settingsService.settingIndex("t1", "openai")).thenReturn(Map.of("gpt-4o::LLM", enabledSetting));
        when(settingsService.listDefaults("t1")).thenReturn(Map.of());

        when(definitionService.listPredefined("t1", "openai"))
                .thenReturn(List.of(predefined("openai", "gpt-4o", ModelType.LLM)));
        when(modelService.listByProvider("t1", "openai"))
                .thenReturn(List.of(
                        agentModel("openai", "gpt-4o", ModelType.LLM, true),
                        agentModel("openai", "my-custom-llm", ModelType.LLM, true)));

        ModelCatalogQueryService queryService = new ModelCatalogQueryService(
                modelService,
                mock(ModelProviderRegistry.class),
                mock(ProviderCredentialService.class),
                definitionService,
                settingsService);

        List<CatalogModelView> catalog = queryService.catalog("openai", "t1");

        assertThat(catalog).extracting(CatalogModelView::getModel).containsExactly("gpt-4o", "my-custom-llm");
        assertThat(catalog.get(0).getSource()).isEqualTo("predefined");
        assertThat(catalog.get(0).getEnabled()).isTrue();
        assertThat(catalog.get(1).getSource()).isEqualTo("custom");
    }

    private static PredefinedModelEntity predefined(String providerName, String model, ModelType modelType) {
        PredefinedModelEntity entity = new PredefinedModelEntity();
        entity.setProviderName(providerName);
        entity.setModel(model);
        entity.setLabel(model);
        entity.setModelType(modelType);
        return entity;
    }

    private static ModelEntity agentModel(String providerName, String modelName, ModelType modelType, boolean enabled) {
        ModelEntity entity = new ModelEntity();
        entity.setProviderName(providerName);
        entity.setModelName(modelName);
        entity.setModelType(modelType);
        entity.setEnabled(enabled);
        return entity;
    }
}
