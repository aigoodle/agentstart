package io.aigoodle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_workflow_resume_signals")
public class WorkflowResumeSignalEntity extends BaseEntity {
    private String runId;
    private String eventId;
    private String correlationKey;
    private String payloadHash;
    private String resumedBy;
}
