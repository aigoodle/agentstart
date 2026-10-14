package io.aigoodle.web.support;

import io.aigoodle.workflow.graph.NodeType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Builds the compact node metadata consumed by the visual workflow palette. */
public final class WorkflowNodeCatalog {

    private WorkflowNodeCatalog() {}

    public static List<Map<String, Object>> entries() {
        return Arrays.stream(NodeType.values())
                .map(nodeType -> Map.<String, Object>of(
                        "name", nodeType.name(),
                        "category", categoryOf(nodeType)))
                .toList();
    }

    private static String categoryOf(NodeType nodeType) {
        return switch (nodeType) {
            case START, END, ANSWER, IF_ELSE, ITERATION, HUMAN_INPUT, APPROVAL, WAIT_EVENT, SLEEP_UNTIL -> "flow";
            case TEMPLATE_TRANSFORM, VARIABLE_ASSIGNER, VARIABLE_AGGREGATOR, LIST_OPERATOR, CODE -> "data";
            case HTTP_REQUEST,
                    SERVICE_API,
                    SCHEDULE_TRIGGER,
                    DOCUMENT_EXTRACTOR,
                    KNOWLEDGE_RETRIEVAL,
                    CONNECTOR,
                    TOOL -> "io";
            case LLM, AGENT, QUESTION_CLASSIFIER, PARAMETER_EXTRACTOR, VIDEO_GENERATION -> "llm";
        };
    }
}
