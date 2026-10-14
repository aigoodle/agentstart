package io.aigoodle.workflow.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Cooperative cancellation shared by the scheduler and every node executor. */
public final class RunCancellationToken {

    private final AtomicReference<Cancellation> cancellation = new AtomicReference<>();
    private final Set<Thread> executingThreads = ConcurrentHashMap.newKeySet();
    private final Sinks.One<String> reactiveCancellation = Sinks.one();

    public boolean cancel(String reason) {
        return stop(reason, StopKind.CANCELLED);
    }

    public boolean timeout(String reason) {
        return stop(reason, StopKind.TIMED_OUT);
    }

    public boolean pause(String reason) {
        return stop(reason, StopKind.PAUSED);
    }

    private boolean stop(String reason, StopKind kind) {
        Cancellation value = new Cancellation(reason == null ? "Workflow stopped" : reason, kind);
        if (!cancellation.compareAndSet(null, value)) return false;
        reactiveCancellation.tryEmitValue(value.reason());
        executingThreads.forEach(Thread::interrupt);
        return true;
    }

    public boolean isCancelled() {
        return cancellation.get() != null;
    }

    public boolean isTimedOut() {
        Cancellation value = cancellation.get();
        return value != null && value.kind() == StopKind.TIMED_OUT;
    }

    public boolean isPaused() {
        Cancellation value = cancellation.get();
        return value != null && value.kind() == StopKind.PAUSED;
    }

    public String reason() {
        Cancellation value = cancellation.get();
        return value == null ? null : value.reason();
    }

    public Registration registerCurrentThread() {
        Thread thread = Thread.currentThread();
        executingThreads.add(thread);
        if (isCancelled()) thread.interrupt();
        return () -> executingThreads.remove(thread);
    }

    public void throwIfCancelled() {
        if (isCancelled()) throw new WorkflowCancelledException(reason(), isTimedOut());
    }

    /** Completes when cancellation is requested, for Reactor-based LLM/tool streams. */
    public Mono<String> cancellationSignal() {
        return reactiveCancellation.asMono();
    }

    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private record Cancellation(String reason, StopKind kind) {}

    private enum StopKind {
        CANCELLED,
        TIMED_OUT,
        PAUSED
    }
}
