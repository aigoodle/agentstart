package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListOperationConfigurationTest {

    @Test
    void filtersMapItemsByConfiguredField() {
        ListOperationConfiguration operation = configuration("filter")
                .with("field", "status")
                .with("value", "active")
                .build();
        Map<String, Object> active = Map.of("name", "Alice", "status", "active");
        Map<String, Object> disabled = Map.of("name", "Bob", "status", "disabled");

        assertThat(operation.apply(List.of(active, disabled))).containsExactly(active);
    }

    @Test
    void sortsNumericMapFieldsDescending() {
        ListOperationConfiguration operation = configuration("sort")
                .with("field", "score")
                .with("order", "desc")
                .build();
        Map<String, Object> low = Map.of("score", 2);
        Map<String, Object> high = Map.of("score", 10);

        assertThat(operation.apply(List.of(low, high))).containsExactly(high, low);
        assertThat(operation.sortDirection()).isEqualTo(ListOperationConfiguration.SortDirection.DESCENDING);
    }

    @Test
    void limitsWithoutReturningSubListView() {
        ListOperationConfiguration operation =
                configuration("limit").with("size", 2).build();

        List<Object> result = operation.apply(List.of("a", "b", "c"));

        assertThat(result).containsExactly("a", "b");
        assertThat(result).isInstanceOf(java.util.ArrayList.class);
    }

    @Test
    void distinctPreservesFirstEncounterOrder() {
        ListOperationConfiguration operation = configuration("distinct").build();

        assertThat(operation.apply(List.of("b", "a", "b", "c", "a"))).containsExactly("b", "a", "c");
    }

    @Test
    void unknownOperationPassesThroughDefensiveCopy() {
        List<String> input = List.of("a", "b");
        ListOperationConfiguration operation = configuration("future-operation").build();

        List<Object> result = operation.apply(input);

        assertThat(operation.operation()).isEqualTo(ListOperationConfiguration.Operation.PASSTHROUGH);
        assertThat(result).containsExactlyElementsOf(input).isNotSameAs(input);
    }

    private static NodeBuilder configuration(String operation) {
        return new NodeBuilder().with("operation", operation);
    }

    private static final class NodeBuilder {
        private final NodeDef node = NodeDef.of("list", NodeType.LIST_OPERATOR);

        private NodeBuilder with(String name, Object value) {
            node.with(name, value);
            return this;
        }

        private ListOperationConfiguration build() {
            return ListOperationConfiguration.from(node);
        }
    }
}
