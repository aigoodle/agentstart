package io.aigoodle.model.service;

import java.util.HashMap;
import java.util.Map;

/** Applies credential PATCH semantics without discarding secrets omitted by the UI. */
final class CredentialPatchMerger {

    private CredentialPatchMerger() {}

    static Map<String, Object> merge(Map<String, Object> existingCredentials, Map<String, Object> patch) {
        Map<String, Object> mergedCredentials =
                existingCredentials == null ? new HashMap<>() : new HashMap<>(existingCredentials);
        if (patch == null) {
            return mergedCredentials;
        }
        patch.forEach((fieldName, value) -> {
            if (isSupplied(value)) {
                mergedCredentials.put(fieldName, value);
            }
        });
        return mergedCredentials;
    }

    private static boolean isSupplied(Object value) {
        return value != null && (!(value instanceof String text) || !text.isEmpty());
    }
}
