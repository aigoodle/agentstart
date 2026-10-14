package io.aigoodle.web.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/** Provider group used by the default-model selector. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupedProviderView {
    private String id;
    private String provider;
    private String label;
    private String description;
    private ProviderDeclarationView declaration;
    private List<GroupedModelView> modelList;
}
