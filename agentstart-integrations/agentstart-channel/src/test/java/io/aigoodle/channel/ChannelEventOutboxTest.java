package io.aigoodle.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.aigoodle.channel.config.ChannelProperties;
import io.aigoodle.channel.persistence.ChannelEventEntity;
import io.aigoodle.channel.persistence.ChannelEventMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ChannelEventOutboxTest {

    @Test
    void conversationProjectionFailureCannotLoseADurableInboundClaim() {
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelConversationService conversations = mock(ChannelConversationService.class);
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership(
                        "connection-1", "tenant-a", "employee-1", "openclaw-default"));
        doAnswer(invocation -> {
                    ((ChannelEventEntity) invocation.getArgument(0)).setId("event-claimed");
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));
        when(conversations.touchInbound(any())).thenThrow(new DuplicateKeyException("projection race"));
        ChannelMessageObserver observer = mock(ChannelMessageObserver.class);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties(),
                conversations,
                new InMemoryChannelOutboundRateLimiter(),
                observer);

        ChannelEventLogService.InboundClaim claim = service.claimInbound(inbound(), "worker-1");

        assertThat(claim.acquired()).isTrue();
        assertThat(claim.eventId()).isEqualTo("event-claimed");
        verify(conversations).touchInbound(any());
        verify(observer).conversationProjectionFinished("openclaw", "qqbot", "openclaw-default", "failure");
    }

    @Test
    void inboundRuntimeNodeIsUsedForOwnershipAndPersistedInTheIdempotencyScope() {
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.ownership("openclaw", "node-b", "qqbot", "shared-account"))
                .thenReturn(new ChannelConnectionService.Ownership("connection-b", "tenant-b", "employee-b", "node-b"));
        doAnswer(invocation -> {
                    ((ChannelEventEntity) invocation.getArgument(0)).setId("event-b");
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "shared-account",
                "same-message",
                "sender",
                "conversation",
                "hello",
                "TEXT",
                List.of(),
                Map.of(),
                Instant.now(),
                false,
                Map.of(),
                "node-b");

        service.record(event, ChannelInboundResult.unhandled(), 1, null);

        ArgumentCaptor<ChannelEventEntity> stored = ArgumentCaptor.forClass(ChannelEventEntity.class);
        verify(mapper).insert(stored.capture());
        assertThat(stored.getValue().getTenantId()).isEqualTo("tenant-b");
        assertThat(stored.getValue().getConnectionId()).isEqualTo("connection-b");
        assertThat(stored.getValue().getRuntimeNodeId()).isEqualTo("node-b");
        verify(connections).ownership("openclaw", "node-b", "qqbot", "shared-account");
        verify(connections, never()).ownership("openclaw", "qqbot", "shared-account");
    }

    @Test
    void activeInboundClaimMakesConcurrentDeliveryWaitWithoutAgentExecution() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-inbound-claim"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership(
                        "connection-1", "tenant-a", "employee-7", "openclaw-node-1"));
        when(mapper.insert(any(ChannelEventEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        ChannelEventEntity winner = source();
        winner.setId("event-1");
        winner.setStatus("PROCESSING");
        winner.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        winner.setLeaseOwner("other-node");
        when(mapper.selectOne(any())).thenReturn(winner);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());

        ChannelEventLogService.InboundClaim claim = service.claimInbound(inbound(), "this-node");

        assertThat(claim.acquired()).isFalse();
        assertThat(claim.busy()).isTrue();
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void expiredInboundClaimCanBeAtomicallyRecovered() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-inbound-recovery"),
                ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership(
                        "connection-1", "tenant-a", "employee-7", "openclaw-node-1"));
        when(mapper.insert(any(ChannelEventEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        ChannelEventEntity abandoned = source();
        abandoned.setId("event-1");
        abandoned.setStatus("PROCESSING");
        abandoned.setAttempts(1);
        abandoned.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        abandoned.setLeaseOwner("dead-node");
        when(mapper.selectOne(any())).thenReturn(abandoned);
        when(mapper.update(isNull(), any())).thenReturn(1);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());

        ChannelEventLogService.InboundClaim claim = service.claimInbound(inbound(), "recovery-node");

        assertThat(claim.acquired()).isTrue();
        assertThat(claim.eventId()).isEqualTo("event-1");
        assertThat(claim.leaseOwner()).isEqualTo("recovery-node");
    }

    @Test
    void inboundLeaseHeartbeatUsesClaimOwnerAsFencingToken() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-inbound-renew"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        when(mapper.update(isNull(), any())).thenReturn(1);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                mock(ChannelConnectionService.class),
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());
        ChannelEventLogService.InboundClaim claim =
                new ChannelEventLogService.InboundClaim("event-1", "tenant-a", "worker-7", true, null);

        assertThat(service.renewInbound(claim)).isTrue();
        verify(mapper).update(isNull(), any());
    }

    @Test
    void storesRichInboundEnvelopeWithoutFlatteningAttachments() {
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership(
                        "connection-1", "tenant-a", "employee-7", "openclaw-node-1"));
        doAnswer(invocation -> {
                    ((ChannelEventEntity) invocation.getArgument(0)).setId("rich-1");
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-1",
                "external-user-1",
                "conversation-1",
                "查看图片",
                "IMAGE",
                List.of(new ChannelAttachment(
                        "IMAGE", "https://example.test/image.png", "image.png", "image/png", 128L, Map.of())),
                Map.of("alt", "截图"),
                Instant.now(),
                false,
                Map.of());

        ChannelEventLogService.View view = service.record(event, ChannelInboundResult.unhandled(), 3, null);

        ArgumentCaptor<ChannelEventEntity> stored = ArgumentCaptor.forClass(ChannelEventEntity.class);
        verify(mapper).insert(stored.capture());
        assertThat(stored.getValue().getMessageType()).isEqualTo("IMAGE");
        assertThat(stored.getValue().getAttachmentsJson()).contains("image.png");
        assertThat(view.attachments()).hasSize(1);
        assertThat(view.contentPayload()).containsEntry("alt", "截图");
    }

    @Test
    void persistsGroupReplyTargetAndOutboxSendsBackToGroupInsteadOfSender() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-group-target"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelRuntimeProvider runtime = mock(ChannelRuntimeProvider.class);
        when(runtime.type()).thenReturn("openclaw");
        when(runtime.nodeId()).thenReturn("openclaw-node-1");
        when(runtime.sendWithResult(any())).thenReturn(ChannelSendResult.accepted());
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership(
                        "connection-1", "tenant-a", "employee-7", "openclaw-node-1"));
        AtomicInteger sequence = new AtomicInteger();
        doAnswer(invocation -> {
                    ((ChannelEventEntity) invocation.getArgument(0)).setId("group-event-" + sequence.incrementAndGet());
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of(runtime)),
                new ChannelProperties());

        service.record(
                new ChannelInboundEvent(
                        "openclaw",
                        "qqbot",
                        "account-1",
                        "message-group-1",
                        "member-openid",
                        "group-conversation",
                        "大家好",
                        Instant.now(),
                        true,
                        Map.of("replyTargetId", "group-openid")),
                ChannelInboundResult.reply("群回复", Map.of()),
                2,
                null);

        ArgumentCaptor<ChannelEventEntity> inserted = ArgumentCaptor.forClass(ChannelEventEntity.class);
        verify(mapper, times(2)).insert(inserted.capture());
        ChannelEventEntity inbound = inserted.getAllValues().get(0);
        ChannelEventEntity outbound = inserted.getAllValues().get(1);
        assertThat(inbound.getSenderId()).isEqualTo("member-openid");
        assertThat(inbound.getReplyTargetId()).isEqualTo("group-openid");
        assertThat(outbound.getReplyTargetId()).isEqualTo("group-openid");
        when(mapper.selectList(any())).thenReturn(List.of(outbound));
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.deliverPendingBatch("worker-group");

        ArgumentCaptor<ChannelOutboundMessage> message = ArgumentCaptor.forClass(ChannelOutboundMessage.class);
        verify(runtime).sendWithResult(message.capture());
        assertThat(message.getValue().targetId()).isEqualTo("group-openid");
    }

    private static ChannelInboundEvent inbound() {
        return new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-1",
                "external-user-1",
                "conversation-1",
                "你好",
                Instant.now(),
                false,
                Map.of());
    }

    @Test
    void agentReplyIsAtomicallyQueuedForOutboxInsteadOfMarkedAsAlreadySent() {
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership(
                        "connection-1", "tenant-a", "employee-7", "openclaw-node-1"));
        AtomicInteger sequence = new AtomicInteger();
        doAnswer(invocation -> {
                    ((ChannelEventEntity) invocation.getArgument(0)).setId("event-" + sequence.incrementAndGet());
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());

        service.record(
                new ChannelInboundEvent(
                        "openclaw",
                        "qqbot",
                        "account-1",
                        "message-1",
                        "external-user-1",
                        "conversation-1",
                        "你好",
                        Instant.now(),
                        false,
                        Map.of()),
                ChannelInboundResult.reply("您好", Map.of("agentId", "agent-1")),
                8,
                null);

        ArgumentCaptor<ChannelEventEntity> stored = ArgumentCaptor.forClass(ChannelEventEntity.class);
        verify(mapper, times(2)).insert(stored.capture());
        ChannelEventEntity outbound = stored.getAllValues().get(1);
        assertThat(outbound.getStatus()).isEqualTo("PENDING");
        assertThat(outbound.getSenderType()).isEqualTo("AGENT");
        assertThat(outbound.getIdempotencyKey()).isEqualTo("agent-reply:event-1");
        assertThat(outbound.getNextAttemptAt()).isNotNull();
    }

    @Test
    void timelineUsesOpaqueKeysetCursor() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-page"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                mock(ChannelConnectionService.class),
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());
        ChannelEventEntity newer = source();
        newer.setId("event-2");
        newer.setCreatedAt(LocalDateTime.of(2026, 8, 19, 12, 0));
        ChannelEventEntity older = source();
        older.setId("event-1");
        older.setCreatedAt(LocalDateTime.of(2026, 8, 19, 11, 0));
        when(mapper.selectList(any())).thenReturn(List.of(newer, older));

        ChannelEventLogService.Page page = service.page("tenant-a", null, "conversation-1", null, 1);

        assertThat(page.items()).extracting(ChannelEventLogService.View::id).containsExactly("event-2");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        assertThatThrownBy(() -> service.page("tenant-a", null, null, "not-a-cursor", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid channel event cursor");
    }

    @Test
    void deliveryReceiptAdvancesSentMessage() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-delivery"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        when(connections.ownership("openclaw", "qqbot", "account-1"))
                .thenReturn(new ChannelConnectionService.Ownership("connection-1", "tenant-a", "employee-1", "node-1"));
        when(mapper.update(isNull(), any())).thenReturn(1);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());

        assertThat(service.markDelivered("openclaw", "qqbot", "account-1", "message-9", null))
                .isTrue();
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChannelEventEntity>> update =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSegment()).contains("tenant_id", "connection_id", "platform_message_id");
        assertThat(update.getValue().getParamNameValuePairs().values())
                .contains("tenant-a", "connection-1", "message-9");
    }

    @Test
    void deliveryReceiptForUnknownRuntimeAccountCannotUpdateAnyTenant() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-delivery-missing"),
                ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                connections,
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of()),
                new ChannelProperties());

        assertThat(service.markDelivered("openclaw", "qqbot", "unknown", "message-9", null))
                .isFalse();
        verify(connections).ownership("openclaw", "qqbot", "unknown");
        verifyNoInteractions(mapper);
    }

    @Test
    void manualReplyIsPersistedBeforeDeliveryAndCapturesProviderMessageId() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelConnectionService connections = mock(ChannelConnectionService.class);
        ChannelInboundDispatcher dispatcher = mock(ChannelInboundDispatcher.class);
        ChannelRuntimeProvider runtime = mock(ChannelRuntimeProvider.class);
        when(runtime.type()).thenReturn("native");
        when(runtime.sendWithResult(any())).thenReturn(new ChannelSendResult("qq-message-9", Map.of()));
        ChannelEventLogService service = new ChannelEventLogService(
                mapper, connections, dispatcher, new ChannelRuntimeRegistry(List.of(runtime)), new ChannelProperties());

        ChannelEventEntity source = source();
        source.setProvider("native");
        source.setMessageId("qq-inbound-message-1");
        source.setContentJson("{\"conversationType\":\"GROUP\"}");
        when(mapper.selectOne(any())).thenReturn(source, null, source);
        doAnswer(invocation -> {
                    ChannelEventEntity inserted = invocation.getArgument(0);
                    inserted.setId("outbound-1");
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));

        ChannelEventLogService.View queued =
                service.manualReply("tenant-a", "inbound-1", "  您好  ", "request-1", "EMPLOYEE", "employee-7");

        ArgumentCaptor<ChannelEventEntity> inserted = ArgumentCaptor.forClass(ChannelEventEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(queued.status()).isEqualTo("PENDING");
        assertThat(inserted.getValue().getReplyToEventId()).isEqualTo("inbound-1");
        assertThat(inserted.getValue().getSenderActorId()).isEqualTo("employee-7");
        assertThat(inserted.getValue().getIdempotencyKey()).isEqualTo("request-1");

        ChannelEventEntity outbound = inserted.getValue();
        when(mapper.selectList(any())).thenReturn(List.of(outbound));
        when(mapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.deliverPendingBatch("worker-a")).isEqualTo(1);
        ArgumentCaptor<ChannelOutboundMessage> message = ArgumentCaptor.forClass(ChannelOutboundMessage.class);
        verify(runtime).sendWithResult(message.capture());
        assertThat(message.getValue().targetId()).isEqualTo("external-user-1");
        assertThat(message.getValue().metadata())
                .containsEntry("sourceEventId", "inbound-1")
                .containsEntry("replyToPlatformMessageId", "qq-inbound-message-1")
                .containsEntry("conversationType", "GROUP")
                .containsEntry("idempotencyKey", "request-1");
        // exhausted-lease recovery sweep, claim, then SENT acknowledgement
        verify(mapper, times(3)).update(isNull(), any());
    }

    @Test
    void richManualReplySurvivesDatabaseOutboxBoundary() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-rich-outbound"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelRuntimeProvider runtime = mock(ChannelRuntimeProvider.class);
        when(runtime.type()).thenReturn("openclaw");
        when(runtime.sendWithResult(any())).thenReturn(ChannelSendResult.accepted());
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                mock(ChannelConnectionService.class),
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of(runtime)),
                new ChannelProperties());
        ChannelEventEntity source = source();
        when(mapper.selectOne(any())).thenReturn(source, (ChannelEventEntity) null);
        doAnswer(invocation -> {
                    ((ChannelEventEntity) invocation.getArgument(0)).setId("rich-outbound");
                    return 1;
                })
                .when(mapper)
                .insert(any(ChannelEventEntity.class));

        service.manualReply(
                "tenant-a",
                "inbound-1",
                "请查看",
                "IMAGE",
                List.of(new ChannelAttachment(
                        "IMAGE", "https://example.test/a.png", "a.png", "image/png", 12L, Map.of())),
                Map.of("text", "卡片"),
                "rich-key",
                "EMPLOYEE",
                "employee-7");

        ArgumentCaptor<ChannelEventEntity> stored = ArgumentCaptor.forClass(ChannelEventEntity.class);
        verify(mapper).insert(stored.capture());
        assertThat(stored.getValue().getMessageType()).isEqualTo("IMAGE");
        assertThat(stored.getValue().getAttachmentsJson()).contains("a.png");
        assertThat(stored.getValue().getContentJson()).contains("卡片");
        when(mapper.selectList(any())).thenReturn(List.of(stored.getValue()));
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.deliverPendingBatch("worker-rich");

        ArgumentCaptor<ChannelOutboundMessage> sent = ArgumentCaptor.forClass(ChannelOutboundMessage.class);
        verify(runtime).sendWithResult(sent.capture());
        assertThat(sent.getValue().messageType()).isEqualTo("IMAGE");
        assertThat(sent.getValue().attachments()).hasSize(1);
        assertThat(sent.getValue().contentPayload()).containsEntry("text", "卡片");
    }

    @Test
    void reclaimsAnExpiredSendingLeaseAfterWorkerCrash() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test-reclaim"), ChannelEventEntity.class);
        ChannelEventMapper mapper = mock(ChannelEventMapper.class);
        ChannelRuntimeProvider runtime = mock(ChannelRuntimeProvider.class);
        when(runtime.type()).thenReturn("openclaw");
        when(runtime.sendWithResult(any())).thenReturn(new ChannelSendResult("platform-1", Map.of()));
        ChannelEventLogService service = new ChannelEventLogService(
                mapper,
                mock(ChannelConnectionService.class),
                mock(ChannelInboundDispatcher.class),
                new ChannelRuntimeRegistry(List.of(runtime)),
                new ChannelProperties());
        ChannelEventEntity stranded = source();
        stranded.setId("outbound-stranded");
        stranded.setDirection("OUTBOUND");
        stranded.setStatus("SENDING");
        stranded.setAttempts(1);
        stranded.setLeaseOwner("dead-worker");
        stranded.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        stranded.setIdempotencyKey("stable-key");
        when(mapper.selectList(any())).thenReturn(List.of(stranded));
        when(mapper.update(isNull(), any())).thenReturn(0, 1, 1);

        assertThat(service.deliverPendingBatch("replacement-worker")).isEqualTo(1);

        ArgumentCaptor<ChannelOutboundMessage> sent = ArgumentCaptor.forClass(ChannelOutboundMessage.class);
        verify(runtime).sendWithResult(sent.capture());
        assertThat(sent.getValue().metadata()).containsEntry("idempotencyKey", "stable-key");
        verify(mapper, times(3)).update(isNull(), any());
    }

    private static ChannelEventEntity source() {
        ChannelEventEntity source = new ChannelEventEntity();
        source.setId("inbound-1");
        source.setTenantId("tenant-a");
        source.setConnectionId("connection-1");
        source.setOwnerId("employee-7");
        source.setProvider("openclaw");
        source.setChannelId("qqbot");
        source.setAccountId("account-1");
        source.setSenderId("external-user-1");
        source.setConversationId("conversation-1");
        source.setDirection("INBOUND");
        source.setContent("你好");
        source.setEventTime(LocalDateTime.now());
        return source;
    }
}
