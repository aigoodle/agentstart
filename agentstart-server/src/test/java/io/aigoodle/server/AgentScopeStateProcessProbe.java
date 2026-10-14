package io.aigoodle.server;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.runtime.ModelInstance;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.runtime.agentscope.DefaultAgentScopeAgentFactory;
import io.aigoodle.runtime.agentscope.JdbcAgentScopeStateStore;
import io.aigoodle.skill.service.SkillResolver;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import reactor.core.publisher.Flux;

/** Child-process fixture: no in-memory state is shared between pause and resume. */
public final class AgentScopeStateProcessProbe {
    public static void main(String[] args) {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:file:" + args[1], "sa", ""));
        var executions = new AtomicInteger();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("Must stream");
            }

            public Flux<ChatResponse> stream(Prompt prompt) {
                boolean hasResult = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
                var output = hasResult
                        ? new AssistantMessage("process restored answer")
                        : AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "process-call", "function", "echo", "{\"value\":42}")))
                                .build();
                return Flux.just(new ChatResponse(List.of(new Generation(output))));
            }
        };
        var catalog = org.mockito.Mockito.mock(ModelService.class);
        org.mockito.Mockito.when(catalog.getDefaultInstance("default", ModelType.LLM))
                .thenReturn(ModelInstance.forChat(
                        "offline",
                        ModelEndpoint.builder()
                                .modelName("offline")
                                .modelType(ModelType.LLM)
                                .build(),
                        model));
        ToolDefinition tool = new ToolDefinition() {
            public String name() {
                return "echo";
            }

            public String description() {
                return "Echo";
            }

            public Object execute(Map<String, Object> arguments) {
                executions.incrementAndGet();
                return arguments.get("value");
            }
        };
        var factory = new DefaultAgentScopeAgentFactory(
                catalog,
                new ToolRegistry(List.of(tool), List.of()),
                SkillResolver.none(),
                new JdbcAgentScopeStateStore(jdbc));
        var context = RuntimeContext.builder()
                .userId("process-user")
                .sessionId("process-session")
                .put("tenantId", "default")
                .put("runId", "process-run")
                .build();
        var definition = AgentDefinition.builder()
                .id("process-agent")
                .approvalRequiredTools(Set.of("echo"))
                .build();
        try (var agent = factory.create(definition, AgentRequest.of("echo"), context)) {
            if (args[0].equals("pause")) {
                var events = agent.streamEvents("echo", context).collectList().block();
                if (events.stream().noneMatch(RequireUserConfirmEvent.class::isInstance) || executions.get() != 0)
                    throw new AssertionError("Pause did not protect tool execution");
                System.out.println("PROCESS_PAUSED");
            } else {
                var call = ToolUseBlock.builder()
                        .id("process-call")
                        .name("echo")
                        .input(Map.of("value", 42))
                        .content("{\"value\":42}")
                        .build();
                var confirmation = Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text("").build()))
                        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(new ConfirmResult(true, call))))
                        .build();
                var events =
                        agent.streamEvents(confirmation, context).collectList().block();
                boolean completed = events.stream()
                        .filter(AgentResultEvent.class::isInstance)
                        .map(AgentResultEvent.class::cast)
                        .anyMatch(event -> "process restored answer"
                                .equals(event.getResult().getTextContent()));
                if (!completed || executions.get() != 1) throw new AssertionError("Process recovery failed");
                System.out.println("PROCESS_RECOVERED");
            }
        }
    }
}
