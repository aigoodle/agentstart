package io.aigoodle.rag.agent;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RagResult;
import io.aigoodle.rag.spi.RagPipeline;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Agent tool: the agent decides when to retrieve, inspects evidence, then may call again. */
public final class KnowledgeSearchTool {
    private final RagPipeline pipeline;

    public KnowledgeSearchTool(RagPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Tool(
            name = "knowledge_search",
            description =
                    "Search one or more knowledge bases. Use this when an answer needs private or factual evidence. Inspect returned evidence; if it is insufficient or contradictory, reformulate the query and call the tool again before answering.")
    public RagResult search(
            @ToolParam(description = "Comma-separated knowledge-base dataset IDs") String datasetIds,
            @ToolParam(description = "A focused retrieval query that preserves exact names, codes and constraints")
                    String query) {
        List<String> ids = Arrays.stream(datasetIds.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        return pipeline.retrieve(
                new RagRequest(UserContextHolder.currentTenantId(), ids, query, null, RagPolicy.defaults(), null));
    }
}
