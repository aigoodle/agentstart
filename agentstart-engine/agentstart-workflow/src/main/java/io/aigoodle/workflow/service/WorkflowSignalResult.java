package io.aigoodle.workflow.service;

import io.aigoodle.workflow.engine.WorkflowRunResult;

public record WorkflowSignalResult(boolean accepted, boolean duplicate, WorkflowRunResult runResult) {}
