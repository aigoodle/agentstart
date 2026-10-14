package io.aigoodle.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.mapper.WorkflowMapper;
import io.aigoodle.workflow.mapper.WorkflowRunMapper;
import org.junit.jupiter.api.Test;

class WorkflowServiceTest {

    @Test
    void checksLinkedApplicationBeforeReturningWorkflowOrChangingDraft() {
        var permissions = mock(io.aigoodle.agent.service.AppPermissionService.class);
        workflowService.setAppPermissions(permissions);
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setTenantId("tenant-1");
        workflow.setAppId("app-1");
        workflow.setId("workflow-1");
        when(workflowMapper.selectOne(any())).thenReturn(workflow);
        org.mockito.Mockito.doThrow(new io.aigoodle.common.exception.PlatformException("forbidden", "denied", null))
                .when(permissions)
                .requireLinkedApp("tenant-1", "app-1", false);
        assertThatThrownBy(() -> workflowService.require("tenant-1", "workflow-1"))
                .hasMessage("denied");

        org.mockito.Mockito.doThrow(new io.aigoodle.common.exception.PlatformException("forbidden", "read only", null))
                .when(permissions)
                .requireLinkedApp("tenant-1", "app-1", true);
        assertThatThrownBy(() -> workflowService.saveDraft(
                        "tenant-1", "app-1", new WorkflowDraftChanges(null, null, null, null)))
                .hasMessage("read only");
        verify(workflowMapper, never()).update(any(), any());
    }

    @Test
    void filtersRestrictedLinkedApplicationsFromWorkflowList() {
        var permissions = mock(io.aigoodle.agent.service.AppPermissionService.class);
        workflowService.setAppPermissions(permissions);
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setAppId("restricted-app");
        WorkflowEntity standalone = new WorkflowEntity();
        when(workflowMapper.selectList(any())).thenReturn(java.util.List.of(workflow, standalone));
        when(permissions.unreadableAppIds("tenant-1", java.util.List.of("restricted-app")))
                .thenReturn(java.util.Set.of("restricted-app"));
        assertThat(workflowService.list("tenant-1")).containsExactly(standalone);
    }

    private final WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
    private final WorkflowService workflowService =
            new WorkflowService(workflowMapper, mock(WorkflowRunMapper.class), mock(WorkflowEngine.class));

    @Test
    void createsDraftFromNamedDefinitionWithoutPositionalAmbiguity() {
        WorkflowDraftDefinition definition =
                new WorkflowDraftDefinition("app-1", "tenant-1", "Customer support flow", "chatflow", null);

        WorkflowEntity draft = workflowService.createDraft(definition);

        assertThat(draft.getId()).isEqualTo("app-1");
        assertThat(draft.getAppId()).isEqualTo("app-1");
        assertThat(draft.getTenantId()).isEqualTo("tenant-1");
        assertThat(draft.getName()).isEqualTo("Customer support flow");
        assertThat(draft.getMode()).isEqualTo("chatflow");
        assertThat(draft.getGraph()).isNotNull();
        verify(workflowMapper).insert(draft);
    }

    @Test
    void updatesOnlyDesignerStateExplicitlyIncludedInTheChangeSet() {
        WorkflowEntity draft = new WorkflowEntity();
        draft.setId("app-1");
        draft.setTenantId("default");
        draft.setVersion("draft");
        draft.setFeatures("old-features");
        draft.setEnvironmentVariables("old-environment");
        draft.setConversationVariables("old-conversation");
        when(workflowMapper.selectOne(any())).thenReturn(draft);

        WorkflowDraftChanges changes = new WorkflowDraftChanges(
                new ObjectMapper().createObjectNode(), "new-features", null, "new-conversation");

        WorkflowEntity saved = workflowService.saveDraft("app-1", changes);

        assertThat(saved.getFeatures()).isEqualTo("new-features");
        assertThat(saved.getEnvironmentVariables()).isEqualTo("old-environment");
        assertThat(saved.getConversationVariables()).isEqualTo("new-conversation");
        verify(workflowMapper).update(org.mockito.ArgumentMatchers.eq(draft), any());
    }

    @Test
    void tenantScopedMutationCannotTouchForeignWorkflow() {
        when(workflowMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> workflowService.update("tenant-a", "workflow-b", "stolen", "workflow", null))
                .hasMessageContaining("Workflow not found");
        assertThatThrownBy(() -> workflowService.delete("tenant-a", "workflow-b"))
                .hasMessageContaining("Workflow not found");

        verify(workflowMapper, never()).update(any(), any());
        verify(workflowMapper, never()).delete(any());
    }
}
