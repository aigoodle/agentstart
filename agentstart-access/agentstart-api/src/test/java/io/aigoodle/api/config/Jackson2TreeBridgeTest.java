package io.aigoodle.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.api.workflow.WorkflowRunRequest;
import io.aigoodle.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class Jackson2TreeBridgeTest {

    private final JsonMapper mapper = mapperWithBridge();

    @Test
    void decodesJackson2TreeFromBoot4RequestBody() {
        WorkflowRunRequest request = mapper.readValue(
                """
                {"workflowId":"flow","graph":{"nodes":[{"id":"start"}],"edges":[]}}
                """,
                WorkflowRunRequest.class);

        assertThat(request.getGraph().path("nodes").get(0).path("id").asText()).isEqualTo("start");
    }

    @Test
    void encodesJackson2TreeAsOriginalJsonShape() {
        JsonNode graph = JsonUtils.readTree(
                """
                {"nodes":[{"id":"start"}],"edges":[],"viewport":{"zoom":1.25}}
                """);

        tools.jackson.databind.JsonNode encoded = mapper.readTree(mapper.writeValueAsString(graph));

        assertThat(encoded.path("nodes").get(0).path("id").asString()).isEqualTo("start");
        assertThat(encoded.path("viewport").path("zoom").asDouble()).isEqualTo(1.25);
        assertThat(encoded.has("containerNode")).isFalse();
    }

    private static JsonMapper mapperWithBridge() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonTreeAutoConfiguration().springAgentJackson2TreeBridge().customize(builder);
        return builder.build();
    }
}
