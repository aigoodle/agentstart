package io.aigoodle.connector.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_connector_execution")
public class ConnectorExecutionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String tenantId;
    private String provider;
    private String connectorId;
    private String actionId;
    private String installationId;
    private String connectionId;
    private String agentId;
    private String workflowId;
    private String runId;
    private String nodeId;
    private String status;
    private Long durationMs;
    private Integer attempts;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
}
