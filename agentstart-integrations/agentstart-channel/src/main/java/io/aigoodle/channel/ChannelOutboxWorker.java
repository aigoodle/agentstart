package io.aigoodle.channel;

import io.aigoodle.channel.config.ChannelProperties;
import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/** Lightweight embedded outbox poller. The database lease provides cluster-safe ownership. */
public final class ChannelOutboxWorker implements SmartLifecycle {
    private final ChannelEventLogService events;
    private final ChannelProperties properties;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public ChannelOutboxWorker(ChannelEventLogService events, ChannelProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isOutboxEnabled() || !running.compareAndSet(false, true)) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-channel-outbox");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(100, properties.getOutboxPollInterval().toMillis());
        executor.scheduleWithFixedDelay(this::pollSafely, 0, interval, TimeUnit.MILLISECONDS);
    }

    private void pollSafely() {
        try {
            events.deliverPendingBatch(workerId);
        } catch (RuntimeException ignored) {
            // A database/runtime outage is retried by the next poll; individual rows retain failure details.
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
