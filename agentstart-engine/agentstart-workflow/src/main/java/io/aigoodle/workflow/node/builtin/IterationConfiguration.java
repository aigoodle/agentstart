package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized variable names and failure behavior for one iteration node. */
record IterationConfiguration(
        String itemVariable, String indexVariable, String outputVariable, boolean continueOnError) {

    private static final String DEFAULT_ITEM_VARIABLE = "item";
    private static final String DEFAULT_INDEX_VARIABLE = "index";
    private static final String DEFAULT_OUTPUT_VARIABLE = "output";

    static IterationConfiguration from(NodeDef node) {
        return new IterationConfiguration(
                configuredName(node.getString("itemKey"), DEFAULT_ITEM_VARIABLE),
                configuredName(node.getString("indexKey"), DEFAULT_INDEX_VARIABLE),
                configuredName(node.getString("outputKey"), DEFAULT_OUTPUT_VARIABLE),
                Boolean.parseBoolean(node.getString("continueOnError", "false")));
    }

    Map<String, Object> inputsFor(Object item, int index) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(itemVariable, item);
        inputs.put(indexVariable, index);
        return inputs;
    }

    private static String configuredName(String configuredName, String defaultName) {
        return configuredName == null || configuredName.isBlank() ? defaultName : configuredName.trim();
    }
}
