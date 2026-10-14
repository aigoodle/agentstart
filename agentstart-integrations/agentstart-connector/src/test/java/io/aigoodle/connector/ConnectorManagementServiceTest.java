package io.aigoodle.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.aigoodle.common.crypto.TenantSecretCodec;
import io.aigoodle.connector.connection.ConnectorConnectionService;
import io.aigoodle.connector.execution.ConnectorExecutionQueryService;
import io.aigoodle.connector.persistence.ConnectorConnectionEntity;
import io.aigoodle.connector.persistence.ConnectorConnectionMapper;
import io.aigoodle.connector.persistence.ConnectorExecutionEntity;
import io.aigoodle.connector.persistence.ConnectorExecutionMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorManagementServiceTest {
    @Test
    void connectionTestValidatesEncryptedPayloadWithoutReturningSecrets() {
        ConnectorConnectionMapper mapper = mock(ConnectorConnectionMapper.class);
        TenantSecretCodec codec = mock(TenantSecretCodec.class);
        ConnectorConnectionEntity entity = new ConnectorConnectionEntity();
        entity.setId("connection-1");
        entity.setTenantId("acme");
        entity.setEncryptedCredentials("ciphertext");
        entity.setEncryptedConfig("config-ciphertext");
        when(mapper.selectOne(any())).thenReturn(entity);
        when(codec.decode(eq("acme"), any())).thenReturn(Map.of("token", "secret"));

        var result = new ConnectorConnectionService(mapper, codec).test("connection-1", "acme");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).doesNotContain("secret");
        assertThat(entity.getStatus()).isEqualTo("CONFIGURED");
        assertThat(entity.getLastTestedAt()).isNotNull();
        verify(mapper).update(eq(entity), any());
    }

    @Test
    void executionQueryIsTenantScopedAndLimited() {
        ConnectorExecutionMapper mapper = mock(ConnectorExecutionMapper.class);
        ConnectorExecutionEntity row = new ConnectorExecutionEntity();
        row.setTenantId("acme");
        when(mapper.selectList(any())).thenReturn(List.of(row));
        var service = new ConnectorExecutionQueryService(mapper);

        assertThat(service.list("acme", "openclaw", "qq", "FAILED", 10)).containsExactly(row);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<ConnectorExecutionEntity>> query =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("tenant_id", "provider", "connector_id", "status", "limit 10");
    }
}
