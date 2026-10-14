package io.aigoodle.connector.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_connector_connection")
public class ConnectorConnectionEntity extends BaseEntity {
    private String installationId;
    private String name;
    private String encryptedCredentials;
    private String encryptedConfig;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastTestedAt;
}
