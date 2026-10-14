package io.aigoodle.model.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.aigoodle.model.enums.ModelType;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for registering or updating a model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @JsonCreator(mode = JsonCreator.Mode.DISABLED))
public class ModelRegistration {

    private String tenantId;
    private String providerName;
    private String modelName;
    private ModelType modelType;

    /** Optional reference to a shared provider credential row. */
    private String credentialId;

    /** Inline model-level credentials/overrides (apiKey, baseUrl, dimensions, ...). */
    @Builder.Default
    private Map<String, Object> credentials = new HashMap<>();

    private boolean asDefault;
}
