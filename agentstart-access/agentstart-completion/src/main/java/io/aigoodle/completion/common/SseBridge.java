package io.aigoodle.completion.common;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Reusable helper that turns a synchronous "producer + listener callbacks" style run
 * (like {@code WorkflowEngine.run(..., stepListener)}) into a real-time
 * {@link Flux} of {@link ServerSentEvent}s.
 * <p>
 * Under WebFlux, an SSE endpoint just returns a {@code Flux<ServerSentEvent<?>>} and
 * Netty pushes each element as an {@code event:}/{@code data:} block as it arrives.
 * The producer we're bridging (agent runs, workflow steps, LLM stream tokens) blocks
 * on the calling thread, so we hand it off to a virtual-thread scheduler and pump
 * events into a {@link FluxSink}.
 */
public final class SseBridge {

    private static final Logger logger = LoggerFactory.getLogger(SseBridge.class);

    private static final Scheduler PRODUCER_SCHEDULER =
            Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor(), "sse-producer");

    private SseBridge() {}

    @FunctionalInterface
    public interface Emit {
        void event(String eventName, Object eventData);
    }

    @FunctionalInterface
    public interface Producer {
        void run(Emit emit) throws Exception;
    }

    /**
     * Start {@code producer} on a virtual thread and stream its events as
     * {@link ServerSentEvent}s. Errors surface as an {@code event: error} carrying
     * {@code {"message": "..."}} followed by an error terminal signal.
     */
    public static Flux<ServerSentEvent<Object>> stream(Producer producer) {
        var caller = io.aigoodle.common.context.UserContextHolder.get();
        return Flux.<ServerSentEvent<Object>>create(sink -> {
                    AtomicLong eventSequence = new AtomicLong();
                    Emit emitter = (eventName, eventData) -> {
                        if (sink.isCancelled()) {
                            return;
                        }
                        sink.next(ServerSentEvent.builder(eventData)
                                .id(Long.toString(eventSequence.incrementAndGet()))
                                .event(eventName)
                                .build());
                    };
                    try (var ignored = io.aigoodle.common.context.UserContextHolder.openScope(caller)) {
                        producer.run(emitter);
                        sink.complete();
                    } catch (Exception producerFailure) {
                        logger.warn("SSE producer failed: {}", producerFailure.getMessage());
                        if (!sink.isCancelled()) {
                            emitter.event("error", Map.of("message", failureMessage(producerFailure)));
                        }
                        sink.error(producerFailure);
                    }
                })
                .subscribeOn(PRODUCER_SCHEDULER);
    }

    private static String failureMessage(Exception producerFailure) {
        String message = producerFailure.getMessage();
        return message == null || message.isBlank() ? "unknown" : message;
    }
}
