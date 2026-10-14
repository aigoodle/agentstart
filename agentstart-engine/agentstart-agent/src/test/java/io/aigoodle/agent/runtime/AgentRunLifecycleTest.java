package io.aigoodle.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import org.junit.jupiter.api.Test;

class AgentRunLifecycleTest {

    @Test
    void persistsOrderedLifecycleAndApprovalPause() {
        InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        AgentDefinition definition = AgentDefinition.builder()
                .id("agent-1")
                .tenantId("tenant-1")
                .name("researcher")
                .build();
        AgentRequest request = AgentRequest.of("research this");

        store.create("run-1", definition, request, "conversation-1");
        store.transition("run-1", AgentRunStatus.RUNNING, null, null);
        AgentResponse response = AgentResponse.forConversation("conversation-1");
        response.setRunId("run-1");
        response.awaitApproval(AgentResponse.PendingApproval.forTool("approval-1", "shell", "{\"command\":\"pwd\"}"));
        store.transition("run-1", AgentRunStatus.WAITING_APPROVAL, response, null);

        AgentRunSnapshot run = store.find("run-1").orElseThrow();
        assertThat(run.status()).isEqualTo(AgentRunStatus.WAITING_APPROVAL);
        assertThat(run.version()).isEqualTo(2);
        assertThat(store.events("run-1", 0, 10))
                .extracting(AgentRunEvent::type)
                .containsExactly("RUN_CREATED", "RUN_RUNNING", "RUN_WAITING_APPROVAL");
    }

    @Test
    void rejectsTransitionsOutOfTerminalState() {
        InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        AgentDefinition definition = AgentDefinition.builder().id("agent-1").build();
        store.create("run-1", definition, AgentRequest.of("hello"), "conversation-1");
        store.transition("run-1", AgentRunStatus.RUNNING, null, null);
        store.transition("run-1", AgentRunStatus.COMPLETED, new AgentResponse(), null);

        assertThatThrownBy(() -> store.transition("run-1", AgentRunStatus.RUNNING, null, null))
                .hasMessageContaining("cannot transition");
    }

    @Test
    void tenantExplicitOperationsCannotObserveOrMutateAnotherTenantRun() {
        InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        store.create(
                "run-a",
                AgentDefinition.builder().id("agent-1").tenantId("tenant-a").build(),
                AgentRequest.of("hello"),
                "conversation-1");

        assertThat(store.find("tenant-b", "run-a")).isEmpty();
        assertThat(store.events("tenant-b", "run-a", 0, 10)).isEmpty();
        assertThatThrownBy(() -> store.transition("tenant-b", "run-a", AgentRunStatus.RUNNING, null, null))
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> store.appendEvent("tenant-b", "run-a", "FORGED", "{}"))
                .hasMessageContaining("not found");
        assertThat(store.find("tenant-a", "run-a")).isPresent();
    }

    @Test
    void sameExternalRunIdHasStructurallyIsolatedTenantStateAndEvents() {
        InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        store.create(
                "shared-run",
                AgentDefinition.builder().id("agent-a").tenantId("tenant-a").build(),
                AgentRequest.of("from a"),
                "conversation-a");
        store.create(
                "shared-run",
                AgentDefinition.builder().id("agent-b").tenantId("tenant-b").build(),
                AgentRequest.of("from b"),
                "conversation-b");

        store.transition("tenant-a", "shared-run", AgentRunStatus.RUNNING, null, null);
        store.appendEvent("tenant-b", "shared-run", "TENANT_B_ONLY", "{}");

        assertThat(store.find("tenant-a", "shared-run").orElseThrow().status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(store.find("tenant-b", "shared-run").orElseThrow().status()).isEqualTo(AgentRunStatus.CREATED);
        assertThat(store.events("tenant-a", "shared-run", 0, 10))
                .extracting(AgentRunEvent::type)
                .containsExactly("RUN_CREATED", "RUN_RUNNING");
        assertThat(store.events("tenant-b", "shared-run", 0, 10))
                .extracting(AgentRunEvent::type)
                .containsExactly("RUN_CREATED", "TENANT_B_ONLY");
        assertThatThrownBy(() -> store.find("shared-run")).hasMessageContaining("Tenant id is required");
    }
}
