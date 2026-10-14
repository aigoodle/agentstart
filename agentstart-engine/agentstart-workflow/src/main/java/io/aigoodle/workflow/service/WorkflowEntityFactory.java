package io.aigoodle.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.workflow.entity.WorkflowEntity;

/**
 * Creates workflow persistence entities and applies the mutations allowed during
 * their lifecycle. Keeping this mapping here leaves {@link WorkflowService}
 * focused on application orchestration and database interaction.
 */
final class WorkflowEntityFactory {

    static final String DRAFT_VERSION = "draft";
    static final String DEFAULT_MODE = "workflow";

    private WorkflowEntityFactory() {}

    static WorkflowEntity draft(WorkflowDraftDefinition definition) {
        WorkflowEntity draft = new WorkflowEntity();
        draft.setId(definition.applicationId());
        draft.setAppId(definition.applicationId());
        draft.setTenantId(definition.tenantId());
        draft.setName(definition.name());
        draft.setMode(defaultIfBlank(definition.mode(), DEFAULT_MODE));
        draft.setGraph(definition.graph());
        draft.setOutput(WorkflowContractExtractor.extract(definition.graph()));
        draft.setVersion(DRAFT_VERSION);
        draft.setPublished(Boolean.FALSE);
        return draft;
    }

    static void updateDefinition(WorkflowEntity workflow, String name, String mode, JsonNode graphDefinition) {
        if (hasText(name)) {
            workflow.setName(name);
        }
        if (hasText(mode)) {
            workflow.setMode(mode);
        }
        if (graphDefinition != null) {
            workflow.setGraph(graphDefinition);
            workflow.setOutput(WorkflowContractExtractor.extract(graphDefinition));
        }
    }

    static void updateDesignerState(WorkflowEntity draft, WorkflowDraftChanges changes) {
        if (changes.graph() != null) {
            draft.setGraph(changes.graph());
            draft.setOutput(WorkflowContractExtractor.extract(changes.graph()));
        }
        if (changes.features() != null) {
            draft.setFeatures(changes.features());
        }
        if (changes.environmentVariables() != null) {
            draft.setEnvironmentVariables(changes.environmentVariables());
        }
        if (changes.conversationVariables() != null) {
            draft.setConversationVariables(changes.conversationVariables());
        }
    }

    static WorkflowEntity publishedSnapshot(
            WorkflowEntity draft, WorkflowPublication publication, String defaultVersion) {
        WorkflowEntity snapshot = new WorkflowEntity();
        snapshot.setAppId(draft.getAppId());
        snapshot.setTenantId(draft.getTenantId());
        snapshot.setName(draft.getName());
        snapshot.setMode(draft.getMode());
        snapshot.setGraph(draft.getGraph());
        snapshot.setFeatures(draft.getFeatures());
        snapshot.setEnvironmentVariables(draft.getEnvironmentVariables());
        snapshot.setConversationVariables(draft.getConversationVariables());
        snapshot.setOutput(draft.getOutput());
        snapshot.setVersion(defaultIfBlank(publication.versionName(), defaultVersion));
        snapshot.setMarkedName(publication.versionName());
        snapshot.setMarkedComment(publication.comment());
        snapshot.setPublished(Boolean.TRUE);
        return snapshot;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
