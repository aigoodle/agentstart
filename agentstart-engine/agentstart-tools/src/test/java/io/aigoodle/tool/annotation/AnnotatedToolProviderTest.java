package io.aigoodle.tool.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.aigoodle.tool.ToolDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;

class AnnotatedToolProviderTest {

    @Test
    void discoversSchemaAndInvokesSpringAiToolMethod() {
        @SuppressWarnings("unchecked")
        ObjectProvider<io.aigoodle.tool.ToolRegistry> registry = mock(ObjectProvider.class);
        AnnotatedToolProvider provider = new AnnotatedToolProvider(registry);

        provider.postProcessAfterInitialization(new GreetingTools(), "greetingTools");

        assertThat(provider.getTools()).hasSize(1);
        ToolDefinition tool = provider.getTools().getFirst();
        assertThat(tool.name()).isEqualTo("greet_person");
        assertThat(tool.description()).isEqualTo("Greets one person");
        assertThat(tool.inputSchema()).contains("name", "Person name");
        assertThat(tool.execute(Map.of("name", "Ada"))).isEqualTo("Hello Ada");
    }

    static final class GreetingTools {
        @Tool(name = "greet_person", description = "Greets one person")
        String greet(@ToolParam(description = "Person name") String name) {
            return "Hello " + name;
        }
    }
}
