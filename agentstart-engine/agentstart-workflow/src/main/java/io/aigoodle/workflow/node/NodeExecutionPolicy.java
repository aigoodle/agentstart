package io.aigoodle.workflow.node;

public record NodeExecutionPolicy(
        NodeExecutionMode executionMode,
        String idempotencyKey,
        NodeRetryPolicy retryPolicy,
        NodeResultCachePolicy resultCachePolicy,
        boolean resumable,
        NodeCompensationHandler compensationHandler) {}
