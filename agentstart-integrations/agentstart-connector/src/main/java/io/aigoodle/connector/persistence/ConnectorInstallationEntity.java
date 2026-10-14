package io.aigoodle.connector.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_connector_installation")
public class ConnectorInstallationEntity extends BaseEntity {
    private String provider;
    private String connectorId;
    private String version;
    private String source;
    private Boolean enabled;
    private String trustLevel;
    private String manifestJson;
    private String configSchemaJson;
}
