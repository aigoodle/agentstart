package io.aigoodle.rag.query;

import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.spi.QueryPlanner;
import java.util.List;

public final class DirectQueryPlanner implements QueryPlanner {
    @Override
    public String getName() {
        return "DIRECT";
    }

    @Override
    public QueryPlan plan(RagRequest request) {
        return new QueryPlan(
                request.query(),
                List.of(new PlannedQuery("q0", request.query(), PlannedQuery.Type.ORIGINAL, 1.0, null)));
    }
}
