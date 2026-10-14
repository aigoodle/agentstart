package io.aigoodle.web.service;

import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Answers "which workflows currently reference this prompt template id?".
 * <p>
 * Naive scan: pull all workflows, string-search each graph JSON for the target
 * {@code systemPromptTemplateId}. Good enough for the volumes a single-app tool sees;
 * if you outgrow it, add a materialized index table on save.
 * <p>
 * Only registered when the workflow module is on the classpath — the whole feature
 * is a no-op otherwise.
 */
@Service
@ConditionalOnBean(WorkflowService.class)
public class PromptReferenceScanner {

    private final WorkflowService workflowService;

    public PromptReferenceScanner(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public List<Map<String, Object>> findUsingTemplate(String tenantId, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return List.of();
        }
        // Match either JSON-quoted form to avoid coincidental matches inside content strings.
        String needle = "\"systemPromptTemplateId\":\"" + templateId + "\"";
        List<Map<String, Object>> hits = new ArrayList<>();
        for (WorkflowEntity wf : workflowService.list(tenantId)) {
            // Round-trip the JsonNode through toString() for a cheap contains-check.
            String graph = wf.getGraph() == null ? null : wf.getGraph().toString();
            if (graph != null && graph.contains(needle)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "workflow");
                row.put("id", wf.getId());
                row.put("name", wf.getName());
                row.put("updatedAt", wf.getUpdatedAt());
                hits.add(row);
            }
        }
        return hits;
    }
}
