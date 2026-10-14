package io.aigoodle.web.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.aigoodle.model.provider.CredentialField;
import lombok.Data;

/** Describes one provider credential form field exposed to administration clients. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialFieldView {
    private String name;
    private String label;
    private CredentialField.Type type;
    private boolean required;
    private boolean secret;
    private String defaultValue;
    private String placeholder;
}
