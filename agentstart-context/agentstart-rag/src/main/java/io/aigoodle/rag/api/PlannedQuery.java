package io.aigoodle.rag.api;

public record PlannedQuery(String id, String query, Type type, double weight, String parentQueryId) {
    public enum Type {
        ORIGINAL,
        MULTI_QUERY,
        HYDE,
        STEP_BACK,
        SUB_QUESTION
    }
}
