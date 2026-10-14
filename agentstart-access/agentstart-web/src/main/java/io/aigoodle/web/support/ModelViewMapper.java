package io.aigoodle.web.support;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.ProviderCredentialEntity;
import io.aigoodle.model.entity.ProviderDefinitionEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.CredentialField;
import io.aigoodle.model.provider.ModelParameterRule;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.provider.PredefinedModel;
import io.aigoodle.model.provider.RemoteModel;
import io.aigoodle.web.dto.model.CatalogModelView;
import io.aigoodle.web.dto.model.CredentialFieldView;
import io.aigoodle.web.dto.model.GroupedModelView;
import io.aigoodle.web.dto.model.GroupedProviderView;
import io.aigoodle.web.dto.model.ParameterRuleView;
import io.aigoodle.web.dto.model.ProviderCredentialView;
import io.aigoodle.web.dto.model.ProviderDeclarationView;
import io.aigoodle.web.dto.model.RemoteModelView;
import java.util.List;
import java.util.Map;

/**
 * Converts model-domain objects into strongly typed views exposed by the web API.
 *
 * <p>The mapper is deliberately stateless: tenant-aware enrichment and persistence
 * lookups belong to the controller-facing assembler, while this class only translates
 * values. Keeping those concerns separate makes the response contract readable and
 * allows it to be tested without starting Spring.</p>
 */
public final class ModelViewMapper {

    private ModelViewMapper() {}

    public static ProviderDefinitionEntity toProviderDefinition(Map<String, Object> payload) {
        ProviderDefinitionEntity definition = new ProviderDefinitionEntity();
        definition.setName((String) payload.get("name"));
        definition.setLabel(
                payload.get("label") == null ? (String) payload.get("name") : (String) payload.get("label"));
        definition.setDescription((String) payload.get("description"));
        definition.setIcon((String) payload.get("icon"));
        definition.setSvgIcon((String) payload.get("svgIcon"));
        definition.setImplementationKey((String) payload.get("implementationKey"));
        definition.setDefaultBaseUrl((String) payload.get("defaultBaseUrl"));
        definition.setSource((String) payload.get("source"));
        if (payload.get("sortOrder") instanceof Number sortOrder) {
            definition.setSortOrder(sortOrder.intValue());
        }
        if (payload.get("enabled") instanceof Boolean enabled) {
            definition.setEnabled(enabled);
        }
        if (payload.get("supportsRemoteModelListing") instanceof Boolean supportsRemoteListing) {
            definition.setSupportsRemoteModelListing(supportsRemoteListing);
        }
        if (payload.containsKey("supportedModelTypes")) {
            definition.setSupportedModelTypes(JsonUtils.toJson(payload.get("supportedModelTypes")));
        }
        if (payload.containsKey("credentialSchema")) {
            definition.setCredentialSchema(JsonUtils.toJson(payload.get("credentialSchema")));
        }
        if (payload.containsKey("defaultParameterRules")) {
            definition.setDefaultParameterRules(JsonUtils.toJson(payload.get("defaultParameterRules")));
        }
        return definition;
    }

    public static GroupedProviderView toGroupedProviderView(
            ProviderDefinitionEntity definition, List<GroupedModelView> models) {
        GroupedProviderView view = new GroupedProviderView();
        view.setId(definition.getName());
        view.setProvider(definition.getName());
        view.setLabel(definition.getLabel());
        view.setDescription(definition.getDescription());
        ProviderDeclarationView declaration = new ProviderDeclarationView();
        declaration.setIcon(definition.getIcon());
        declaration.setSvgIcon(definition.getSvgIcon());
        view.setDeclaration(declaration);
        view.setModelList(models);
        return view;
    }

    public static GroupedModelView toGroupedModelView(String providerName, String modelName, ModelType modelType) {
        GroupedModelView view = new GroupedModelView();
        view.setId(providerName + "::" + modelName + "::" + modelType.name());
        view.setProviderName(providerName);
        view.setModelName(modelName);
        view.setModelType(modelType.name());
        return view;
    }

    public static ProviderCredentialView toCredentialView(ModelProvider provider, ProviderCredentialEntity credential) {
        ProviderCredentialView view = new ProviderCredentialView();
        view.setProviderName(provider.getName());
        view.setConfigured(credential != null);
        if (credential != null) {
            view.setCredentialId(credential.getId());
            view.setCredentialName(credential.getCredentialName());
        }
        return view;
    }

    public static CredentialFieldView toCredentialFieldView(CredentialField field) {
        CredentialFieldView view = new CredentialFieldView();
        view.setName(field.getName());
        view.setLabel(field.getLabel());
        view.setType(field.getType());
        view.setRequired(field.isRequired());
        view.setSecret(field.isSecret());
        view.setDefaultValue(field.getDefaultValue());
        view.setPlaceholder(field.getPlaceholder());
        return view;
    }

    public static CatalogModelView toPredefinedModelView(PredefinedModel model) {
        CatalogModelView view = new CatalogModelView();
        view.setModel(model.getModel());
        view.setLabel(model.getLabel());
        view.setModelType(model.getModelType());
        view.setFeatures(model.getFeatures());
        view.setContextLength(model.getContextLength());
        view.setDimensions(model.getDimensions());
        if (model.getParameterRules() != null && !model.getParameterRules().isEmpty()) {
            view.setParameterRules(model.getParameterRules().stream()
                    .map(ModelViewMapper::toParameterRuleView)
                    .toList());
        }
        return view;
    }

    public static ParameterRuleView toParameterRuleView(ModelParameterRule rule) {
        ParameterRuleView view = new ParameterRuleView();
        view.setName(rule.getName());
        view.setLabel(rule.getLabel());
        view.setType(rule.getType());
        view.setMin(rule.getMin());
        view.setMax(rule.getMax());
        view.setStep(rule.getStep());
        view.setPrecision(rule.getPrecision());
        view.setDefaultValue(rule.getDefaultValue());
        view.setPlaceholder(rule.getPlaceholder());
        view.setHelp(rule.getHelp());
        view.setRequired(rule.isRequired());
        return view;
    }

    public static RemoteModelView toRemoteModelView(RemoteModel model) {
        RemoteModelView view = new RemoteModelView();
        view.setModelId(model.getModelId());
        view.setLabel(model.getLabel());
        view.setModelType(model.getModelType());
        view.setContextLength(model.getContextLength());
        view.setDimensions(model.getDimensions());
        view.setFeatures(model.getFeatures());
        view.setOwnedBy(model.getOwnedBy());
        view.setTypeInferred(model.isTypeInferred());
        return view;
    }
}
