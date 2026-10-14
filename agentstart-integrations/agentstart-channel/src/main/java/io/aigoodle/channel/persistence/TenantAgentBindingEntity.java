package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_tenant_agent_binding")
public class TenantAgentBindingEntity extends BaseEntity {
    private String defaultAgentId;
    private String defaultAgentVersionId;
    private String fallbackAgentId;
    private String fallbackAgentVersionId;
    private Long routingPolicyVersion;
    private Boolean enabled;
}
