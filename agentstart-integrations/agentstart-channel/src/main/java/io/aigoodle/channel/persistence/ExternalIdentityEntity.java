package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_external_identity")
public class ExternalIdentityEntity extends BaseEntity {
    private String platform;
    private String platformTenantId;
    private String externalUserId;
    private String enterpriseUserId;
    private String verificationStatus;
    private String bindingMethod;
    private Boolean enabled;
    private LocalDateTime boundAt;
    private LocalDateTime lastLoginAt;
}
