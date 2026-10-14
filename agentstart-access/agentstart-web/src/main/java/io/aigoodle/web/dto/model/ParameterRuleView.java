package io.aigoodle.web.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.aigoodle.model.provider.ModelParameterRule;
import lombok.Data;

/** UI-facing validation and rendering rules for one configurable model parameter. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParameterRuleView {
    private String name;
    private String label;
    private ModelParameterRule.Type type;
    private Double min;
    private Double max;
    private Double step;
    private Integer precision;
    private Object defaultValue;
    private String placeholder;
    private String help;
    private boolean required;
}
