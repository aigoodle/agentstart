package io.aigoodle.model.provider;

import java.util.List;

/**
 * The ordered set of {@link CredentialField}s a provider exposes for configuration.
 */
public record CredentialSchema(List<CredentialField> fields) {

    public CredentialSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public List<String> secretFieldNames() {
        return fields.stream()
                .filter(CredentialField::isSecret)
                .map(CredentialField::getName)
                .toList();
    }

    public static CredentialSchema of(CredentialField... fields) {
        return new CredentialSchema(List.of(fields));
    }
}
