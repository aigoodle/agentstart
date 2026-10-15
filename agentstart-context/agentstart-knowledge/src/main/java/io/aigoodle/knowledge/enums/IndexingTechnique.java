package io.aigoodle.knowledge.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/** Supported dataset indexing techniques. */
public enum IndexingTechnique {
    /** Embedding-based dense vector index. */
    HIGH_QUALITY;

    /** Accept the retired client value using the same mapping as the schema migration. */
    @JsonCreator
    public static IndexingTechnique fromJson(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "ECONOMY".equals(normalized) ? HIGH_QUALITY : valueOf(normalized);
    }
}
