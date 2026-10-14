package io.aigoodle.channel;

import io.aigoodle.channel.config.ChannelProperties;
import io.aigoodle.channel.persistence.ChannelConversationEntity;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/** Cluster-safe embedded SLA scanner. Database leases prevent duplicate notifications across nodes. */
public final class ChannelSlaReminderWorker implements SmartLifecycle {
    private final ChannelConversationService conversations;
    private final ChannelSlaNotifier notifier;
    private final ChannelProperties properties;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + ":sla:" + UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public ChannelSlaReminderWorker(
            ChannelConversationService conversations, ChannelSlaNotifier notifier, ChannelProperties properties) {
        this.conversations = conversations;
        this.notifier = notifier;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isSlaReminderEnabled() || !running.compareAndSet(false, true)) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-channel-sla-reminder");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(1_000, properties.getSlaReminderPollInterval().toMillis());
        executor.scheduleWithFixedDelay(this::pollSafely, 0, interval, TimeUnit.MILLISECONDS);
    }

    void pollSafely() {
        try {
            poll(LocalDateTime.now());
        } catch (RuntimeException ignored) {
            /* next scan retries */
        }
    }

    void poll(LocalDateTime now) {
        LocalDateTime threshold = now.plus(properties.getSlaReminderLeadTime());
        for (ChannelConversationService.SlaCandidate candidate :
                conversations.findSlaReminderCandidates(threshold, properties.getSlaReminderBatchSize())) {
            int targetStage = reminderStage(candidate, now);
            if (targetStage <= candidate.stage()) continue;
            ChannelConversationEntity row = conversations.claimSlaReminder(
                    candidate, targetStage, workerId, now, properties.getSlaReminderLeaseDuration());
            if (row == null) continue;
            try {
                notifier.notify(toReminder(row, targetStage));
                conversations.completeSlaReminder(row.getTenantId(), row.getId(), workerId, targetStage, now);
            } catch (RuntimeException failure) {
                conversations.releaseSlaReminder(row.getTenantId(), row.getId(), workerId);
            }
        }
    }

    private static int reminderStage(ChannelConversationService.SlaCandidate candidate, LocalDateTime now) {
        return candidate.dueAt() != null && !candidate.dueAt().isAfter(now) ? 2 : 1;
    }

    private static ChannelSlaReminder toReminder(ChannelConversationEntity row, int stage) {
        return new ChannelSlaReminder(
                row.getTenantId(),
                row.getId(),
                row.getConnectionId(),
                row.getConversationId(),
                row.getProvider(),
                row.getAccountId(),
                row.getStatus(),
                stage == 2 ? "BREACHED" : "DUE_SOON",
                row.getSlaDueAt(),
                row.getAssigneeId(),
                row.getAssigneeName(),
                row.getAssignmentGroup(),
                row.getUnreadCount() == null ? 0 : row.getUnreadCount(),
                row.getLastMessagePreview());
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
        return Integer.MAX_VALUE - 90;
    }
}
