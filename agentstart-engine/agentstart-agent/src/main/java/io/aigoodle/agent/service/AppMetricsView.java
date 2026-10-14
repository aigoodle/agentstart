package io.aigoodle.agent.service;

import lombok.Builder;
import lombok.Data;

/** Read-only summary of conversation activity for one application. */
@Data
@Builder
public class AppMetricsView {
    private String appId;
    /** Number of distinct conversations with persisted messages. */
    private int totalConversations;
    /** Total number of messages, including roles outside user and assistant. */
    private int totalMessages;

    private int userMessages;
    private int assistantMessages;
    /** Average number of user turns per conversation, rounded to two decimals. */
    private double avgInteractionsPerConversation;
    /** ISO-8601 time of the most recent message, or {@code null} when empty. */
    private String lastActivityAt;
}
