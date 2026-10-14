package io.aigoodle.web.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import java.util.Set;
import lombok.Data;

/** Stable API representation of a model discovered from a provider's live catalog. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RemoteModelView {
    private String modelId;
    private String label;
    private ModelType modelType;
    private Integer contextLength;
    private Integer dimensions;
    private Set<ModelFeature> features;
    private String ownedBy;
    private boolean typeInferred;
}
