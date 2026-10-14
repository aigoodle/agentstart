package io.aigoodle.completion.service;

import io.aigoodle.workflow.engine.WorkflowRunResult;
import java.util.List;
import java.util.Map;

/** Selects the human-facing answer from a workflow's output variables. */
final class WorkflowAnswerExtractor {

    private static final List<String> PREFERRED_OUTPUTS = List.of("answer", "text", "output", "result");

    private WorkflowAnswerExtractor() {}

    static String extract(WorkflowRunResult result) {
        Map<String, Object> outputs = result.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        for (String outputName : PREFERRED_OUTPUTS) {
            Object value = outputs.get(outputName);
            if (value != null) {
                return value.toString();
            }
        }
        if (outputs.size() == 1) {
            Object value = outputs.values().iterator().next();
            return value == null ? "" : value.toString();
        }
        return outputs.toString();
    }
}
