package io.aigoodle.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;

/** Mutable designer state saved together with a draft workflow graph. */
public record WorkflowDraftChanges(
        JsonNode graph, String features, String environmentVariables, String conversationVariables) {

    public static WorkflowDraftChanges graphOnly(JsonNode graph) {
        return new WorkflowDraftChanges(graph, null, null, null);
    }
}
