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

/** Recovers PENDING/ERROR runtime accounts with a database lease, safe for embedded multi-node hosts. */
public final class ChannelConnectionReconcileWorker implements SmartLifecycle {
    private final ChannelConnectionService connections;
    private final ChannelProperties properties;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public ChannelConnectionReconcileWorker(ChannelConnectionService connections, ChannelProperties properties) {
        this.connections = connections;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isConnectionReconcileEnabled() || !running.compareAndSet(false, true)) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-channel-connection-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        long interval =
                Math.max(500, properties.getConnectionReconcileInterval().toMillis());
        executor.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void pollSafely() {
        try {
            LocalDateTime now = LocalDateTime.now();
            for (ChannelConnectionService.ReconcileCandidate candidate :
                    connections.reconciliationCandidates(now, properties.getConnectionReconcileBatchSize())) {
                connections.reconcile(candidate, workerId, now, properties.getConnectionReconcileLeaseDuration());
            }
        } catch (RuntimeException ignored) {
            // Database/runtime outages are retried on the next poll.
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
        return Integer.MAX_VALUE - 110;
    }
}
