package io.aigoodle.web.dto.model;

import lombok.Data;

/** One selectable model option inside a provider group. */
@Data
public class GroupedModelView {
    private String id;
    private String providerName;
    private String modelName;
    private String modelType;
}
