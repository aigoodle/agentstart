package io.aigoodle.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.common.util.JsonUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@AutoConfiguration
@ConditionalOnClass(JsonMapperBuilderCustomizer.class)
public class JacksonTreeAutoConfiguration {
    /**
     * Bridges the Jackson 2 tree model used by persistence/MyBatis-Plus with
     * Spring Boot 4's Jackson 3 HTTP codecs. Without this bridge, request
     * bodies containing a workflow graph cannot be decoded and graph responses
     * are serialized as JsonNode implementation metadata instead of JSON.
     */
    @Bean
    public JsonMapperBuilderCustomizer springAgentJackson2TreeBridge() {
        return builder -> {
            SimpleModule bridge = new SimpleModule("spring-agent-jackson2-tree-bridge");
            bridge.addSerializer(JsonNode.class, new ValueSerializer<>() {
                @Override
                public void serialize(
                        JsonNode value,
                        tools.jackson.core.JsonGenerator generator,
                        tools.jackson.databind.SerializationContext context)
                        throws tools.jackson.core.JacksonException {
                    generator.writeRawValue(value.toString());
                }
            });
            bridge.addDeserializer(JsonNode.class, new ValueDeserializer<>() {
                @Override
                public JsonNode deserialize(
                        tools.jackson.core.JsonParser parser, tools.jackson.databind.DeserializationContext context)
                        throws tools.jackson.core.JacksonException {
                    tools.jackson.databind.JsonNode tree = context.readTree(parser);
                    return JsonUtils.readTree(tree.toString());
                }
            });
            builder.addModule(bridge);
        };
    }
}
