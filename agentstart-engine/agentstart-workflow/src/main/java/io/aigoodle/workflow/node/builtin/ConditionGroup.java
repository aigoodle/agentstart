package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.variable.VariablePool;
import java.util.List;
import java.util.Map;

/** A group of designer conditions combined with all-match or any-match semantics. */
record ConditionGroup(List<Map<String, Object>> conditions, LogicalOperator logicalOperator) {

    ConditionGroup {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        logicalOperator = logicalOperator == null ? LogicalOperator.ALL : logicalOperator;
    }

    static ConditionGroup of(List<Map<String, Object>> conditions, String configuredOperator) {
        return new ConditionGroup(conditions, LogicalOperator.from(configuredOperator));
    }

    @SuppressWarnings("unchecked")
    static ConditionGroup fromCase(Map<String, Object> caseDefinition) {
        if (caseDefinition == null) {
            return null;
        }
        Object configuredConditions = caseDefinition.get("conditions");
        List<Map<String, Object>> conditions = configuredConditions instanceof List<?> conditionList
                ? (List<Map<String, Object>>) conditionList
                : List.of();
        return of(conditions, text(caseDefinition.get("logicalOperator")));
    }

    boolean matches(VariablePool variablePool) {
        if (conditions.isEmpty()) {
            return true;
        }
        return switch (logicalOperator) {
            case ALL -> conditions.stream()
                    .allMatch(condition -> ConditionEvaluator.evaluateOne(condition, variablePool));
            case ANY -> conditions.stream()
                    .anyMatch(condition -> ConditionEvaluator.evaluateOne(condition, variablePool));
        };
    }

    enum LogicalOperator {
        ALL,
        ANY;

        static LogicalOperator from(String configuredOperator) {
            return "or".equalsIgnoreCase(configuredOperator) ? ANY : ALL;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
