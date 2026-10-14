package io.aigoodle.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.entity.WorkflowRunEntity;
import io.aigoodle.workflow.mapper.WorkflowRunMapper;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowRunStoreTest {

    private final WorkflowRunMapper workflowRunMapper = mock(WorkflowRunMapper.class);
    private final WorkflowRunStore runStore = new WorkflowRunStore(workflowRunMapper);

    @Test
    void recordsStoredRunWithItsWorkflowIdentity() {
        WorkflowRunResult result =
                WorkflowRunResult.forRun("run-1", new ArrayList<>()).succeed(Map.of("answer", "Done"));

        runStore.recordStoredRun("tenant-a", "workflow-1", "conversation-1", Map.of("question", "Why?"), result);

        WorkflowRunEntity entity = insertedEntity();
        assertThat(entity.getId()).isEqualTo("run-1");
        assertThat(entity.getTenantId()).isEqualTo("tenant-a");
        assertThat(entity.getWorkflowId()).isEqualTo("workflow-1");
        assertThat(entity.getConversationId()).isEqualTo("conversation-1");
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getInputsJson()).contains("question", "Why?");
        assertThat(entity.getOutputsJson()).contains("answer", "Done");
    }

    @Test
    void recordsAdHocRunWithoutInventingAWorkflowIdentity() {
        WorkflowRunResult result =
                WorkflowRunResult.forRun("run-2", new ArrayList<>()).fail("Node failed", Map.of());

        runStore.recordAdHocRun("tenant-a", null, Map.of(), result);

        WorkflowRunEntity entity = insertedEntity();
        assertThat(entity.getWorkflowId()).isNull();
        assertThat(entity.getTenantId()).isEqualTo("tenant-a");
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getError()).isEqualTo("Node failed");
    }

    @Test
    void observabilityFailureDoesNotReplaceTheWorkflowOutcome() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(workflowRunMapper)
                .insert(any(WorkflowRunEntity.class));
        WorkflowRunResult result =
                WorkflowRunResult.forRun("run-3", new ArrayList<>()).succeed(Map.of());

        assertThatCode(() -> runStore.recordAdHocRun("tenant-a", null, Map.of(), result))
                .doesNotThrowAnyException();
    }

    @Test
    void redactsSecretsAndBoundsPersistedObservabilityPayloads() {
        WorkflowRunResult result = WorkflowRunResult.forRun("run-private", new ArrayList<>())
                .succeed(Map.of("accessToken", "must-not-leak", "text", "x".repeat(800)));
        runStore.recordAdHocRun("tenant-a", null, Map.of("password", "must-not-leak", "question", "safe"), result);

        WorkflowRunEntity entity = insertedEntity();
        assertThat(entity.getInputsJson()).contains("[REDACTED]").doesNotContain("must-not-leak");
        assertThat(entity.getOutputsJson()).contains("[REDACTED]").doesNotContain("must-not-leak");
        assertThat(entity.getOutputsJson().length()).isLessThan(700);
    }

    private WorkflowRunEntity insertedEntity() {
        ArgumentCaptor<WorkflowRunEntity> entityCaptor = ArgumentCaptor.forClass(WorkflowRunEntity.class);
        verify(workflowRunMapper).insert(entityCaptor.capture());
        return entityCaptor.getValue();
    }
}
