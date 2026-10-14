package io.aigoodle.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentResponseTest {

    @Test
    void createsAResponseForAConversationAndIgnoresNullSteps() {
        AgentResponse response =
                AgentResponse.forConversation("conversation-1").addStep(null).addStep(AgentStep.observation("found"));

        assertThat(response.getConversationId()).isEqualTo("conversation-1");
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().getFirst().getObservation()).isEqualTo("found");
    }

    @Test
    void completedTransitionClearsStaleApprovalAndErrorState() {
        AgentResponse response = AgentResponse.forConversation("conversation-1");
        response.setError("old error");
        response.setPendingApproval(AgentResponse.PendingApproval.forTool("approval-1", "search", "{}"));

        response.complete("Final answer");

        assertThat(response.isCompleted()).isTrue();
        assertThat(response.getText()).isEqualTo("Final answer");
        assertThat(response.getError()).isNull();
        assertThat(response.getPendingApproval()).isNull();
    }

    @Test
    void awaitingApprovalTransitionCarriesTheToolCallAsOneValue() {
        AgentResponse.PendingApproval approval =
                AgentResponse.PendingApproval.forTool("approval-1", "delete_record", "{\"id\":42}");
        AgentResponse response = AgentResponse.forConversation("conversation-1").awaitApproval(approval);

        assertThat(response.getStatus()).isEqualTo(AgentResponse.Status.AWAITING_APPROVAL);
        assertThat(response.getPendingApproval()).isSameAs(approval);
        assertThat(response.getPendingApproval().getToolName()).isEqualTo("delete_record");
    }

    @Test
    void maxIterationTransitionBuildsItsDiagnosticText() {
        AgentResponse response = AgentResponse.forConversation("conversation-1").stopAfterMaxIterations(6);

        assertThat(response.getStatus()).isEqualTo(AgentResponse.Status.MAX_ITERATIONS);
        assertThat(response.getText()).isEqualTo("Stopped after 6 iterations without a final answer.");
    }
}
