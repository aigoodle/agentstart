package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.variable.VariablePool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionGroupTest {

    private final VariablePool variables = variables();

    @Test
    void allMatchIsTheReadableDefault() {
        ConditionGroup group = ConditionGroup.of(
                List.of(condition("sys.age", "greater_than", "18"), condition("sys.country", "is", "SG")), null);

        assertThat(group.logicalOperator()).isEqualTo(ConditionGroup.LogicalOperator.ALL);
        assertThat(group.matches(variables)).isTrue();
    }

    @Test
    void anyMatchShortCircuitsAlternativeConditions() {
        ConditionGroup group = ConditionGroup.of(
                List.of(condition("sys.country", "is", "US"), condition("sys.country", "is", "SG")), "or");

        assertThat(group.logicalOperator()).isEqualTo(ConditionGroup.LogicalOperator.ANY);
        assertThat(group.matches(variables)).isTrue();
    }

    @Test
    void emptyConditionGroupIsSatisfiedForBothOperators() {
        assertThat(ConditionGroup.of(List.of(), "and").matches(variables)).isTrue();
        assertThat(ConditionGroup.of(List.of(), "or").matches(variables)).isTrue();
    }

    @Test
    void readsDesignerCaseShapeAndSelectorPath() {
        Map<String, Object> caseDefinition = Map.of(
                "logicalOperator",
                "and",
                "conditions",
                List.of(Map.of(
                        "variableSelector", List.of("sys", "profile", "tier"),
                        "operator", "is",
                        "value", "gold")));

        variables.setSystem("profile", Map.of("tier", "gold"));
        ConditionGroup group = ConditionGroup.fromCase(caseDefinition);

        assertThat(group).isNotNull();
        assertThat(group.matches(variables)).isTrue();
    }

    private static Map<String, Object> condition(String variable, String operator, Object value) {
        return Map.of("variable", variable, "operator", operator, "value", value);
    }

    private static VariablePool variables() {
        VariablePool variables = new VariablePool();
        variables.setSystem("age", 21);
        variables.setSystem("country", "SG");
        return variables;
    }
}
