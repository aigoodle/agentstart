package io.aigoodle.channel;

import io.aigoodle.channel.config.ChannelProperties;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/** Periodically refreshes persisted runtime health without browser-side N+1 probes. */
public final class ChannelConnectionHealthWorker implements SmartLifecycle {
    private final ChannelConnectionService connections;
    private final ChannelProperties properties;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + ":health:" + UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public ChannelConnectionHealthWorker(ChannelConnectionService connections, ChannelProperties properties) {
        this.connections = connections;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isConnectionHealthEnabled() || !running.compareAndSet(false, true)) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-channel-connection-health");
            thread.setDaemon(true);
            return thread;
        });
        long interval =
                Math.max(1000, properties.getConnectionHealthPollInterval().toMillis());
        executor.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void pollSafely() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime staleBefore = now.minus(properties.getConnectionHealthRefreshInterval());
            for (ChannelConnectionService.HealthCandidate candidate :
                    connections.healthCandidates(staleBefore, properties.getConnectionHealthBatchSize())) {
                connections.refreshHealth(candidate, workerId, now, properties.getConnectionHealthLeaseDuration());
            }
        } catch (RuntimeException ignored) {
            // A later poll retries database and runtime failures.
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
        return Integer.MAX_VALUE - 109;
    }
}
