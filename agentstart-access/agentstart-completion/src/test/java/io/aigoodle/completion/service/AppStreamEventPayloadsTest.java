package io.aigoodle.completion.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.agent.api.AgentResponse;
import org.junit.jupiter.api.Test;

class AppStreamEventPayloadsTest {

    @Test
    void approvalEventCarriesDurableRunIdentity() {
        AgentResponse response = AgentResponse.forConversation("conversation-1");
        response.setRunId("run-1");
        response.awaitApproval(
                AgentResponse.PendingApproval.forTool("approval-1", "calculator", "{\"expression\":\"2+2\"}"));

        assertThat(AppStreamEventPayloads.approvalRequired("task-1", response))
                .containsEntry("task_id", "task-1")
                .containsEntry("run_id", "run-1")
                .containsEntry("conversation_id", "conversation-1")
                .containsKey("approval");
    }
}
