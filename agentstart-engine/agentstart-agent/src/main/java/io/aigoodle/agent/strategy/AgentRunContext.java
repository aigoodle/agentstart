package io.aigoodle.agent.strategy;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.agent.hitl.AutoApproveGate;
import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;

/** All resolved collaborators and inputs required to execute one agent turn. */
@Data
@Builder
public class AgentRunContext {

    private static final ApprovalGate DEFAULT_APPROVAL_GATE = new AutoApproveGate();

    private AgentDefinition definition;
    private String query;
    private String conversationId;
    private String runId;

    @Builder.Default
    private Map<String, Object> requestVariables = Map.of();

    private Instant deadline;

    @Builder.Default
    private AtomicInteger modelCalls = new AtomicInteger();

    @Builder.Default
    private AtomicInteger toolCalls = new AtomicInteger();

    @Builder.Default
    private BooleanSupplier active = () -> true;

    @Builder.Default
    private List<AgentMessage> history = List.of();

    private ChatClient chatClient;

    @Builder.Default
    private List<ToolDefinition> tools = List.of();

    @Builder.Default
    private ApprovalGate approvalGate = DEFAULT_APPROVAL_GATE;

    @Builder.Default
    private ToolExecutionGateway toolExecutionGateway = ToolExecutionGateway.direct();

    /** Receives each completed reasoning step when step streaming is enabled. */
    private Consumer<AgentStep> stepListener;

    /** Receives answer deltas when token streaming is enabled. */
    private Consumer<String> tokenListener;

    /** Never exposes a nullable history collection to strategy implementations. */
    public List<AgentMessage> getHistory() {
        return history == null ? List.of() : history;
    }

    /** Never exposes a nullable tool collection to strategy implementations. */
    public List<ToolDefinition> getTools() {
        return tools == null ? List.of() : tools;
    }

    /** Falls back to the starter's opt-in approval policy when no custom gate is supplied. */
    public ApprovalGate getApprovalGate() {
        return approvalGate == null ? DEFAULT_APPROVAL_GATE : approvalGate;
    }

    public ToolExecutionGateway getToolExecutionGateway() {
        return toolExecutionGateway == null ? ToolExecutionGateway.direct() : toolExecutionGateway;
    }

    /** Executes through the shared governance boundary with run identity attached. */
    public Object executeTool(ToolDefinition tool, Map<String, Object> arguments) {
        checkActive();
        claimToolCall();
        AgentDefinition agent = getDefinition();
        return getToolExecutionGateway()
                .execute(
                        tool,
                        arguments,
                        new ToolExecutionContext(
                                runId,
                                agent == null ? null : agent.getTenantId(),
                                requestVariables.get("enterpriseUserId") == null
                                        ? (agent == null ? null : agent.getId())
                                        : String.valueOf(requestVariables.get("enterpriseUserId")),
                                conversationId,
                                requestVariables));
    }

    /** Claims one model boundary before invoking the provider. */
    public int claimModelCall() {
        checkActive();
        int limit = definition == null ? 0 : definition.getMaxModelCalls();
        return claim(modelCalls, limit, true);
    }

    private void claimToolCall() {
        int limit = definition == null ? 0 : definition.getMaxToolCalls();
        claim(toolCalls, limit, false);
    }

    private static int claim(AtomicInteger counter, int limit, boolean model) {
        AtomicInteger effective = counter == null ? new AtomicInteger() : counter;
        while (true) {
            int current = effective.get();
            if (limit > 0 && current >= limit) {
                throw model
                        ? AgentExecutionBudgetExceededException.modelCalls(limit)
                        : AgentExecutionBudgetExceededException.toolCalls(limit);
            }
            if (effective.compareAndSet(current, current + 1)) return current + 1;
        }
    }

    public int modelCallCount() {
        return modelCalls == null ? 0 : modelCalls.get();
    }

    public int toolCallCount() {
        return toolCalls == null ? 0 : toolCalls.get();
    }

    /** Cooperative guard used before model and tool boundaries. */
    public void checkActive() {
        if (Thread.currentThread().isInterrupted() || (active != null && !active.getAsBoolean())) {
            throw AgentRunInterruptedException.cancelled(runId);
        }
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw AgentRunInterruptedException.timedOut(runId);
        }
    }

    public boolean isTokenStreamingEnabled() {
        return tokenListener != null;
    }

    /** Publishes a reasoning step without allowing a broken listener to abort the run. */
    public void publishStep(AgentStep step) {
        Consumer<AgentStep> listener = this.stepListener;
        if (listener == null || step == null) {
            return;
        }
        try {
            listener.accept(step);
        } catch (RuntimeException ignored) {
            // Streaming delivery is best effort by contract.
        }
    }

    /** Publishes a non-empty token delta without interrupting the underlying run. */
    public void publishToken(String tokenDelta) {
        Consumer<String> listener = this.tokenListener;
        if (listener == null || tokenDelta == null || tokenDelta.isEmpty()) {
            return;
        }
        try {
            listener.accept(tokenDelta);
        } catch (RuntimeException ignored) {
            // Streaming delivery is best effort by contract.
        }
    }

    /** @deprecated Use {@link #publishStep(AgentStep)}. */
    @Deprecated(forRemoval = false)
    public void fireStep(AgentStep step) {
        publishStep(step);
    }

    /** @deprecated Use {@link #publishToken(String)}. */
    @Deprecated(forRemoval = false)
    public void fireToken(String tokenDelta) {
        publishToken(tokenDelta);
    }
}
