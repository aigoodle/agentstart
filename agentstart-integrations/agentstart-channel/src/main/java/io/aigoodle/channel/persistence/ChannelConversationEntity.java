package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_channel_conversation")
public class ChannelConversationEntity extends BaseEntity {
    private String connectionId;
    private String ownerId;
    private String provider;
    private String channelId;
    private String accountId;
    private String conversationId;
    /** Agent routing decision pinned when the conversation first reaches the runtime. */
    private String agentId;

    private String agentVersionId;
    private String agentRouteReason;
    private Long routingPolicyVersion;
    /** BOT_ACTIVE / WAITING_HUMAN / HUMAN_ACTIVE / CLOSED. */
    private String status;

    private Boolean agentPaused;
    private String assigneeId;
    private String assigneeName;
    private String assignmentGroup;
    private LocalDateTime handoffRequestedAt;
    private LocalDateTime claimedAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastMessageAt;
    private String lastEventId;
    private String lastMessagePreview;
    private Integer unreadCount;
    private LocalDateTime slaDueAt;
    /** 0 = none, 1 = due soon, 2 = breached. */
    private Integer slaReminderStage;

    private LocalDateTime slaRemindedAt;
    private LocalDateTime slaReminderLeaseUntil;
    private String slaReminderLeaseOwner;
    private Long lockVersion;
}
