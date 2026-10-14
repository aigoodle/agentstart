package io.aigoodle.channel;

/** Deployment-wide aggregate health counts. Contains no tenant, account or user dimensions. */
@FunctionalInterface
public interface ChannelOperationalSnapshotProvider {
    record Snapshot(
            long outboxPending,
            long outboxSending,
            long outboxRetrying,
            long outboxDeadLetters,
            long connectionsOnline,
            long connectionsPending,
            long connectionsError,
            long connectionsDisabled) {}

    Snapshot snapshot();
}
