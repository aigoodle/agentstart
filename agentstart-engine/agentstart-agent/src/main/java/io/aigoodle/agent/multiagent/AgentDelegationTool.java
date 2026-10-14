package io.aigoodle.agent.multiagent;

import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.tool.AbstractToolDefinition;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Exposes one delegated agent as a model-callable tool. */
public class AgentDelegationTool extends AbstractToolDefinition {

    private static final String INPUT_SCHEMA =
            """
            {
              "type": "object",
              "properties": {
                "input": {
                  "type": "string",
                  "description": "The task or question for the delegated agent"
                }
              },
              "required": ["input"]
            }
            """;

    private final String toolName;
    private final String description;
    private final String delegatedAgentId;
    private final BiFunction<String, AgentRequest, AgentResponse> agentRunner;

    public AgentDelegationTool(
            String toolName,
            String description,
            String delegatedAgentId,
            BiFunction<String, AgentRequest, AgentResponse> agentRunner) {
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.delegatedAgentId = Objects.requireNonNull(delegatedAgentId, "delegatedAgentId must not be null");
        this.agentRunner = Objects.requireNonNull(agentRunner, "agentRunner must not be null");
    }

    @Override
    public String name() {
        return toolName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String inputSchema() {
        return INPUT_SCHEMA;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String delegatedTask = delegatedTask(arguments);
        AgentResponse delegationResponse = agentRunner.apply(delegatedAgentId, AgentRequest.of(delegatedTask));
        return completedText(delegationResponse);
    }

    private String delegatedTask(Map<String, Object> arguments) {
        String legacyQuery = stringArgument(arguments, "query");
        String task = stringArgument(arguments, "input", legacyQuery);
        if (task == null || task.isBlank()) {
            throw new PlatformException(
                    "delegation_input_required", "Delegation tool '" + toolName + "' requires a non-blank input", null);
        }
        return task;
    }

    private String completedText(AgentResponse response) {
        if (response == null) {
            throw delegationFailed("Delegated agent returned no response");
        }
        if (response.getStatus() == AgentResponse.Status.COMPLETED) {
            return response.getText() == null ? "" : response.getText();
        }
        if (response.getStatus() == AgentResponse.Status.AWAITING_APPROVAL) {
            throw new PlatformException(
                    "delegation_awaiting_approval",
                    "Delegated agent " + delegatedAgentId + " is awaiting approval",
                    null);
        }

        String details = response.getError() == null || response.getError().isBlank()
                ? String.valueOf(response.getStatus())
                : response.getError();
        throw delegationFailed(details);
    }

    private PlatformException delegationFailed(String details) {
        return new PlatformException(
                "delegation_failed", "Delegated agent " + delegatedAgentId + " failed: " + details, null);
    }
}
