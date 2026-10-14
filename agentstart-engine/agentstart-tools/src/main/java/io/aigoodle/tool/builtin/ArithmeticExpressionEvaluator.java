package io.aigoodle.tool.builtin;

/** Evaluates the arithmetic grammar supported by {@link CalculatorTool}. */
final class ArithmeticExpressionEvaluator {

    private static final int END_OF_EXPRESSION = -1;

    private final String expression;
    private int position = -1;
    private int currentCharacter;

    private ArithmeticExpressionEvaluator(String expression) {
        this.expression = expression;
    }

    static double evaluate(String expression) {
        return new ArithmeticExpressionEvaluator(expression).parse();
    }

    private double parse() {
        advance();
        double result = parseExpression();
        skipWhitespace();
        if (currentCharacter != END_OF_EXPRESSION) {
            throw new IllegalArgumentException("unexpected character '" + (char) currentCharacter + "'");
        }
        return result;
    }

    private double parseExpression() {
        double result = parseTerm();
        while (true) {
            if (consume('+')) {
                result += parseTerm();
            } else if (consume('-')) {
                result -= parseTerm();
            } else {
                return result;
            }
        }
    }

    private double parseTerm() {
        double result = parseFactor();
        while (true) {
            if (consume('*')) {
                result *= parseFactor();
            } else if (consume('/')) {
                result /= parseFactor();
            } else {
                return result;
            }
        }
    }

    private double parseFactor() {
        if (consume('+')) {
            return parseFactor();
        }
        if (consume('-')) {
            return -parseFactor();
        }
        if (consume('(')) {
            double nestedResult = parseExpression();
            if (!consume(')')) {
                throw new IllegalArgumentException("missing ')'");
            }
            return nestedResult;
        }
        return parseNumber();
    }

    private double parseNumber() {
        skipWhitespace();
        int numberStart = position;
        while (Character.isDigit(currentCharacter) || currentCharacter == '.') {
            advance();
        }
        if (numberStart == position) {
            String found =
                    currentCharacter == END_OF_EXPRESSION ? "end of expression" : "'" + (char) currentCharacter + "'";
            throw new IllegalArgumentException("expected a number but found " + found);
        }
        try {
            return Double.parseDouble(expression.substring(numberStart, position));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "invalid number '" + expression.substring(numberStart, position) + "'", exception);
        }
    }

    private boolean consume(char expectedCharacter) {
        skipWhitespace();
        if (currentCharacter != expectedCharacter) {
            return false;
        }
        advance();
        return true;
    }

    private void skipWhitespace() {
        while (currentCharacter != END_OF_EXPRESSION && Character.isWhitespace(currentCharacter)) {
            advance();
        }
    }

    private void advance() {
        position++;
        currentCharacter = position < expression.length() ? expression.charAt(position) : END_OF_EXPRESSION;
    }
}
