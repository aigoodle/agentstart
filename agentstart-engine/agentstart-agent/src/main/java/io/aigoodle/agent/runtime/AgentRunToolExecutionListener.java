package io.aigoodle.agent.runtime;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.tool.execution.ToolExecutionListener;
import io.aigoodle.tool.execution.ToolExecutionRecord;

/** Projects governed tool executions into an existing durable Agent Run stream. */
public class AgentRunToolExecutionListener implements ToolExecutionListener {
    private final AgentRunStore runStore;

    public AgentRunToolExecutionListener(AgentRunStore runStore) {
        this.runStore = runStore;
    }

    @Override
    public void onExecution(ToolExecutionRecord record) {
        String runId = record.executionId();
        if (runId == null || runId.isBlank() || runStore.find(runId).isEmpty()) return;
        runStore.appendEvent(runId, "TOOL_" + record.status().name(), JsonUtils.toJson(record));
    }
}
