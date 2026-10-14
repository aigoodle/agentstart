package io.aigoodle.channel;

import java.time.LocalDateTime;

/** Host-facing SLA notification payload. It deliberately carries no framework-specific user model. */
public record ChannelSlaReminder(
        String tenantId,
        String conversationRecordId,
        String connectionId,
        String conversationId,
        String provider,
        String accountId,
        String status,
        String stage,
        LocalDateTime dueAt,
        String assigneeId,
        String assigneeName,
        String assignmentGroup,
        int unreadCount,
        String lastMessagePreview) {}
