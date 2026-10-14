package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_run_events")
public class AgentRunEventEntity extends BaseEntity {
    private String runId;
    private Long sequenceNo;
    private String eventType;
    private String payloadJson;
}
