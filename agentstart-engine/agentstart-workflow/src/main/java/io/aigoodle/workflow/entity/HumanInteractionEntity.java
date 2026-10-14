package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Durable, channel-neutral human interaction associated with one waiting node. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_human_interactions")
public class HumanInteractionEntity extends BaseEntity {
    private String workflowId;
    private String appId;
    private String runId;
    private String nodeId;
    private String conversationId;
    private String status;
    private String title;
    private String description;
    private String formMode;
    private String presentationMode;
    private String inputSchemaJson;
    private String accessTokenHash;
    private String deliveryConfigJson;
    private String shortCode;
    private String channelProvider;
    private String channelId;
    private String channelConnectionId;
    private String channelTarget;
    private String channelConversationId;
    private String notificationMessageId;
    private String submittedValuesJson;
    private String submittedText;
    private String submittedBy;
    private LocalDateTime expiresAt;
    private LocalDateTime submittedAt;
}
