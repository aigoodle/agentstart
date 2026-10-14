package io.aigoodle.web.common;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * MVC counterpart of the WebFlux SSE bridge. It turns a synchronous
 * "producer + listener callbacks" run into a real-time stream of
 * {@link SseEmitter} events.
 * <p>
 * Each call spawns a virtual thread that runs the producer and pushes events
 * through an {@link SseEmitter}. Tomcat's async request handling keeps the
 * connection open until the emitter completes or errors out.
 */
public final class SseEmitterBridge {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterBridge.class);

    /** No server-side timeout; clients close the stream when they are done. */
    private static final long NO_TIMEOUT = 0L;

    private static final ExecutorService PRODUCER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private SseEmitterBridge() {}

    @FunctionalInterface
    public interface Emit {
        void event(String name, Object data);
    }

    @FunctionalInterface
    public interface Producer {
        void run(Emit emit) throws Exception;
    }

    /**
     * Start {@code producer} on a virtual thread and stream its events through
     * a new {@link SseEmitter}. Errors surface as an {@code event: error}
     * carrying {@code {"message": "..."}} followed by {@link SseEmitter#completeWithError}.
     */
    public static SseEmitter stream(Producer producer) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        Emit emit = (name, data) -> {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException exception) {
                // Propagate disconnects to the producer without adding noisy logs here.
                throw new RuntimeException("SSE emit failed: " + exception.getMessage(), exception);
            }
        };
        PRODUCER_EXECUTOR.execute(() -> {
            try {
                producer.run(emit);
                emitter.complete();
            } catch (Exception exception) {
                log.warn("SSE producer failed: {}", exception.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "message", exception.getMessage() == null ? "unknown" : exception.getMessage())));
                } catch (IOException ignored) {
                    // Client already gone; nothing to do.
                }
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }
}
