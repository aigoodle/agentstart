package io.aigoodle.workflow.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariablePoolMergeTest {

    @Test
    void supportsEveryExplicitMergePolicy() {
        VariablePool pool = new VariablePool();
        pool.merge("shared", "items", "a", VariableMergeStrategy.APPEND, null);
        pool.merge("shared", "items", "b", VariableMergeStrategy.APPEND, null);
        assertEquals(List.of("a", "b"), pool.get("shared.items"));

        pool.merge("shared", "map", Map.of("a", 1), VariableMergeStrategy.MERGE, null);
        pool.merge("shared", "map", Map.of("b", 2), VariableMergeStrategy.MERGE, null);
        assertEquals(Map.of("a", 1, "b", 2), pool.get("shared.map"));

        pool.merge(
                "shared", "sum", 2, VariableMergeStrategy.REDUCER, (left, right) -> (Integer) left + (Integer) right);
        pool.merge(
                "shared", "sum", 3, VariableMergeStrategy.REDUCER, (left, right) -> (Integer) left + (Integer) right);
        assertEquals(5, pool.get("shared.sum"));
    }

    @Test
    void rejectsAmbiguousConflicts() {
        VariablePool pool = new VariablePool();
        pool.merge("shared", "value", 1, VariableMergeStrategy.REJECT_ON_CONFLICT, null);
        assertThrows(
                IllegalStateException.class,
                () -> pool.merge("shared", "value", 2, VariableMergeStrategy.REJECT_ON_CONFLICT, null));
    }

    @Test
    void parallelContributionsAreFoldedByStableWriterIdNotArrivalOrder() {
        VariablePool reverse = new VariablePool();
        reverse.mergeFrom("shared", "items", "node-b", "b", VariableMergeStrategy.APPEND, null);
        reverse.mergeFrom("shared", "items", "node-a", "a", VariableMergeStrategy.APPEND, null);
        reverse.mergeFrom("shared", "winner", "node-b", "b", VariableMergeStrategy.OVERWRITE, null);
        reverse.mergeFrom("shared", "winner", "node-a", "a", VariableMergeStrategy.OVERWRITE, null);

        assertEquals(List.of("a", "b"), reverse.get("shared.items"));
        assertEquals("b", reverse.get("shared.winner"));
    }
}
