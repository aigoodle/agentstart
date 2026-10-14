package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_channel_identity")
public class ChannelIdentityEntity extends BaseEntity {
    private String provider;
    private String channelId;
    private String accountId;
    private String externalUserId;
    private String enterpriseUserId;
    private String verificationStatus;
    private Boolean enabled;
}
