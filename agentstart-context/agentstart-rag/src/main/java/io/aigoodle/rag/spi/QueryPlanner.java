package io.aigoodle.rag.spi;

import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.api.RagRequest;

public interface QueryPlanner {
    String getName();

    QueryPlan plan(RagRequest request);
}
