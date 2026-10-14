package io.aigoodle.web.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Icon declaration embedded in grouped provider responses. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderDeclarationView {
    private String icon;

    @JsonProperty("svg_icon")
    private String svgIcon;
}
