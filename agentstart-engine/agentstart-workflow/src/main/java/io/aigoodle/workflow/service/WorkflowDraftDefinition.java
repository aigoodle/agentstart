package io.aigoodle.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;

/** Complete definition used when creating or replacing an application's draft workflow. */
public record WorkflowDraftDefinition(
        String applicationId, String tenantId, String name, String mode, JsonNode graph) {}
