package io.aigoodle.workflow.node.builtin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Implements the designer's unary and binary condition operator vocabulary. */
final class ConditionMatcher {

    private ConditionMatcher() {}

    static boolean matches(String operator, Object actualValue, String expectedValue, Object rawExpectedValue) {
        String normalizedOperator =
                operator == null ? "equals" : operator.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedOperator) {
            case "empty", "is_empty", "isempty" -> isEmpty(actualValue);
            case "not_empty", "is_not_empty", "isnotempty" -> !isEmpty(actualValue);
            case "null", "is_null", "isnull" -> actualValue == null;
            case "not_null", "is_not_null", "isnotnull", "exists" -> actualValue != null;
            case "true", "is_true", "istrue" -> Boolean.TRUE.equals(asBoolean(actualValue));
            case "false", "is_false", "isfalse" -> Boolean.FALSE.equals(asBoolean(actualValue));
            default -> matchesBinary(normalizedOperator, asString(actualValue), expectedValue, rawExpectedValue);
        };
    }

    private static boolean matchesBinary(
            String operator, String actualValue, String expectedValue, Object rawExpectedValue) {
        return switch (operator) {
            case "equals", "is", "=", "==", "eq" -> actualValue.equals(expectedValue);
            case "not_equals", "is_not", "isnot", "!=", "<>", "neq", "not_eq" -> !actualValue.equals(expectedValue);
            case "contains" -> actualValue.contains(expectedValue);
            case "not_contains", "not_contain", "does_not_contain" -> !actualValue.contains(expectedValue);
            case "starts_with", "startswith", "starts" -> actualValue.startsWith(expectedValue);
            case "ends_with", "endswith", "ends" -> actualValue.endsWith(expectedValue);
            case "gt", ">", "greater", "greater_than" -> compare(actualValue, expectedValue) > 0;
            case "lt", "<", "less", "less_than" -> compare(actualValue, expectedValue) < 0;
            case "ge", ">=", "gte", "greater_or_equal", "greater_than_or_equal" -> compare(actualValue, expectedValue)
                    >= 0;
            case "le", "<=", "lte", "less_or_equal", "less_than_or_equal" -> compare(actualValue, expectedValue) <= 0;
            case "in" -> isIn(actualValue, rawExpectedValue, expectedValue);
            case "not_in", "notin" -> !isIn(actualValue, rawExpectedValue, expectedValue);
            case "regex", "matches", "match" -> matchesRegex(actualValue, expectedValue);
            case "not_regex", "not_matches", "not_match" -> !matchesRegex(actualValue, expectedValue);
            default -> false;
        };
    }

    private static int compare(String left, String right) {
        try {
            return Double.compare(Double.parseDouble(left), Double.parseDouble(right));
        } catch (NumberFormatException notNumeric) {
            return left.compareTo(right);
        }
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        return false;
    }

    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return switch (String.valueOf(value).trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> Boolean.TRUE;
            case "false", "0", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static boolean isIn(String actualValue, Object rawExpectedValue, String renderedExpectedValue) {
        if (rawExpectedValue instanceof List<?> expectedValues) {
            return expectedValues.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .anyMatch(actualValue::equals);
        }
        if (renderedExpectedValue == null || renderedExpectedValue.isEmpty()) {
            return false;
        }
        for (String expectedValue : renderedExpectedValue.split(",")) {
            if (expectedValue.trim().equals(actualValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRegex(String actualValue, String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        try {
            return Pattern.compile(expression).matcher(actualValue).find();
        } catch (PatternSyntaxException invalidExpression) {
            return false;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
