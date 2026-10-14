package io.aigoodle.rag.api;

import java.util.List;

public record QueryPlan(String originalQuery, List<PlannedQuery> queries) {
    public QueryPlan {
        queries = queries == null ? List.of() : List.copyOf(queries);
    }
}
