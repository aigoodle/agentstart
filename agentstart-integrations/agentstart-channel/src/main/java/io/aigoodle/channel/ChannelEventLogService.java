package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.channel.config.ChannelProperties;
import io.aigoodle.channel.persistence.ChannelEventEntity;
import io.aigoodle.channel.persistence.ChannelEventMapper;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.persistence.TenantSqlScope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** Durable, secret-free observation log for inbound channel traffic and Agent replies. */
public class ChannelEventLogService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChannelEventLogService.class);
    private static final ScheduledExecutorService INBOUND_LEASE_RENEWER =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                    .daemon(true)
                    .name("channel-inbound-lease-renewer")
                    .factory());

    @FunctionalInterface
    public interface InboundLease extends AutoCloseable {
        @Override
        void close();
    }

    public record View(
            String id,
            String tenantId,
            String connectionId,
            String runtimeNodeId,
            String ownerId,
            String provider,
            String channelId,
            String accountId,
            String messageId,
            String senderId,
            String replyTargetId,
            String conversationId,
            String replyToEventId,
            String direction,
            String content,
            String messageType,
            List<ChannelAttachment> attachments,
            java.util.Map<String, Object> contentPayload,
            boolean handled,
            String status,
            String replyContent,
            Long durationMs,
            String errorMessage,
            String idempotencyKey,
            String platformMessageId,
            String senderType,
            String senderActorId,
            Integer attempts,
            LocalDateTime nextAttemptAt,
            LocalDateTime sentAt,
            LocalDateTime deliveredAt,
            LocalDateTime eventTime,
            LocalDateTime createdAt) {}

    public record Page(List<View> items, String nextCursor, boolean hasMore) {}

    public record DeadLetterReplayResult(int requested, int eligible, int requeued) {}

    /** Durable claim taken before Agent execution, preventing concurrent duplicate side effects. */
    public record InboundClaim(
            String eventId, String tenantId, String leaseOwner, boolean acquired, ChannelInboundResult priorResult) {
        public boolean busy() {
            return !acquired && priorResult == null;
        }
    }

    private final ChannelEventMapper mapper;
    private final ChannelConnectionService connections;
    private final ChannelInboundDispatcher dispatcher;
    private final ChannelRuntimeRegistry runtimes;
    private final ChannelProperties properties;
    private final ChannelConversationService conversations;
    private final ChannelOutboundRateLimiter rateLimiter;
    private final ChannelMessageObserver observer;

    public ChannelEventLogService(
            ChannelEventMapper mapper,
            ChannelConnectionService connections,
            ChannelInboundDispatcher dispatcher,
            ChannelRuntimeRegistry runtimes) {
        this(
                mapper,
                connections,
                dispatcher,
                runtimes,
                new ChannelProperties(),
                null,
                new InMemoryChannelOutboundRateLimiter(),
                ChannelMessageObserver.NOOP);
    }

    public ChannelEventLogService(
            ChannelEventMapper mapper,
            ChannelConnectionService connections,
            ChannelInboundDispatcher dispatcher,
            ChannelRuntimeRegistry runtimes,
            ChannelProperties properties) {
        this(
                mapper,
                connections,
                dispatcher,
                runtimes,
                properties,
                null,
                new InMemoryChannelOutboundRateLimiter(),
                ChannelMessageObserver.NOOP);
    }

    public ChannelEventLogService(
            ChannelEventMapper mapper,
            ChannelConnectionService connections,
            ChannelInboundDispatcher dispatcher,
            ChannelRuntimeRegistry runtimes,
            ChannelProperties properties,
            ChannelConversationService conversations) {
        this(
                mapper,
                connections,
                dispatcher,
                runtimes,
                properties,
                conversations,
                new InMemoryChannelOutboundRateLimiter(),
                ChannelMessageObserver.NOOP);
    }

    public ChannelEventLogService(
            ChannelEventMapper mapper,
            ChannelConnectionService connections,
            ChannelInboundDispatcher dispatcher,
            ChannelRuntimeRegistry runtimes,
            ChannelProperties properties,
            ChannelConversationService conversations,
            ChannelOutboundRateLimiter rateLimiter) {
        this(
                mapper,
                connections,
                dispatcher,
                runtimes,
                properties,
                conversations,
                rateLimiter,
                ChannelMessageObserver.NOOP);
    }

    public ChannelEventLogService(
            ChannelEventMapper mapper,
            ChannelConnectionService connections,
            ChannelInboundDispatcher dispatcher,
            ChannelRuntimeRegistry runtimes,
            ChannelProperties properties,
            ChannelConversationService conversations,
            ChannelOutboundRateLimiter rateLimiter,
            ChannelMessageObserver observer) {
        this.mapper = mapper;
        this.connections = connections;
        this.dispatcher = dispatcher;
        this.runtimes = runtimes;
        this.properties = properties;
        this.conversations = conversations;
        this.rateLimiter = java.util.Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.observer = java.util.Objects.requireNonNull(observer, "observer");
    }

    @Transactional
    public View record(ChannelInboundEvent event, ChannelInboundResult result, long durationMs, Throwable error) {
        ChannelConnectionService.Ownership owner = ownership(event);
        ChannelEventEntity entity = inboundEntity(event, owner);
        entity.setHandled(result != null && result.handled());
        entity.setStatus(
                error != null || result != null && "agent_error".equals(result.code())
                        ? "ERROR"
                        : result != null && result.handled() ? "HANDLED" : "RECEIVED");
        entity.setReplyContent(result == null ? null : result.reply());
        entity.setDurationMs(durationMs);
        entity.setErrorMessage(error == null ? null : error.getMessage());
        if (event.timestamp() != null)
            entity.setEventTime(LocalDateTime.ofInstant(event.timestamp(), ZoneId.systemDefault()));
        mapper.insert(entity);
        touchInbound(entity);
        if (result != null && result.reply() != null && !result.reply().isBlank()) {
            ChannelEventEntity outbound = replyEvent(entity, result.reply(), "PENDING");
            outbound.setSenderType("AGENT");
            Object agentId =
                    result.metadata() == null ? null : result.metadata().get("agentId");
            outbound.setSenderActorId(agentId == null ? null : String.valueOf(agentId));
            outbound.setDurationMs(durationMs);
            outbound.setIdempotencyKey("agent-reply:" + entity.getId());
            outbound.setAttempts(0);
            outbound.setNextAttemptAt(LocalDateTime.now());
            mapper.insert(outbound);
            touchOutbound(outbound);
        }
        return view(entity);
    }

    /**
     * Claims a platform message before invoking the Agent. A live claim makes concurrent callbacks retry;
     * an expired claim can be recovered by another application node.
     * Deliberately not transactional: PostgreSQL aborts a transaction after a unique-key violation, while the
     * duplicate path must still read the winning row. Each insert/update remains an atomic database statement.
     */
    public InboundClaim claimInbound(ChannelInboundEvent event, String workerId) {
        if (event.messageId() == null || event.messageId().isBlank()) {
            return new InboundClaim(null, null, workerId, true, null);
        }
        ChannelConnectionService.Ownership owner = ownership(event);
        String leaseOwner =
                workerId == null || workerId.isBlank() ? UUID.randomUUID().toString() : workerId;
        LocalDateTime now = LocalDateTime.now();
        ChannelEventEntity entity = inboundEntity(event, owner);
        entity.setHandled(false);
        entity.setStatus("PROCESSING");
        entity.setAttempts(1);
        entity.setLeaseOwner(leaseOwner);
        entity.setLeaseUntil(now.plus(properties.getInboundLeaseDuration()));
        try {
            TenantSqlScope.bypass(() -> mapper.insert(entity));
            // Conversation is a rebuildable projection. Materialize it while each statement is in
            // autocommit mode: a concurrent insert that loses its unique-key race must not abort the
            // transaction that later completes the durable inbound claim.
            try {
                touchInbound(entity);
                observer.conversationProjectionFinished(
                        entity.getProvider(), entity.getChannelId(), entity.getRuntimeNodeId(), "success");
            } catch (RuntimeException projectionFailure) {
                observer.conversationProjectionFinished(
                        entity.getProvider(), entity.getChannelId(), entity.getRuntimeNodeId(), "failure");
                log.warn(
                        "Could not update channel conversation projection for inbound event {}: {}",
                        entity.getId(),
                        abbreviate(projectionFailure.getMessage(), 500));
            }
            return new InboundClaim(entity.getId(), entity.getTenantId(), leaseOwner, true, null);
        } catch (DuplicateKeyException duplicate) {
            ChannelEventEntity existing = findInbound(event, owner);
            if (existing == null) throw duplicate;
            if ("PROCESSING".equals(existing.getStatus())
                    && existing.getLeaseUntil() != null
                    && existing.getLeaseUntil().isBefore(now)) {
                int updated = TenantSqlScope.bypass(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<ChannelEventEntity>()
                                .eq(ChannelEventEntity::getId, existing.getId())
                                .eq(ChannelEventEntity::getStatus, "PROCESSING")
                                .lt(ChannelEventEntity::getLeaseUntil, now)
                                .set(ChannelEventEntity::getLeaseOwner, leaseOwner)
                                .set(ChannelEventEntity::getLeaseUntil, now.plus(properties.getInboundLeaseDuration()))
                                .set(
                                        ChannelEventEntity::getAttempts,
                                        (existing.getAttempts() == null ? 0 : existing.getAttempts()) + 1)));
                if (updated == 1) {
                    return new InboundClaim(existing.getId(), existing.getTenantId(), leaseOwner, true, null);
                }
            }
            if ("PROCESSING".equals(existing.getStatus())) {
                return new InboundClaim(existing.getId(), existing.getTenantId(), null, false, null);
            }
            return new InboundClaim(existing.getId(), existing.getTenantId(), null, false, duplicateResult(existing));
        }
    }

    /** Keeps a claimed message fenced while a slow Agent invocation is still running. */
    public InboundLease keepAlive(InboundClaim claim) {
        if (claim == null || !claim.acquired() || claim.eventId() == null || claim.leaseOwner() == null) {
            return () -> {};
        }
        Duration duration = properties.getInboundLeaseDuration();
        if (duration == null || duration.isZero() || duration.isNegative()) duration = Duration.ofMinutes(2);
        long periodMs = Math.max(1_000L, Math.max(1L, duration.toMillis()) / 3L);
        Duration renewalDuration = duration;
        ScheduledFuture<?> future = INBOUND_LEASE_RENEWER.scheduleWithFixedDelay(
                () -> {
                    try {
                        renewInbound(claim, renewalDuration);
                    } catch (RuntimeException ignored) {
                        // A transient database outage must not permanently stop later heartbeat attempts.
                    }
                },
                periodMs,
                periodMs,
                TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    boolean renewInbound(InboundClaim claim) {
        Duration duration = properties.getInboundLeaseDuration();
        if (duration == null || duration.isZero() || duration.isNegative()) duration = Duration.ofMinutes(2);
        return renewInbound(claim, duration);
    }

    private boolean renewInbound(InboundClaim claim, Duration duration) {
        LocalDateTime now = LocalDateTime.now();
        return TenantSqlScope.bypass(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<ChannelEventEntity>()
                                .eq(ChannelEventEntity::getId, claim.eventId())
                                .eq(ChannelEventEntity::getStatus, "PROCESSING")
                                .eq(ChannelEventEntity::getLeaseOwner, claim.leaseOwner())
                                .set(ChannelEventEntity::getLeaseUntil, now.plus(duration))))
                == 1;
    }

    /** Completes the claimed inbound event and atomically queues any Agent reply in the Outbox. */
    @Transactional
    public View completeInbound(
            InboundClaim claim,
            ChannelInboundEvent event,
            ChannelInboundResult result,
            long durationMs,
            Throwable error) {
        if (claim == null || !claim.acquired()) throw new IllegalArgumentException("inbound claim is not acquired");
        if (claim.eventId() == null) return record(event, result, durationMs, error);
        ChannelEventEntity entity = TenantSqlScope.bypass(() -> mapper.selectById(claim.eventId()));
        if (entity == null) throw new IllegalStateException("inbound claim no longer exists");
        entity.setHandled(result != null && result.handled());
        entity.setStatus(
                error != null || result != null && "agent_error".equals(result.code())
                        ? "ERROR"
                        : result != null && result.handled() ? "HANDLED" : "RECEIVED");
        entity.setReplyContent(result == null ? null : result.reply());
        entity.setDurationMs(durationMs);
        entity.setErrorMessage(error == null ? null : error.getMessage());
        entity.setLeaseOwner(null);
        entity.setLeaseUntil(null);
        int updated = TenantSqlScope.bypass(() -> mapper.update(
                entity,
                new LambdaUpdateWrapper<ChannelEventEntity>()
                        .eq(ChannelEventEntity::getId, claim.eventId())
                        .eq(ChannelEventEntity::getStatus, "PROCESSING")
                        .eq(ChannelEventEntity::getLeaseOwner, claim.leaseOwner())
                        .set(ChannelEventEntity::getLeaseOwner, null)
                        .set(ChannelEventEntity::getLeaseUntil, null)));
        if (updated != 1) throw new IllegalStateException("inbound claim was lost before completion");
        // The conversation projection was updated when the durable claim was created. Updating it
        // here would double-count unread messages and lets a projection race roll back completion.
        if (result != null && result.reply() != null && !result.reply().isBlank()) {
            ChannelEventEntity outbound = replyEvent(entity, result.reply(), "PENDING");
            outbound.setSenderType("AGENT");
            Object agentId =
                    result.metadata() == null ? null : result.metadata().get("agentId");
            outbound.setSenderActorId(agentId == null ? null : String.valueOf(agentId));
            outbound.setDurationMs(durationMs);
            outbound.setIdempotencyKey("agent-reply:" + entity.getId());
            outbound.setAttempts(0);
            outbound.setNextAttemptAt(LocalDateTime.now());
            TenantSqlScope.bypass(() -> mapper.insert(outbound));
            touchOutbound(outbound);
        }
        return view(entity);
    }

    /** Returns the prior result for the runtime idempotency key without executing the Agent again. */
    public Optional<ChannelInboundResult> priorResult(ChannelInboundEvent event) {
        if (event.messageId() == null || event.messageId().isBlank()) return Optional.empty();
        ChannelEventEntity entity =
                TenantSqlScope.bypass(() -> mapper.selectOne(new LambdaQueryWrapper<ChannelEventEntity>()
                        .eq(ChannelEventEntity::getProvider, event.provider())
                        .eq(ChannelEventEntity::getChannelId, event.channelId())
                        .eq(ChannelEventEntity::getAccountId, event.accountId())
                        .eq(ChannelEventEntity::getMessageId, event.messageId())
                        .last("LIMIT 1")));
        if (entity == null) return Optional.empty();
        return Optional.of(duplicateResult(entity));
    }

    private static ChannelInboundResult duplicateResult(ChannelEventEntity entity) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("duplicate", true);
        metadata.put("managed", entity.getConnectionId() != null);
        if (entity.getConnectionId() != null) metadata.put("connectionId", entity.getConnectionId());
        metadata.put(
                "replyQueued",
                entity.getReplyContent() != null && !entity.getReplyContent().isBlank());
        return new ChannelInboundResult(Boolean.TRUE.equals(entity.getHandled()), null, "duplicate", metadata);
    }

    private ChannelEventEntity findInbound(ChannelInboundEvent event, ChannelConnectionService.Ownership owner) {
        LambdaQueryWrapper<ChannelEventEntity> query = new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getProvider, event.provider())
                .eq(ChannelEventEntity::getChannelId, event.channelId())
                .eq(ChannelEventEntity::getAccountId, event.accountId())
                .eq(ChannelEventEntity::getMessageId, event.messageId());
        if (owner != null) {
            query.eq(ChannelEventEntity::getTenantId, owner.tenantId())
                    .eq(ChannelEventEntity::getConnectionId, owner.connectionId());
        } else if (event.runtimeNodeId() != null) {
            query.eq(ChannelEventEntity::getRuntimeNodeId, event.runtimeNodeId());
        }
        return TenantSqlScope.bypass(() -> mapper.selectOne(query.last("LIMIT 1")));
    }

    private static ChannelEventEntity inboundEntity(
            ChannelInboundEvent event, ChannelConnectionService.Ownership owner) {
        ChannelEventEntity entity = new ChannelEventEntity();
        entity.setTenantId(owner == null ? "default" : owner.tenantId());
        entity.setConnectionId(owner == null ? null : owner.connectionId());
        entity.setRuntimeNodeId(owner == null ? event.runtimeNodeId() : owner.runtimeNodeId());
        entity.setOwnerId(owner == null ? null : owner.ownerId());
        entity.setProvider(event.provider());
        entity.setChannelId(event.channelId());
        entity.setAccountId(event.accountId());
        entity.setMessageId(event.messageId());
        entity.setSenderId(event.senderId());
        entity.setReplyTargetId(replyTarget(event));
        entity.setConversationId(event.conversationId());
        entity.setDirection("INBOUND");
        entity.setContent(event.content());
        entity.setMessageType(event.messageType());
        entity.setAttachmentsJson(event.attachments().isEmpty() ? null : JsonUtils.toJson(event.attachments()));
        entity.setContentJson(event.contentPayload().isEmpty() ? null : JsonUtils.toJson(event.contentPayload()));
        if (event.timestamp() != null)
            entity.setEventTime(LocalDateTime.ofInstant(event.timestamp(), ZoneId.systemDefault()));
        return entity;
    }

    private ChannelConnectionService.Ownership ownership(ChannelInboundEvent event) {
        return event.runtimeNodeId() == null
                ? connections.ownership(event.provider(), event.channelId(), event.accountId())
                : connections.ownership(event.provider(), event.runtimeNodeId(), event.channelId(), event.accountId());
    }

    public List<View> list(String tenantId, String connectionId, int limit) {
        LambdaQueryWrapper<ChannelEventEntity> query = new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getTenantId, tenantId == null || tenantId.isBlank() ? "default" : tenantId)
                .orderByDesc(ChannelEventEntity::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        if (connectionId != null && !connectionId.isBlank())
            query.eq(ChannelEventEntity::getConnectionId, connectionId);
        return mapper.selectList(query).stream()
                .map(ChannelEventLogService::view)
                .toList();
    }

    /** Stable keyset pagination for a tenant timeline or one conversation. */
    public Page page(String tenantId, String connectionId, String conversationId, String cursor, int limit) {
        int pageSize = Math.max(1, Math.min(limit, 200));
        Cursor position = decodeCursor(cursor);
        LambdaQueryWrapper<ChannelEventEntity> query = new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getTenantId, tenantId == null || tenantId.isBlank() ? "default" : tenantId)
                .orderByDesc(ChannelEventEntity::getCreatedAt)
                .orderByDesc(ChannelEventEntity::getId)
                .last("LIMIT " + (pageSize + 1));
        if (connectionId != null && !connectionId.isBlank()) {
            query.eq(ChannelEventEntity::getConnectionId, connectionId);
        }
        if (conversationId != null && !conversationId.isBlank()) {
            query.eq(ChannelEventEntity::getConversationId, conversationId);
        }
        if (position != null) {
            query.and(q -> q.lt(ChannelEventEntity::getCreatedAt, position.createdAt())
                    .or(nested -> nested.eq(ChannelEventEntity::getCreatedAt, position.createdAt())
                            .lt(ChannelEventEntity::getId, position.id())));
        }
        List<ChannelEventEntity> rows = mapper.selectList(query);
        boolean hasMore = rows.size() > pageSize;
        List<ChannelEventEntity> slice = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore && !slice.isEmpty() ? encodeCursor(slice.getLast()) : null;
        return new Page(slice.stream().map(ChannelEventLogService::view).toList(), next, hasMore);
    }

    public View retry(String tenantId, String eventId) {
        ChannelEventEntity entity = owned(tenantId, eventId);
        ChannelInboundEvent event = new ChannelInboundEvent(
                entity.getProvider(),
                entity.getChannelId(),
                entity.getAccountId(),
                entity.getMessageId(),
                entity.getSenderId(),
                entity.getConversationId(),
                entity.getContent(),
                defaulted(entity.getMessageType(), "TEXT"),
                attachments(entity),
                contentPayload(entity),
                entity.getEventTime() == null
                        ? null
                        : entity.getEventTime().atZone(ZoneId.systemDefault()).toInstant(),
                false,
                java.util.Map.of("recoveryEventId", entity.getId()));
        long started = System.nanoTime();
        try {
            ChannelInboundResult result = dispatcher.dispatch(event);
            if (result.reply() != null && entity.getSenderId() != null) {
                ChannelEventEntity outbound = replyEvent(entity, result.reply(), "PENDING");
                outbound.setSenderType("AGENT");
                outbound.setIdempotencyKey("agent-retry:" + entity.getId() + ":" + UUID.randomUUID());
                outbound.setAttempts(0);
                outbound.setNextAttemptAt(LocalDateTime.now());
                outbound.setDurationMs((System.nanoTime() - started) / 1_000_000);
                mapper.insert(outbound);
                touchOutbound(outbound);
            }
            entity.setHandled(result.handled());
            entity.setReplyContent(result.reply());
            entity.setStatus(result.handled() ? "RETRIED" : "RETRY_UNHANDLED");
            entity.setErrorMessage(null);
        } catch (RuntimeException ex) {
            entity.setStatus("ERROR");
            entity.setErrorMessage(ex.getMessage());
        }
        entity.setDurationMs((System.nanoTime() - started) / 1_000_000);
        updateOwned(entity);
        return view(entity);
    }

    /** Terminal outbound failures for the current tenant only. */
    public List<View> deadLetters(String tenantId, int limit) {
        String trustedTenant = requiredTenant(tenantId);
        int pageSize = Math.max(1, Math.min(limit, 200));
        int maxAttempts = Math.max(1, properties.getOutboxMaxAttempts());
        return mapper
                .selectList(new LambdaQueryWrapper<ChannelEventEntity>()
                        .eq(ChannelEventEntity::getTenantId, trustedTenant)
                        .eq(ChannelEventEntity::getDirection, "OUTBOUND")
                        .eq(ChannelEventEntity::getStatus, "FAILED")
                        .ge(ChannelEventEntity::getAttempts, maxAttempts)
                        .orderByDesc(ChannelEventEntity::getCreatedAt)
                        .orderByDesc(ChannelEventEntity::getId)
                        .last("LIMIT " + pageSize))
                .stream()
                .map(ChannelEventLogService::view)
                .toList();
    }

    /** Requeues only still-terminal rows, fenced by tenant, direction, status and attempt count. */
    public DeadLetterReplayResult replayDeadLetters(String tenantId, List<String> eventIds) {
        String trustedTenant = requiredTenant(tenantId);
        List<String> ids = eventIds == null
                ? List.of()
                : eventIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .distinct()
                        .limit(100)
                        .toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("eventIds is required");
        int maxAttempts = Math.max(1, properties.getOutboxMaxAttempts());
        List<ChannelEventEntity> eligible = mapper.selectList(new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getTenantId, trustedTenant)
                .eq(ChannelEventEntity::getDirection, "OUTBOUND")
                .eq(ChannelEventEntity::getStatus, "FAILED")
                .ge(ChannelEventEntity::getAttempts, maxAttempts)
                .in(ChannelEventEntity::getId, ids));
        int requeued = 0;
        for (ChannelEventEntity event : eligible) {
            requeued += mapper.update(
                    null,
                    new LambdaUpdateWrapper<ChannelEventEntity>()
                            .set(ChannelEventEntity::getStatus, "PENDING")
                            .set(ChannelEventEntity::getAttempts, 0)
                            .set(ChannelEventEntity::getNextAttemptAt, LocalDateTime.now())
                            .set(ChannelEventEntity::getLeaseOwner, null)
                            .set(ChannelEventEntity::getLeaseUntil, null)
                            .set(ChannelEventEntity::getErrorMessage, null)
                            .eq(ChannelEventEntity::getTenantId, trustedTenant)
                            .eq(ChannelEventEntity::getId, event.getId())
                            .eq(ChannelEventEntity::getDirection, "OUTBOUND")
                            .eq(ChannelEventEntity::getStatus, "FAILED")
                            .ge(ChannelEventEntity::getAttempts, maxAttempts));
        }
        return new DeadLetterReplayResult(ids.size(), eligible.size(), requeued);
    }

    public View handoff(String tenantId, String eventId, String note) {
        return handoff(tenantId, eventId, note, null);
    }

    public View handoff(String tenantId, String eventId, String note, String assignmentGroup) {
        ChannelEventEntity entity = owned(tenantId, eventId);
        entity.setStatus("HANDOFF");
        entity.setErrorMessage(note == null || note.isBlank() ? "等待人工处理" : note.trim());
        updateOwned(entity);
        if (conversations != null && entity.getConnectionId() != null && entity.getConversationId() != null) {
            conversations.ensure(entity);
            conversations.requestHandoff(
                    entity.getTenantId(), entity.getConnectionId(), entity.getConversationId(), assignmentGroup);
        }
        return view(entity);
    }

    /** Durably enqueues an operator-authored reply; delivery happens outside the HTTP request thread. */
    public View manualReply(String tenantId, String eventId, String content) {
        return manualReply(tenantId, eventId, content, null, "EMPLOYEE", null);
    }

    /** Queues a workflow reply using the original platform message as its routing source. */
    public View workflowReply(ChannelInboundEvent event, String content, String idempotencyKey) {
        ChannelConnectionService.Ownership owner = ownership(event);
        ChannelEventEntity source = findInbound(event, owner);
        if (source == null) throw new IllegalStateException("inbound channel event was not persisted");
        return manualReply(source.getTenantId(), source.getId(), content, idempotencyKey, "SYSTEM", null);
    }

    public View manualReply(
            String tenantId,
            String eventId,
            String content,
            String idempotencyKey,
            String senderType,
            String senderActorId) {
        return manualReply(
                tenantId,
                eventId,
                content,
                "TEXT",
                List.of(),
                java.util.Map.of(),
                idempotencyKey,
                senderType,
                senderActorId);
    }

    public View manualReply(
            String tenantId,
            String eventId,
            String content,
            String messageType,
            List<ChannelAttachment> messageAttachments,
            java.util.Map<String, Object> contentPayload,
            String idempotencyKey,
            String senderType,
            String senderActorId) {
        ChannelEventEntity source = owned(tenantId, eventId);
        if (conversations != null && source.getConnectionId() != null && source.getConversationId() != null) {
            conversations.ensure(source);
        }
        if (source.getSenderId() == null || source.getSenderId().isBlank()) {
            throw new IllegalArgumentException("channel event has no reply target");
        }
        List<ChannelAttachment> safeAttachments =
                messageAttachments == null ? List.of() : List.copyOf(messageAttachments);
        if ((content == null || content.isBlank()) && safeAttachments.isEmpty()) {
            throw new IllegalArgumentException("reply content or attachment is required");
        }
        if (safeAttachments.size() > 10) throw new IllegalArgumentException("at most 10 attachments are allowed");
        for (ChannelAttachment attachment : safeAttachments) {
            if (attachment.url() == null
                    || !(attachment.url().startsWith("https://")
                            || attachment.url().startsWith("http://"))) {
                throw new IllegalArgumentException("attachment URL must use http or https");
            }
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey.trim();
        if (key.length() > 128) throw new IllegalArgumentException("idempotency key is too long");
        ChannelEventEntity prior = findByIdempotencyKey(source.getTenantId(), key);
        if (prior != null) return view(prior);
        String reply = content == null ? "" : content.trim();
        ChannelEventEntity outbound = replyEvent(source, reply, "PENDING");
        String normalizedType = messageType == null || messageType.isBlank()
                ? "TEXT"
                : messageType.trim().toUpperCase();
        if ("TEXT".equals(normalizedType) && !safeAttachments.isEmpty())
            normalizedType = safeAttachments.getFirst().type();
        outbound.setMessageType(normalizedType);
        outbound.setAttachmentsJson(safeAttachments.isEmpty() ? null : JsonUtils.toJson(safeAttachments));
        java.util.Map<String, Object> payload = contentPayload == null ? java.util.Map.of() : contentPayload;
        outbound.setContentJson(payload.isEmpty() ? null : JsonUtils.toJson(payload));
        outbound.setIdempotencyKey(key);
        outbound.setSenderType(senderType == null || senderType.isBlank() ? "EMPLOYEE" : senderType);
        outbound.setSenderActorId(senderActorId);
        outbound.setAttempts(0);
        outbound.setNextAttemptAt(LocalDateTime.now());
        try {
            mapper.insert(outbound);
            touchOutbound(outbound);
        } catch (DuplicateKeyException ex) {
            ChannelEventEntity raced = findByIdempotencyKey(source.getTenantId(), key);
            if (raced != null) return view(raced);
            throw ex;
        }
        return view(outbound);
    }

    /** Persists an operator-only note. It never enters the outbound queue. */
    public View internalNote(String tenantId, String eventId, String content, String actorId) {
        ChannelEventEntity source = owned(tenantId, eventId);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("note content is required");
        ChannelEventEntity note = replyEvent(source, content.trim(), "NOTED");
        note.setDirection("INTERNAL");
        note.setMessageType("INTERNAL_NOTE");
        note.setSenderType("EMPLOYEE");
        note.setSenderActorId(actorId);
        note.setIdempotencyKey(null);
        note.setNextAttemptAt(null);
        note.setAttempts(0);
        mapper.insert(note);
        return view(note);
    }

    /** Claims and delivers due rows. Safe for multiple application instances through a DB lease. */
    public int deliverPendingBatch(String workerId) {
        return TenantSqlScope.bypass(() -> deliverPendingBatchAcrossTenants(workerId));
    }

    private int deliverPendingBatchAcrossTenants(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, Math.min(properties.getOutboxBatchSize(), 200));
        int maxAttempts = Math.max(1, properties.getOutboxMaxAttempts());
        // A worker can die after claiming a message. Expired leases must not leave
        // the row permanently stuck in SENDING. Exhausted rows become an explicit
        // terminal-looking FAILED record for operators; retryable rows are reclaimed below.
        mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelEventEntity>()
                        .set(ChannelEventEntity::getStatus, "FAILED")
                        .set(ChannelEventEntity::getLeaseOwner, null)
                        .set(ChannelEventEntity::getLeaseUntil, null)
                        .set(ChannelEventEntity::getErrorMessage, "Outbox worker lease expired after maximum attempts")
                        .eq(ChannelEventEntity::getDirection, "OUTBOUND")
                        .eq(ChannelEventEntity::getStatus, "SENDING")
                        .ge(ChannelEventEntity::getAttempts, maxAttempts)
                        .lt(ChannelEventEntity::getLeaseUntil, now));
        List<ChannelEventEntity> due = mapper.selectList(new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getDirection, "OUTBOUND")
                .in(ChannelEventEntity::getStatus, "PENDING", "FAILED", "SENDING")
                .lt(ChannelEventEntity::getAttempts, maxAttempts)
                .and(q -> q.isNull(ChannelEventEntity::getNextAttemptAt)
                        .or()
                        .le(ChannelEventEntity::getNextAttemptAt, now))
                .and(q -> q.isNull(ChannelEventEntity::getLeaseUntil).or().lt(ChannelEventEntity::getLeaseUntil, now))
                .orderByAsc(ChannelEventEntity::getNextAttemptAt)
                .last("LIMIT " + batchSize));
        int delivered = 0;
        for (ChannelEventEntity candidate : due) {
            Duration delay = rateLimiter.acquire(rateLimitScope(candidate), properties.getOutboxMaxSendsPerSecond());
            if (!delay.isZero()) {
                observer.outboundRateLimited(
                        candidate.getProvider(), candidate.getChannelId(), candidate.getRuntimeNodeId(), delay);
                mapper.update(
                        null,
                        new LambdaUpdateWrapper<ChannelEventEntity>()
                                .set(ChannelEventEntity::getNextAttemptAt, now.plus(delay))
                                .eq(ChannelEventEntity::getId, candidate.getId())
                                .in(ChannelEventEntity::getStatus, "PENDING", "FAILED"));
                continue;
            }
            if (!claim(candidate, workerId, now)) continue;
            deliver(candidate, workerId);
            delivered++;
        }
        return delivered;
    }

    private void updateOwned(ChannelEventEntity entity) {
        mapper.update(
                entity,
                new LambdaUpdateWrapper<ChannelEventEntity>()
                        .eq(ChannelEventEntity::getTenantId, entity.getTenantId())
                        .eq(ChannelEventEntity::getId, entity.getId()));
    }

    private boolean claim(ChannelEventEntity event, String workerId, LocalDateTime now) {
        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelEventEntity>()
                        .set(ChannelEventEntity::getStatus, "SENDING")
                        .set(ChannelEventEntity::getLeaseOwner, workerId)
                        .set(ChannelEventEntity::getLeaseUntil, now.plus(properties.getOutboxLeaseDuration()))
                        .setSql("attempts = attempts + 1")
                        .eq(ChannelEventEntity::getId, event.getId())
                        .in(ChannelEventEntity::getStatus, "PENDING", "FAILED", "SENDING")
                        .and(q -> q.isNull(ChannelEventEntity::getLeaseUntil)
                                .or()
                                .lt(ChannelEventEntity::getLeaseUntil, now)));
        return updated == 1;
    }

    private void deliver(ChannelEventEntity event, String workerId) {
        long started = System.nanoTime();
        try {
            ChannelSendResult result = runtimes.require(event.getProvider(), event.getRuntimeNodeId())
                    .sendWithResult(new ChannelOutboundMessage(
                            event.getChannelId(),
                            event.getAccountId(),
                            defaulted(event.getReplyTargetId(), event.getSenderId()),
                            event.getConversationId(),
                            event.getContent(),
                            defaulted(event.getMessageType(), "TEXT"),
                            attachments(event),
                            contentPayload(event),
                            outboundMetadata(event)));
            mapper.update(
                    null,
                    terminalUpdate(event, workerId)
                            .set(ChannelEventEntity::getStatus, "SENT")
                            .set(ChannelEventEntity::getPlatformMessageId, result.platformMessageId())
                            .set(ChannelEventEntity::getSentAt, LocalDateTime.now())
                            .set(ChannelEventEntity::getDurationMs, (System.nanoTime() - started) / 1_000_000)
                            .set(ChannelEventEntity::getErrorMessage, null)
                            .set(ChannelEventEntity::getNextAttemptAt, null));
            observer.outboundFinished(
                    event.getProvider(),
                    event.getChannelId(),
                    event.getRuntimeNodeId(),
                    "success",
                    Duration.ofNanos(System.nanoTime() - started));
        } catch (RuntimeException ex) {
            int attempt = event.getAttempts() == null ? 1 : event.getAttempts() + 1;
            long backoffSeconds = Math.min(300, 1L << Math.min(attempt - 1, 8));
            mapper.update(
                    null,
                    terminalUpdate(event, workerId)
                            .set(ChannelEventEntity::getStatus, "FAILED")
                            .set(ChannelEventEntity::getDurationMs, (System.nanoTime() - started) / 1_000_000)
                            .set(ChannelEventEntity::getErrorMessage, abbreviate(ex.getMessage(), 4000))
                            .set(
                                    ChannelEventEntity::getNextAttemptAt,
                                    LocalDateTime.now().plusSeconds(backoffSeconds)));
            observer.outboundFinished(
                    event.getProvider(),
                    event.getChannelId(),
                    event.getRuntimeNodeId(),
                    "failure",
                    Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private java.util.Map<String, Object> outboundMetadata(ChannelEventEntity event) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
        if (event.getIdempotencyKey() != null) values.put("idempotencyKey", event.getIdempotencyKey());
        if (event.getReplyToEventId() != null) {
            values.put("sourceEventId", event.getReplyToEventId());
            ChannelEventEntity source = mapper.selectOne(new LambdaQueryWrapper<ChannelEventEntity>()
                    .eq(ChannelEventEntity::getTenantId, event.getTenantId())
                    .eq(ChannelEventEntity::getId, event.getReplyToEventId())
                    .last("LIMIT 1"));
            if (source != null) {
                if (source.getMessageId() != null) values.put("replyToPlatformMessageId", source.getMessageId());
                Object conversationType = contentPayload(source).get("conversationType");
                if (conversationType != null) values.put("conversationType", conversationType);
            }
        }
        return values;
    }

    /** Authenticated provider callback that advances a sent message to DELIVERED. */
    public boolean markDelivered(
            String provider, String channelId, String accountId, String platformMessageId, LocalDateTime deliveredAt) {
        if (platformMessageId == null || platformMessageId.isBlank()) {
            throw new IllegalArgumentException("platformMessageId is required");
        }
        ChannelConnectionService.Ownership owner = connections.ownership(provider, channelId, accountId);
        if (owner == null) return false;
        return TenantSqlScope.bypass(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<ChannelEventEntity>()
                                .set(ChannelEventEntity::getStatus, "DELIVERED")
                                .set(
                                        ChannelEventEntity::getDeliveredAt,
                                        deliveredAt == null ? LocalDateTime.now() : deliveredAt)
                                .eq(ChannelEventEntity::getTenantId, owner.tenantId())
                                .eq(ChannelEventEntity::getConnectionId, owner.connectionId())
                                .eq(ChannelEventEntity::getProvider, provider)
                                .eq(ChannelEventEntity::getChannelId, channelId)
                                .eq(ChannelEventEntity::getAccountId, accountId)
                                .eq(ChannelEventEntity::getPlatformMessageId, platformMessageId)
                                .in(ChannelEventEntity::getStatus, "SENT", "DELIVERED")))
                > 0;
    }

    private static ChannelOutboundRateLimiter.Scope rateLimitScope(ChannelEventEntity event) {
        return new ChannelOutboundRateLimiter.Scope(
                event.getTenantId(), event.getProvider(), event.getRuntimeNodeId(), event.getAccountId());
    }

    private void touchInbound(ChannelEventEntity event) {
        if (conversations != null && event.getConnectionId() != null && event.getConversationId() != null) {
            conversations.touchInbound(event);
        }
    }

    private void touchOutbound(ChannelEventEntity event) {
        if (conversations != null && event.getConnectionId() != null && event.getConversationId() != null) {
            conversations.touchOutbound(event);
        }
    }

    private LambdaUpdateWrapper<ChannelEventEntity> terminalUpdate(ChannelEventEntity event, String workerId) {
        return new LambdaUpdateWrapper<ChannelEventEntity>()
                .set(ChannelEventEntity::getLeaseOwner, null)
                .set(ChannelEventEntity::getLeaseUntil, null)
                .eq(ChannelEventEntity::getId, event.getId())
                .eq(ChannelEventEntity::getStatus, "SENDING")
                .eq(ChannelEventEntity::getLeaseOwner, workerId);
    }

    private ChannelEventEntity findByIdempotencyKey(String tenantId, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getTenantId, tenantId)
                .eq(ChannelEventEntity::getIdempotencyKey, key)
                .last("LIMIT 1"));
    }

    private static String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static String requiredTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        return tenantId.trim();
    }

    private static List<ChannelAttachment> attachments(ChannelEventEntity entity) {
        return JsonUtils.parseList(entity.getAttachmentsJson(), ChannelAttachment.class);
    }

    private static java.util.Map<String, Object> contentPayload(ChannelEventEntity entity) {
        return JsonUtils.parseMap(entity.getContentJson());
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Cursor(LocalDateTime createdAt, String id) {}

    private static String encodeCursor(ChannelEventEntity entity) {
        String raw = entity.getCreatedAt() + "|" + entity.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator <= 0 || separator == raw.length() - 1) throw new IllegalArgumentException();
            return new Cursor(LocalDateTime.parse(raw.substring(0, separator)), raw.substring(separator + 1));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid channel event cursor");
        }
    }

    private static ChannelEventEntity replyEvent(ChannelEventEntity source, String content, String status) {
        ChannelEventEntity outbound = new ChannelEventEntity();
        outbound.setTenantId(source.getTenantId());
        outbound.setConnectionId(source.getConnectionId());
        outbound.setRuntimeNodeId(source.getRuntimeNodeId());
        outbound.setOwnerId(source.getOwnerId());
        outbound.setProvider(source.getProvider());
        outbound.setChannelId(source.getChannelId());
        outbound.setAccountId(source.getAccountId());
        outbound.setSenderId(source.getSenderId());
        outbound.setReplyTargetId(source.getReplyTargetId());
        outbound.setConversationId(source.getConversationId());
        outbound.setReplyToEventId(source.getId());
        outbound.setDirection("OUTBOUND");
        outbound.setContent(content.trim());
        outbound.setHandled(true);
        outbound.setStatus(status);
        outbound.setMessageType("TEXT");
        outbound.setEventTime(LocalDateTime.now());
        return outbound;
    }

    private ChannelEventEntity owned(String tenantId, String eventId) {
        ChannelEventEntity entity = mapper.selectOne(new LambdaQueryWrapper<ChannelEventEntity>()
                .eq(ChannelEventEntity::getTenantId, tenantId == null || tenantId.isBlank() ? "default" : tenantId)
                .eq(ChannelEventEntity::getId, eventId)
                .last("LIMIT 1"));
        if (entity == null) throw new IllegalArgumentException("channel event not found");
        return entity;
    }

    private static View view(ChannelEventEntity e) {
        return new View(
                e.getId(),
                e.getTenantId(),
                e.getConnectionId(),
                e.getRuntimeNodeId(),
                e.getOwnerId(),
                e.getProvider(),
                e.getChannelId(),
                e.getAccountId(),
                e.getMessageId(),
                e.getSenderId(),
                e.getReplyTargetId(),
                e.getConversationId(),
                e.getReplyToEventId(),
                e.getDirection(),
                e.getContent(),
                defaulted(e.getMessageType(), "TEXT"),
                attachments(e),
                contentPayload(e),
                Boolean.TRUE.equals(e.getHandled()),
                e.getStatus(),
                e.getReplyContent(),
                e.getDurationMs(),
                e.getErrorMessage(),
                e.getIdempotencyKey(),
                e.getPlatformMessageId(),
                e.getSenderType(),
                e.getSenderActorId(),
                e.getAttempts(),
                e.getNextAttemptAt(),
                e.getSentAt(),
                e.getDeliveredAt(),
                e.getEventTime(),
                e.getCreatedAt());
    }

    private static String replyTarget(ChannelInboundEvent event) {
        Object explicit = event.metadata().get("replyTargetId");
        if (explicit != null && !String.valueOf(explicit).isBlank())
            return String.valueOf(explicit).trim();
        Object chat = event.metadata().get("chatId");
        if (event.group() && chat != null && !String.valueOf(chat).isBlank())
            return String.valueOf(chat).trim();
        return event.senderId();
    }
}
