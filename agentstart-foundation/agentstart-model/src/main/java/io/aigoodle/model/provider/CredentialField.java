package io.aigoodle.model.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One field a provider needs in order to be configured (rendered by a front end,
 * validated by the service layer). Mirrors Dify's {@code CredentialFormSchema}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialField {

    public enum Type {
        TEXT,
        SECRET,
        SELECT,
        NUMBER,
        BOOLEAN
    }

    private String name;
    private String label;
    private Type type;
    private boolean required;
    private String placeholder;
    private String defaultValue;

    /** Marks values that must be encrypted at rest. */
    public boolean isSecret() {
        return type == Type.SECRET;
    }

    public static CredentialField secret(String name, String label, boolean required) {
        return CredentialField.builder()
                .name(name)
                .label(label)
                .type(Type.SECRET)
                .required(required)
                .build();
    }

    public static CredentialField text(String name, String label, boolean required) {
        return CredentialField.builder()
                .name(name)
                .label(label)
                .type(Type.TEXT)
                .required(required)
                .build();
    }
}
