package io.aigoodle.rag.query;

import io.aigoodle.model.service.ModelService;
import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.spi.QueryPlanner;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** LLM-backed planner. A failed model call safely degrades to the original query. */
public final class ModelQueryPlanner implements QueryPlanner {
    private final ModelService modelService;
    private final DirectQueryPlanner fallback = new DirectQueryPlanner();

    public ModelQueryPlanner(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public String getName() {
        return "MODEL";
    }

    @Override
    public QueryPlan plan(RagRequest request) {
        RagPolicy.QueryPlanning config = request.policy().queryPlanning();
        if (config == null
                || config.strategy() == RagPolicy.QueryStrategy.DIRECT
                || config.modelId() == null
                || config.modelId().isBlank()) return fallback.plan(request);
        try {
            String output = modelService
                    .getChatClient(request.tenantId(), config.modelId())
                    .prompt()
                    .system(instruction(config.strategy(), Math.max(1, config.maxQueries())))
                    .user(request.query())
                    .call()
                    .content();
            Set<String> values = new LinkedHashSet<>();
            if (config.includeOriginal()) values.add(request.query());
            if (output != null) {
                output.lines()
                        .map(ModelQueryPlanner::clean)
                        .filter(v -> !v.isBlank())
                        .limit(Math.max(1, config.maxQueries()))
                        .forEach(values::add);
            }
            if (values.isEmpty()) return fallback.plan(request);
            List<PlannedQuery> queries = new ArrayList<>();
            int i = 0;
            for (String value : values)
                queries.add(new PlannedQuery(
                        "q" + i++,
                        value,
                        value.equals(request.query()) ? PlannedQuery.Type.ORIGINAL : type(config.strategy()),
                        1.0,
                        null));
            return new QueryPlan(request.query(), queries);
        } catch (RuntimeException ignored) {
            return fallback.plan(request);
        }
    }

    private static String instruction(RagPolicy.QueryStrategy strategy, int max) {
        return "You plan knowledge-base retrieval. Strategy=" + strategy + ". Return at most " + max
                + " retrieval queries, one per line, with no numbering or explanation. Keep names, IDs and constraints exact.";
    }

    private static String clean(String value) {
        return value.trim().replaceFirst("^[-*\\d.)\\s]+", "").trim();
    }

    private static PlannedQuery.Type type(RagPolicy.QueryStrategy strategy) {
        return switch (strategy) {
            case MULTI_QUERY -> PlannedQuery.Type.MULTI_QUERY;
            case HYDE -> PlannedQuery.Type.HYDE;
            case STEP_BACK -> PlannedQuery.Type.STEP_BACK;
            case DECOMPOSITION -> PlannedQuery.Type.SUB_QUESTION;
            default -> PlannedQuery.Type.ORIGINAL;
        };
    }
}
