package io.aigoodle.runtime.agentscope;

import io.aigoodle.common.exception.PlatformException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** One budget per execution, shared by model and parallel tool subscriptions. */
final class AgentScopeCallBudget {
    private final int modelLimit;
    private final int toolLimit;
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicReference<PlatformException> failure = new AtomicReference<>();

    AgentScopeCallBudget(int modelLimit, int toolLimit) {
        this(modelLimit, toolLimit, 0, 0);
    }

    AgentScopeCallBudget(int modelLimit, int toolLimit, int usedModels, int usedTools) {
        if (usedModels < 0 || usedTools < 0) throw new IllegalArgumentException("Invalid persisted call counts");
        modelCalls.set(usedModels);
        toolCalls.set(usedTools);
        this.modelLimit = modelLimit;
        this.toolLimit = toolLimit;
    }

    int modelCount() {
        return modelCalls.get();
    }

    int toolCount() {
        return toolCalls.get();
    }

    void modelCall() {
        consume(modelCalls, modelLimit, "model");
    }

    void modelCall(int maximumIterations) {
        if (modelCalls.get() >= maximumIterations) {
            throw new PlatformException("agent_max_iterations", "Agent iteration limit reached", null);
        }
        modelCall();
    }

    void toolCall() {
        consume(toolCalls, toolLimit, "tool");
    }

    private void consume(AtomicInteger counter, int limit, String kind) {
        var stopped = failure.get();
        if (stopped != null) throw stopped;
        int used = counter.incrementAndGet();
        if (limit > 0 && used > limit) {
            var exceeded = new PlatformException(
                    "agent_budget_exceeded", "Agent " + kind + " call budget exceeded: " + limit, null);
            failure.compareAndSet(null, exceeded);
            throw failure.get();
        }
    }
}
