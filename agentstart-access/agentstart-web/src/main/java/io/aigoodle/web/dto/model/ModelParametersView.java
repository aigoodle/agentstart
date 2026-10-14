package io.aigoodle.web.dto.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

/** Parameter-editor payload containing typed rules and dynamic configured values. */
@Data
public class ModelParametersView {
    private List<ParameterRuleView> rules;
    /** Values are keyed by provider-defined parameter names, so this part is intentionally dynamic. */
    private Map<String, Object> parameters;
}
