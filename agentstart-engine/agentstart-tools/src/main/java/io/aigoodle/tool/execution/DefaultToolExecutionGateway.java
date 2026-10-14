package io.aigoodle.tool.execution;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.tool.ToolDefinition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Virtual-thread execution boundary with bounded admission and deterministic policy ordering. */
public class DefaultToolExecutionGateway implements ToolExecutionGateway, AutoCloseable {
    private final ToolExecutionProperties properties;
    private final List<ToolExecutionPolicy> policies;
    private final List<ToolExecutionListener> listeners;
    private final Semaphore permits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DefaultToolExecutionGateway(
            ToolExecutionProperties properties,
            List<ToolExecutionPolicy> policies,
            List<ToolExecutionListener> listeners) {
        this.properties = Objects.requireNonNull(properties);
        this.policies = policies == null ? List.of() : List.copyOf(policies);
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.permits = new Semaphore(Math.max(1, properties.getMaxConcurrent()), true);
    }

    @Override
    public Object execute(ToolDefinition tool, Map<String, Object> arguments, ToolExecutionContext suppliedContext) {
        Objects.requireNonNull(tool, "tool");
        Map<String, Object> safeArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        ToolExecutionContext context = suppliedContext == null ? ToolExecutionContext.anonymous() : suppliedContext;
        long started = System.nanoTime();
        for (ToolExecutionPolicy policy : policies) {
            ToolExecutionPolicy.Decision decision = policy.evaluate(tool, safeArguments, context);
            if (decision != null && !decision.allowed()) {
                publish(record(tool, context, ToolExecutionRecord.Status.DENIED, 0, started, decision.reason()));
                throw new ToolExecutionException(
                        "tool_denied", decision.reason() == null ? "Tool execution denied" : decision.reason());
            }
        }

        boolean acquired;
        try {
            acquired = permits.tryAcquire(
                    Math.max(1, properties.getAcquireTimeout().toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            publish(record(
                    tool,
                    context,
                    ToolExecutionRecord.Status.CANCELLED,
                    0,
                    started,
                    "Interrupted while waiting for execution capacity"));
            throw new ToolExecutionException("tool_cancelled", "Tool execution cancelled", interrupted);
        }
        if (!acquired) {
            publish(record(
                    tool,
                    context,
                    ToolExecutionRecord.Status.SATURATED,
                    0,
                    started,
                    "Tool execution capacity exhausted"));
            throw new ToolExecutionException("tool_saturated", "Tool execution capacity exhausted");
        }
        try {
            return invokeWithRetry(tool, safeArguments, context, started);
        } finally {
            permits.release();
        }
    }

    private Object invokeWithRetry(
            ToolDefinition tool, Map<String, Object> arguments, ToolExecutionContext context, long started) {
        int maximumAttempts = tool.idempotent() ? Math.max(1, properties.getMaxRetries() + 1) : 1;
        Throwable lastFailure = null;
        var caller = UserContextHolder.get();
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            Future<Object> future = executor.submit(() ->
                    UserContextHolder.callAs(caller, () -> ToolExecutionGateway.invoke(tool, arguments, context)));
            try {
                Duration timeout = tool.timeout() == null ? properties.getTimeout() : tool.timeout();
                Object result = future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
                Object bounded = boundOutput(result);
                publish(record(tool, context, ToolExecutionRecord.Status.SUCCEEDED, attempt, started, null));
                return bounded;
            } catch (TimeoutException timeout) {
                future.cancel(true);
                publish(record(tool, context, ToolExecutionRecord.Status.TIMED_OUT, attempt, started, "Timed out"));
                throw new ToolExecutionException("tool_timeout", "Tool '" + tool.name() + "' timed out", timeout);
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                publish(record(tool, context, ToolExecutionRecord.Status.CANCELLED, attempt, started, "Interrupted"));
                throw new ToolExecutionException(
                        "tool_cancelled", "Tool '" + tool.name() + "' was cancelled", interrupted);
            } catch (ExecutionException failure) {
                lastFailure = failure.getCause() == null ? failure : failure.getCause();
            }
        }
        String message = lastFailure == null ? "unknown failure" : lastFailure.getMessage();
        publish(record(tool, context, ToolExecutionRecord.Status.FAILED, maximumAttempts, started, message));
        throw new ToolExecutionException("tool_failed", "Tool '" + tool.name() + "' failed: " + message, lastFailure);
    }

    private Object boundOutput(Object result) {
        if (!(result instanceof CharSequence text)) return result;
        int limit = Math.max(1, properties.getMaxOutputChars());
        if (text.length() <= limit) return result;
        return text.subSequence(0, limit) + "\n...[tool output truncated]";
    }

    private static ToolExecutionRecord record(
            ToolDefinition tool,
            ToolExecutionContext context,
            ToolExecutionRecord.Status status,
            int attempts,
            long started,
            String error) {
        Map<String, Object> auditMetadata = new java.util.LinkedHashMap<>(context.metadata());
        auditMetadata.remove("authorization");
        ToolExecutionContext auditContext = new ToolExecutionContext(
                context.executionId(), context.tenantId(), context.ownerId(), context.conversationId(), auditMetadata);
        return new ToolExecutionRecord(
                context.executionId(),
                tool.name(),
                status,
                attempts,
                Duration.ofNanos(System.nanoTime() - started),
                error,
                auditContext);
    }

    private void publish(ToolExecutionRecord record) {
        for (ToolExecutionListener listener : listeners) {
            try {
                listener.onExecution(record);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}
