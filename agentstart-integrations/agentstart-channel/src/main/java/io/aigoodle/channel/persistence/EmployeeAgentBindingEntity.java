package io.aigoodle.channel.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_employee_agent_binding")
public class EmployeeAgentBindingEntity extends BaseEntity {
    private String employeeId;
    private String agentId;
    private String agentVersionId;
    private Long routingPolicyVersion;
    private Boolean enabled;
}
