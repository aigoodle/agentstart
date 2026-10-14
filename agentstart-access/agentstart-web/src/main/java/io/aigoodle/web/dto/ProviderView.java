package io.aigoodle.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.web.dto.model.CatalogModelView;
import io.aigoodle.web.dto.model.CredentialFieldView;
import io.aigoodle.web.dto.model.ParameterRuleView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/** Response view for a model provider and its tenant-specific configuration state. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderView {

    private String id;
    private String name;
    private String label;
    private String description;
    private String icon;
    private String svgIcon;
    private String implementationKey;
    private String defaultBaseUrl;
    private String source;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean supportsRemoteModelListing;
    private Set<ModelType> supportedModelTypes;
    private List<CredentialFieldView> credentialSchema;
    private Map<String, List<ParameterRuleView>> defaultParameterRules;
    private List<CatalogModelView> predefinedModels;
    private Boolean credentialConfigured;
    private String credentialId;
    private Map<String, Object> credentialMasked;
    private Integer installedModelCount;
    private Integer enabledModelCount;
}
