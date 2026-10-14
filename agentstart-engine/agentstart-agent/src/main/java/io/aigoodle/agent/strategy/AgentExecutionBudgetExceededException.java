package io.aigoodle.agent.strategy;

/** Raised before an Agent exceeds a configured model or tool execution budget. */
public final class AgentExecutionBudgetExceededException extends RuntimeException {

    private final String resource;
    private final int limit;

    private AgentExecutionBudgetExceededException(String resource, int limit) {
        super("Agent " + resource + " call budget exceeded (limit=" + limit + ")");
        this.resource = resource;
        this.limit = limit;
    }

    public static AgentExecutionBudgetExceededException modelCalls(int limit) {
        return new AgentExecutionBudgetExceededException("model", limit);
    }

    public static AgentExecutionBudgetExceededException toolCalls(int limit) {
        return new AgentExecutionBudgetExceededException("tool", limit);
    }

    public String resource() {
        return resource;
    }

    public int limit() {
        return limit;
    }
}
