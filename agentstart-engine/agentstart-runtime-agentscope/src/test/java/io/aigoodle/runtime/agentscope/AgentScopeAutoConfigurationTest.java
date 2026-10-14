package io.aigoodle.runtime.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentScopeAutoConfigurationTest {
    @Test
    void automaticFactoryUsesTheHostToolCatalogForDelegates() {
        var models = org.mockito.Mockito.mock(ModelService.class);
        var chat = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class);
        org.mockito.Mockito.when(models.getDefaultInstance("default", io.aigoodle.model.enums.ModelType.LLM))
                .thenReturn(io.aigoodle.model.runtime.ModelInstance.forChat(
                        "offline",
                        io.aigoodle.model.provider.ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(io.aigoodle.model.enums.ModelType.LLM)
                                .build(),
                        chat));
        var resolved = new java.util.concurrent.atomic.AtomicBoolean();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentScopeFactoryAutoConfiguration.class))
                .withBean(ModelService.class, () -> models)
                .withBean(ToolRegistry.class, () -> new ToolRegistry(java.util.List.of(), java.util.List.of()))
                .withBean(io.aigoodle.agent.service.AgentToolCatalog.class, () -> (definition, runner) -> {
                    assertThat(definition.getDelegateAgentIds()).containsExactly("child");
                    resolved.set(true);
                    return java.util.List.of();
                })
                .run(context -> {
                    var sdkContext = io.agentscope.core.agent.RuntimeContext.builder()
                            .userId("user")
                            .sessionId("session")
                            .put("tenantId", "default")
                            .build();
                    try (var agent = context.getBean(AgentScopeAgentFactory.class)
                            .create(
                                    io.aigoodle.agent.api.AgentDefinition.builder()
                                            .id("parent")
                                            .delegateAgentIds(java.util.List.of("child"))
                                            .build(),
                                    io.aigoodle.agent.api.AgentRequest.of("task"),
                                    sdkContext)) {
                        assertThat(resolved.get()).isTrue();
                    }
                });
    }

    @Test
    void sdkAnnotationToolsGenerateSchemasAlongsideSpringAi() {
        var toolkit = new io.agentscope.core.tool.Toolkit();
        toolkit.registerTool(new AnnotatedEcho());
        assertThat(toolkit.getToolSchemas().getFirst().getParameters()).containsKey("properties");
    }

    public static final class AnnotatedEcho {
        @io.agentscope.core.tool.Tool(name = "annotated_echo", description = "Echo")
        public String echo(@io.agentscope.core.tool.ToolParam(name = "input", description = "Text") String input) {
            return input;
        }
    }

    @Test
    void explicitJdbcConfigurationCannotSilentlyFallBackToMemory() {
        var configured = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AgentScopeFactoryAutoConfiguration.class,
                        AgentScopeStateAutoConfiguration.class,
                        AgentScopeAutoConfiguration.class))
                .withBean(ModelService.class, () -> org.mockito.Mockito.mock(ModelService.class))
                .withBean(ToolRegistry.class, () -> new ToolRegistry(java.util.List.of(), java.util.List.of()));
        configured
                .withPropertyValues("spring-agent.agentscope.state-store=jdbc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "AgentScope JDBC state store requires a JdbcTemplate or a host AgentStateStore");
                });
        configured
                .withPropertyValues("spring-agent.agentscope.state-store=typo")
                .run(context -> assertThat(context).hasFailed());
        configured
                .withPropertyValues("spring-agent.agentscope.state-store=jdbc")
                .withBean(
                        org.springframework.jdbc.core.JdbcTemplate.class,
                        () -> org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(io.agentscope.core.state.AgentStateStore.class);
                    assertThat(context.getBean(io.agentscope.core.state.AgentStateStore.class))
                            .isInstanceOf(JdbcAgentScopeStateStore.class);
                    assertThat(context).hasSingleBean(AgentScopeRuntime.class);
                });
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AgentScopeAutoConfiguration.class));

    @Test
    void installsDefaultFactoryAfterCatalogsAndBacksOffForCustomFactory() {
        var configured = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AgentScopeAutoConfiguration.class, AgentScopeFactoryAutoConfiguration.class))
                .withBean(ModelService.class, () -> org.mockito.Mockito.mock(ModelService.class))
                .withBean(ToolRegistry.class, () -> new ToolRegistry(java.util.List.of(), java.util.List.of()));
        configured.run(context -> {
            assertThat(context).hasSingleBean(AgentScopeAgentFactory.class).hasSingleBean(AgentScopeRuntime.class);
            assertThat(context.getBean(AgentScopeAgentFactory.class)).isInstanceOf(DefaultAgentScopeAgentFactory.class);
        });
        AgentScopeAgentFactory custom = (definition, request, context) -> {
            throw new AssertionError("not executed");
        };
        configured
                .withBean("custom", AgentScopeAgentFactory.class, () -> custom)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(AgentScopeAgentFactory.class)
                            .hasSingleBean(AgentScopeRuntime.class);
                    assertThat(context.getBean(AgentScopeAgentFactory.class)).isSameAs(custom);
                });
    }

    @Test
    void exposesNamedBackendOnlyWhenTheHostSuppliesAnAgentFactory() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AgentScopeRuntime.class));
        runner.withBean("default", AgentScopeAgentFactory.class, () -> (definition, request, runtimeContext) -> {
                    throw new AssertionError("not executed");
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentScopeRuntime.class);
                    AgentRuntimeRegistry registry = new AgentRuntimeRegistry(
                            (definition, request, steps, tokens) -> AgentResponse.forConversation("native"),
                            java.util.List.of(context.getBean(AgentScopeRuntime.class)));
                    assertThat(registry.available("AGENTSCOPE")).isTrue();
                });
    }
}
