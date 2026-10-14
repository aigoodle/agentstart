package io.aigoodle.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.common.util.JsonUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the durable input/output contract stored in {@code goodle_workflows.output}. */
final class WorkflowContractExtractor {

    private WorkflowContractExtractor() {}

    static String extract(JsonNode graph) {
        Map<String, Object> contract = new LinkedHashMap<>();
        List<Object> inputs = new ArrayList<>();
        List<Object> workflowOutputs = new ArrayList<>();
        List<Map<String, Object>> nodeOutputs = new ArrayList<>();
        JsonNode nodes = graph == null ? null : graph.path("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                String type = node.path("type").asText("").toUpperCase();
                JsonNode data = node.path("data");
                if ("START".equals(type)) addAll(inputs, data.path("variables"));
                JsonNode outputs = data.path("output");
                if (!outputs.isArray()) outputs = data.path("outputs");
                if ("END".equals(type)) addAll(workflowOutputs, outputs);
                JsonNode structuredOutput = data.path("structOutput");
                boolean hasOutputs = outputs.isArray() && !outputs.isEmpty();
                boolean hasStructuredOutput = structuredOutput.isObject() && !structuredOutput.isEmpty();
                if (hasOutputs || hasStructuredOutput) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeId", node.path("id").asText());
                    item.put("nodeType", type);
                    item.put("outputs", hasOutputs ? JsonUtils.mapper().convertValue(outputs, List.class) : List.of());
                    if (hasStructuredOutput) {
                        item.put("structuredOutput", JsonUtils.mapper().convertValue(structuredOutput, Map.class));
                    }
                    nodeOutputs.add(item);
                }
            }
        }
        contract.put("inputs", inputs);
        contract.put("outputs", workflowOutputs);
        contract.put("nodeOutputs", nodeOutputs);
        return JsonUtils.toJson(contract);
    }

    private static void addAll(List<Object> target, JsonNode values) {
        if (values != null && values.isArray()) {
            values.forEach(value -> target.add(JsonUtils.mapper().convertValue(value, Object.class)));
        }
    }
}
