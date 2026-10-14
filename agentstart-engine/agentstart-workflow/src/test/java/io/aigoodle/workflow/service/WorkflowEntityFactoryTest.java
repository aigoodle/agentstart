package io.aigoodle.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.workflow.entity.WorkflowEntity;
import org.junit.jupiter.api.Test;

class WorkflowEntityFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsDraftWithLifecycleDefaults() {
        WorkflowEntity draft = WorkflowEntityFactory.draft(
                new WorkflowDraftDefinition("app-1", "tenant-1", "Support", null, objectMapper.createObjectNode()));

        assertThat(draft.getId()).isEqualTo("app-1");
        assertThat(draft.getAppId()).isEqualTo("app-1");
        assertThat(draft.getMode()).isEqualTo("workflow");
        assertThat(draft.getVersion()).isEqualTo("draft");
        assertThat(draft.getPublished()).isFalse();
    }

    @Test
    void appliesOnlyDefinitionFieldsPresentInAnUpdate() {
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setName("Existing name");
        workflow.setMode("chatflow");
        workflow.setGraph(objectMapper.createObjectNode().put("revision", 1));

        WorkflowEntityFactory.updateDefinition(
                workflow, "  ", null, objectMapper.createObjectNode().put("revision", 2));

        assertThat(workflow.getName()).isEqualTo("Existing name");
        assertThat(workflow.getMode()).isEqualTo("chatflow");
        assertThat(workflow.getGraph().path("revision").asInt()).isEqualTo(2);
    }

    @Test
    void publishesACompleteSnapshotWithoutReusingDraftIdentity() {
        WorkflowEntity draft = WorkflowEntityFactory.draft(new WorkflowDraftDefinition(
                "app-1",
                "tenant-1",
                "Support",
                "chatflow",
                objectMapper.createObjectNode().put("revision", 3)));
        draft.setFeatures("features");
        draft.setOutput("output-schema");

        WorkflowEntity snapshot = WorkflowEntityFactory.publishedSnapshot(
                draft, new WorkflowPublication("1.2", "Add escalation"), "fallback-version");

        assertThat(snapshot.getId()).isNull();
        assertThat(snapshot.getAppId()).isEqualTo("app-1");
        assertThat(snapshot.getGraph()).isSameAs(draft.getGraph());
        assertThat(snapshot.getFeatures()).isEqualTo("features");
        assertThat(snapshot.getOutput()).isEqualTo("output-schema");
        assertThat(snapshot.getVersion()).isEqualTo("1.2");
        assertThat(snapshot.getMarkedName()).isEqualTo("1.2");
        assertThat(snapshot.getMarkedComment()).isEqualTo("Add escalation");
        assertThat(snapshot.getPublished()).isTrue();
    }
}
