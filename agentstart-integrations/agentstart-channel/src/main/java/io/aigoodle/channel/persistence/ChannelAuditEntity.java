package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_channel_audit")
public class ChannelAuditEntity extends BaseEntity {
    private String actorId;
    private String actorName;
    private String principalType;
    private String action;
    private String resourceType;
    private String resourceId;
    private String outcome;
    private String detailsJson;
}
