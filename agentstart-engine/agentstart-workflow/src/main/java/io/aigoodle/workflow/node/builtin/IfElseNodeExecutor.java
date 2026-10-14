package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import java.util.Map;

/**
 * Branches the workflow with either the designer's <b>cases[]</b> shape
 * (arbitrary number of elif-style branches + implicit else) or the older
 * <b>flat conditions</b> shape (single true/false split).
 *
 * <h4>cases[] shape (Dify designer)</h4>
 * <pre>
 * {
 *   "cases": [
 *     { "id": "case_abc", "caseId": "true", "logicalOperator": "or",
 *       "conditions": [
 *         {"variableSelector": ["2","struct","intent_type"], "operator": "IS", "value": "greet"},
 *         {"variableSelector": ["2","struct","intent_type"], "operator": "CONTAINS", "value": "chat"}
 *       ] },
 *     { "id": "case_def", "caseId": "true", "logicalOperator": "and",
 *       "conditions": [ {"variableSelector": ["2","struct","intent_type"], "operator": "IS", "value": "exit"} ] }
 *   ]
 * }
 * </pre>
 * Cases are evaluated in order; <b>the first case that matches wins</b> — its
 * {@code id} becomes the node's {@code handle}, which the engine matches
 * against outgoing edges via {@code sourceHandle}. Only that branch fires;
 * the rest of the cases (and their downstream chains) are skipped.
 * <p>
 * If no case matches, the executor emits {@code "false"} as an implicit else
 * handle so a graph author can wire a fallback edge with
 * {@code sourceHandle="false"} from the node — the same convention the old
 * flat shape uses for its negative branch, so both shapes cohabit safely.
 *
 * <h4>Flat shape (backward-compat)</h4>
 * <pre>
 * { "conditions": [...], "logicalOperator": "and" }
 * </pre>
 * Evaluated as one predicate, emits {@code "true"} or {@code "false"} handle.
 *
 * <h4>Outputs</h4>
 * <ul>
 *   <li>{@code handle} — the outgoing edge selector (case id / true / false)</li>
 *   <li>{@code case} — the matched case's {@code caseId} label, or
 *       {@code "false"} when nothing matched</li>
 *   <li>{@code caseIndex} — 0-based index of the matched case, or {@code -1}
 *       for the else branch (useful when authors want to debug or route
 *       downstream by index)</li>
 *   <li>{@code result} — boolean match flag for the flat shape</li>
 * </ul>
 */
public class IfElseNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.IF_ELSE;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        List<Map<String, Object>> configuredCases = node.getMapList("cases");
        if (!configuredCases.isEmpty()) {
            return evaluateCases(configuredCases, context);
        }
        // Legacy flat shape — kept alive so old saved graphs / tests still work.
        boolean matched = ConditionEvaluator.evaluate(
                node.getMapList("conditions"), node.getString("logicalOperator", "and"), context.getPool());
        return NodeResult.empty()
                .output("result", matched)
                .output("case", matched ? "true" : "false")
                .handle(matched ? "true" : "false");
    }

    private static NodeResult evaluateCases(List<Map<String, Object>> configuredCases, ExecutionContext context) {
        for (int caseIndex = 0; caseIndex < configuredCases.size(); caseIndex++) {
            Map<String, Object> candidateCase = configuredCases.get(caseIndex);
            if (ConditionEvaluator.evaluateCase(candidateCase, context.getPool())) {
                return matchedCase(candidateCase, caseIndex);
            }
        }
        // No case matched — implicit else. Emit "false" so a designer can
        // wire an edge with sourceHandle="false" as the catch-all branch.
        return NodeResult.empty()
                .output("case", "false")
                .output("caseIndex", -1)
                .output("result", false)
                .handle("false");
    }

    private static NodeResult matchedCase(Map<String, Object> candidateCase, int caseIndex) {
        String branchHandle = firstNonBlank(candidateCase, "id", "caseId");
        String caseLabel = firstNonBlank(candidateCase, "caseId", "id");
        return NodeResult.empty()
                .output("case", caseLabel != null ? caseLabel : "true")
                .output("caseIndex", caseIndex)
                .output("result", true)
                .handle(branchHandle != null ? branchHandle : "true");
    }

    private static String firstNonBlank(Map<String, Object> values, String... candidateKeys) {
        for (String candidateKey : candidateKeys) {
            Object candidateValue = values.get(candidateKey);
            if (candidateValue == null) {
                continue;
            }
            String text = String.valueOf(candidateValue).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }
}
