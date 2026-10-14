package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.aigoodle.channel.persistence.ChannelConversationEntity;
import io.aigoodle.channel.persistence.ChannelConversationMapper;
import io.aigoodle.channel.persistence.ChannelEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelConversationServiceTest {
    @BeforeAll
    static void tableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "conversation-test"),
                ChannelConversationEntity.class);
    }

    @Test
    void firstInboundMessageCreatesBotConversationAndUnreadCount() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        ChannelConversationService service = new ChannelConversationService(mapper);
        ChannelEventEntity event = event();

        ChannelConversationService.View result = service.touchInbound(event);

        assertThat(result.status()).isEqualTo("BOT_ACTIVE");
        assertThat(result.agentPaused()).isFalse();
        assertThat(result.unreadCount()).isEqualTo(1);
        ArgumentCaptor<ChannelConversationEntity> inserted = ArgumentCaptor.forClass(ChannelConversationEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(inserted.getValue().getConnectionId()).isEqualTo("connection-1");
    }

    @Test
    void staleOrSecondClaimIsRejectedAtomically() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        when(mapper.update(isNull(), any())).thenReturn(0);
        ChannelConversationService service = new ChannelConversationService(mapper);

        assertThatThrownBy(() -> service.claim("tenant-a", "conversation-row-1", 3, "operator-1", "Alice", "support"))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("already claimed");
    }

    @Test
    void pausedConversationCanBeCheckedBeforeAgentExecution() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        ChannelConversationEntity row = new ChannelConversationEntity();
        row.setTenantId("tenant-a");
        row.setConnectionId("connection-1");
        row.setConversationId("external-conversation");
        row.setAgentPaused(true);
        when(mapper.selectOne(any())).thenReturn(row);

        assertThat(new ChannelConversationService(mapper)
                        .agentPaused("tenant-a", "connection-1", "external-conversation"))
                .isTrue();
    }

    @Test
    void firstAgentRouteIsPinnedAndLaterBindingChangesCannotReplaceIt() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        ChannelConversationEntity unpinned = conversation(null, null);
        ChannelConversationEntity pinned = conversation("agent-1", "version-3");
        pinned.setAgentRouteReason("employee:latest_active_version");
        pinned.setRoutingPolicyVersion(7L);
        when(mapper.selectOne(any())).thenReturn(unpinned, pinned, pinned);
        when(mapper.update(isNull(), any())).thenReturn(1);
        ChannelConversationService service = new ChannelConversationService(mapper);

        ChannelConversationService.AgentAssignment first = service.pinAgentRoute(
                "tenant-a",
                "connection-1",
                "external-conversation",
                "agent-1",
                "version-3",
                "employee:latest_active_version",
                7);
        ChannelConversationService.AgentAssignment later = service.pinAgentRoute(
                "tenant-a",
                "connection-1",
                "external-conversation",
                "agent-2",
                "version-8",
                "tenant_default:pinned_version",
                8);

        assertThat(first.agentId()).isEqualTo("agent-1");
        assertThat(first.agentVersionId()).isEqualTo("version-3");
        assertThat(later).isEqualTo(first);
        verify(mapper, times(1)).update(isNull(), any());
    }

    @Test
    void fallbackPromotionIsTenantScopedAndPersistsTheEffectiveAgent() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        ChannelConversationEntity primary = conversation("agent-primary", "version-primary");
        ChannelConversationEntity fallback = conversation("agent-fallback", "version-fallback");
        fallback.setAgentRouteReason("fallback:primary_unavailable:IllegalStateException");
        fallback.setRoutingPolicyVersion(9L);
        when(mapper.selectOne(any())).thenReturn(primary, fallback);
        when(mapper.update(isNull(), any())).thenReturn(1);
        ChannelConversationService service = new ChannelConversationService(mapper);

        ChannelConversationService.AgentAssignment assignment = service.promoteFallbackRoute(
                "tenant-a",
                "connection-1",
                "external-conversation",
                "agent-primary",
                "agent-fallback",
                "version-fallback",
                "fallback:primary_unavailable:IllegalStateException",
                9);

        assertThat(assignment.agentId()).isEqualTo("agent-fallback");
        assertThat(assignment.agentVersionId()).isEqualTo("version-fallback");
        assertThat(assignment.routeReason()).startsWith("fallback:primary_unavailable");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> update =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSegment()).contains("tenant_id", "id", "agent_id");
        assertThat(update.getValue().getParamNameValuePairs().values())
                .contains("tenant-a", "conversation-row-1", "agent-primary", "agent-fallback");
    }

    @Test
    void lateFallbackCannotOverwriteAConcurrentConversationRouteChange() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        ChannelConversationEntity primary = conversation("agent-primary", "version-primary");
        ChannelConversationEntity concurrent = conversation("agent-admin-selected", "version-admin");
        when(mapper.selectOne(any())).thenReturn(primary, concurrent);
        when(mapper.update(isNull(), any())).thenReturn(0);
        ChannelConversationService service = new ChannelConversationService(mapper);

        assertThatThrownBy(() -> service.promoteFallbackRoute(
                        "tenant-a",
                        "connection-1",
                        "external-conversation",
                        "agent-primary",
                        "agent-fallback",
                        "version-fallback",
                        "fallback:primary_exception",
                        4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("route changed");
    }

    @Test
    void conversationPageUsesStableCursorAndTenantScopedKeysetQuery() {
        ChannelConversationMapper mapper = mock(ChannelConversationMapper.class);
        ChannelConversationEntity first = paged("conversation-3", LocalDateTime.of(2026, 8, 20, 12, 3));
        ChannelConversationEntity second = paged("conversation-2", LocalDateTime.of(2026, 8, 20, 12, 2));
        ChannelConversationEntity lookahead = paged("conversation-1", LocalDateTime.of(2026, 8, 20, 12, 1));
        when(mapper.selectList(any())).thenReturn(List.of(first, second, lookahead), List.of(lookahead));
        ChannelConversationService service = new ChannelConversationService(mapper);

        ChannelConversationService.Page firstPage =
                service.page("tenant-a", null, null, false, "openclaw", "qqbot", null, 2);
        ChannelConversationService.Page secondPage =
                service.page("tenant-a", null, null, false, "openclaw", "qqbot", firstPage.nextCursor(), 2);

        assertThat(firstPage.items())
                .extracting(ChannelConversationService.View::id)
                .containsExactly("conversation-3", "conversation-2");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items())
                .extracting(ChannelConversationService.View::id)
                .containsExactly("conversation-1");
        assertThat(secondPage.hasMore()).isFalse();
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> queries =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper, times(2)).selectList(queries.capture());
        assertThat(queries.getAllValues().getFirst().getSqlSegment())
                .contains("tenant_id", "provider", "channel_id", "last_message_at", "id");
        assertThat(queries.getAllValues().getFirst().getParamNameValuePairs().values())
                .contains("tenant-a", "openclaw", "qqbot");
        assertThat(queries.getAllValues().get(1).getSqlSegment()).contains("last_message_at", "id");
        assertThat(queries.getAllValues().get(1).getParamNameValuePairs().values())
                .contains("tenant-a", "openclaw", "qqbot", "conversation-2");
    }

    @Test
    void invalidConversationCursorFailsClosed() {
        ChannelConversationService service = new ChannelConversationService(mock(ChannelConversationMapper.class));
        assertThatThrownBy(() -> service.page("tenant-a", null, null, false, null, null, "not-a-valid-cursor", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid channel conversation cursor");
    }

    private static ChannelConversationEntity conversation(String agentId, String versionId) {
        ChannelConversationEntity row = new ChannelConversationEntity();
        row.setId("conversation-row-1");
        row.setTenantId("tenant-a");
        row.setConnectionId("connection-1");
        row.setConversationId("external-conversation");
        row.setAgentId(agentId);
        row.setAgentVersionId(versionId);
        return row;
    }

    private static ChannelConversationEntity paged(String id, LocalDateTime lastMessageAt) {
        ChannelConversationEntity row = conversation("agent-1", "version-1");
        row.setId(id);
        row.setOwnerId("employee-1");
        row.setProvider("openclaw");
        row.setChannelId("qqbot");
        row.setAccountId("account-1");
        row.setStatus("BOT_ACTIVE");
        row.setAgentPaused(false);
        row.setUnreadCount(0);
        row.setLockVersion(1L);
        row.setLastMessageAt(lastMessageAt);
        return row;
    }

    private static ChannelEventEntity event() {
        ChannelEventEntity event = new ChannelEventEntity();
        event.setId("event-1");
        event.setTenantId("tenant-a");
        event.setConnectionId("connection-1");
        event.setOwnerId("employee-1");
        event.setProvider("openclaw");
        event.setChannelId("qqbot");
        event.setAccountId("account-1");
        event.setConversationId("external-conversation");
        event.setContent("hello");
        return event;
    }
}
