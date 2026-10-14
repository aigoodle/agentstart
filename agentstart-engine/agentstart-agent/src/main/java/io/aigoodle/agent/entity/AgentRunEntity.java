package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_runs")
public class AgentRunEntity extends BaseEntity {
    private String agentId;
    private String conversationId;
    private String status;
    private String definitionJson;
    private String requestJson;
    private String responseJson;
    private String error;
    private Long version;
    private Long eventSequence;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
