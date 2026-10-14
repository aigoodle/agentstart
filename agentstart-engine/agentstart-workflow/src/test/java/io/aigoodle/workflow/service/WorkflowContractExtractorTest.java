package io.aigoodle.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.common.util.JsonUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowContractExtractorTest {

    @Test
    void persistsStartEndAndEveryNodeOutputDefinition() {
        String contractJson = WorkflowContractExtractor.extract(
                JsonUtils.readTree(
                        """
                {
                  "nodes": [
                    {"id":"start","type":"START","data":{"variables":[{"name":"topic","type":"string"}],"output":[]}},
                    {"id":"llm","type":"LLM","data":{"output":[{"name":"text","type":"string"}]}},
                    {"id":"end","type":"END","data":{"output":[{"name":"answer","type":"string"}]}}
                  ],
                  "edges": []
                }
                """));

        Map<String, Object> contract = JsonUtils.parseMap(contractJson);
        assertThat((List<?>) contract.get("inputs"))
                .extracting(String::valueOf)
                .anyMatch(value -> value.contains("topic"));
        assertThat((List<?>) contract.get("outputs"))
                .extracting(String::valueOf)
                .anyMatch(value -> value.contains("answer"));
        assertThat((List<?>) contract.get("nodeOutputs")).hasSize(2);
    }
}
