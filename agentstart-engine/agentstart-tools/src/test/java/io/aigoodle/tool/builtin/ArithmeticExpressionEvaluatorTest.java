package io.aigoodle.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArithmeticExpressionEvaluatorTest {

    @Test
    void respectsOperatorPrecedenceAndParentheses() {
        assertThat(ArithmeticExpressionEvaluator.evaluate("2 + 3 * 4")).isEqualTo(14);
        assertThat(ArithmeticExpressionEvaluator.evaluate("(2 + 3) * 4")).isEqualTo(20);
    }

    @Test
    void acceptsUnaryOperatorsDecimalsAndWhitespace() {
        assertThat(ArithmeticExpressionEvaluator.evaluate(" -(.5 + 1.5)\t* +3 "))
                .isEqualTo(-6);
    }

    @Test
    void reportsTheInvalidPartOfAnExpression() {
        assertThatThrownBy(() -> ArithmeticExpressionEvaluator.evaluate("2 + value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expected a number but found 'v'");
        assertThatThrownBy(() -> ArithmeticExpressionEvaluator.evaluate("2 * (3 + 4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missing ')'");
        assertThatThrownBy(() -> ArithmeticExpressionEvaluator.evaluate("1.2.3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid number '1.2.3'");
    }
}
