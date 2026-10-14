package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.aigoodle.channel.persistence.ChannelAuditEntity;
import io.aigoodle.channel.persistence.ChannelAuditMapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.PrincipalType;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelAuditServiceTest {
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "channel-audit-test"), ChannelAuditEntity.class);
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void recordsTrustedActorAndNeverNeedsSecretDetails() {
        ChannelAuditMapper mapper = mock(ChannelAuditMapper.class);
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("tenant-a")
                .userId("employee-7")
                .username("Alice")
                .principalType(PrincipalType.USER)
                .build());

        new ChannelAuditService(mapper)
                .success("CHANNEL_MESSAGE_REPLY", "CHANNEL_EVENT", "event-1", Map.of("replyToEventId", "source-1"));

        ArgumentCaptor<ChannelAuditEntity> row = ArgumentCaptor.forClass(ChannelAuditEntity.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(row.getValue().getActorId()).isEqualTo("employee-7");
        assertThat(row.getValue().getDetailsJson()).contains("source-1").doesNotContain("credential");
    }

    @Test
    void filteredAuditQueryAlwaysRetainsTenantAndResourceBoundary() {
        ChannelAuditMapper mapper = mock(ChannelAuditMapper.class);
        when(mapper.selectList(any())).thenReturn(java.util.List.of());

        new ChannelAuditService(mapper)
                .list("tenant-a", "CHANNEL_IDENTITY_SAVE", "CHANNEL_IDENTITY", "identity-1", "admin-1", "success", 50);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> query =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("tenant_id", "action", "resource_type", "resource_id", "actor_id", "outcome");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .contains("tenant-a", "CHANNEL_IDENTITY_SAVE", "CHANNEL_IDENTITY", "identity-1", "admin-1", "SUCCESS");
    }
}
