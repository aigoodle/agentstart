package io.aigoodle.tool.builtin;

import io.aigoodle.tool.AbstractToolDefinition;
import java.util.Map;

/**
 * Evaluates an arithmetic expression ({@code + - * /}, parentheses, decimals, unary
 * minus) with a small recursive-descent parser — no scripting engine required.
 */
public class CalculatorTool extends AbstractToolDefinition {

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Evaluate a basic arithmetic expression. Argument: 'expression' (e.g. '2*(3+4)/7').";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\"}},\"required\":[\"expression\"]}";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String expression = stringArgument(arguments, "expression");
        if (expression == null || expression.isBlank()) {
            return "error: missing 'expression'";
        }
        try {
            return formatResult(ArithmeticExpressionEvaluator.evaluate(expression));
        } catch (IllegalArgumentException exception) {
            return "error: " + exception.getMessage();
        }
    }

    private static String formatResult(double result) {
        if (result == Math.rint(result) && !Double.isInfinite(result)) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }
}
