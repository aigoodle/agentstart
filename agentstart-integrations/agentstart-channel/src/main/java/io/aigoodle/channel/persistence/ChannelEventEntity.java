package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_channel_event")
public class ChannelEventEntity extends BaseEntity {
    private String connectionId;
    private String runtimeNodeId;
    private String ownerId;
    private String provider;
    private String channelId;
    private String accountId;
    private String messageId;
    private String senderId;
    /** Platform-native address used for replies; differs from senderId for group/channel messages. */
    private String replyTargetId;

    private String conversationId;
    private String replyToEventId;
    private String idempotencyKey;
    private String platformMessageId;
    private String senderType;
    private String senderActorId;
    private String direction;
    private String content;
    private String messageType;
    private String attachmentsJson;
    private String contentJson;
    private Boolean handled;
    private String status;
    private String replyContent;
    private Long durationMs;
    private String errorMessage;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    private String leaseOwner;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime eventTime;
}
