package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.variable.VariablePool;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.List;
import java.util.Map;

/** Evaluates the condition vocabulary shared by branching workflow nodes. */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    /** Evaluates conditions using {@code and} by default or {@code or} when configured. */
    public static boolean evaluate(
            List<Map<String, Object>> conditions, String logicalOperator, VariablePool variablePool) {
        return ConditionGroup.of(conditions, logicalOperator).matches(variablePool);
    }

    /** Evaluates one designer case block containing conditions and a logical operator. */
    public static boolean evaluateCase(Map<String, Object> caseDefinition, VariablePool variablePool) {
        ConditionGroup conditionGroup = ConditionGroup.fromCase(caseDefinition);
        return conditionGroup != null && conditionGroup.matches(variablePool);
    }

    /** Evaluates one condition against the current workflow variable pool. */
    public static boolean evaluateOne(Map<String, Object> condition, VariablePool variablePool) {
        if (condition == null) {
            return false;
        }
        String variablePath = resolveVariablePath(condition);
        Object actualValue = variablePath == null ? null : variablePool.get(variablePath);
        Object configuredExpectedValue = condition.get("value");
        String renderedExpectedValue = renderExpectedValue(configuredExpectedValue, variablePool);
        return ConditionMatcher.matches(
                text(condition.get("operator")), actualValue, renderedExpectedValue, configuredExpectedValue);
    }

    /**
     * Converts the designer selector array into the dotted path understood by
     * {@link VariablePool}, with fallback to the legacy {@code variable} field.
     */
    private static String resolveVariablePath(Map<String, Object> condition) {
        Object configuredSelector = condition.get("variableSelector");
        if (configuredSelector instanceof List<?> pathSegments && !pathSegments.isEmpty()) {
            return joinPath(pathSegments);
        }
        return trimmedText(condition.get("variable"));
    }

    private static String joinPath(List<?> pathSegments) {
        StringBuilder path = new StringBuilder();
        for (Object segment : pathSegments) {
            String segmentText = trimmedText(segment);
            if (segmentText == null) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(segmentText);
        }
        return path.isEmpty() ? null : path.toString();
    }

    private static String renderExpectedValue(Object configuredValue, VariablePool variablePool) {
        if (configuredValue instanceof String text) {
            return VariableResolver.render(text, variablePool);
        }
        return configuredValue == null ? "" : String.valueOf(configuredValue);
    }

    private static String trimmedText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
