package io.aigoodle.workflow.variable;

/** Deterministic policy used when multiple branches publish the same variable. */
public enum VariableMergeStrategy {
    OVERWRITE,
    APPEND,
    MERGE,
    REJECT_ON_CONFLICT,
    REDUCER
}
