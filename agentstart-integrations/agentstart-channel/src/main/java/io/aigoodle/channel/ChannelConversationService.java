package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.channel.persistence.ChannelConversationEntity;
import io.aigoodle.channel.persistence.ChannelConversationMapper;
import io.aigoodle.channel.persistence.ChannelEventEntity;
import io.aigoodle.persistence.TenantSqlScope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;

/** Durable customer-service conversation aggregate and optimistic assignment lock. */
public class ChannelConversationService {
    public record View(
            String id,
            String tenantId,
            String connectionId,
            String ownerId,
            String provider,
            String channelId,
            String accountId,
            String conversationId,
            String agentId,
            String agentVersionId,
            String agentRouteReason,
            long routingPolicyVersion,
            String status,
            boolean agentPaused,
            String assigneeId,
            String assigneeName,
            String assignmentGroup,
            LocalDateTime handoffRequestedAt,
            LocalDateTime claimedAt,
            LocalDateTime closedAt,
            LocalDateTime lastMessageAt,
            String lastEventId,
            String lastMessagePreview,
            int unreadCount,
            LocalDateTime slaDueAt,
            boolean slaBreached,
            long lockVersion) {}

    public record Summary(long total, long waitingHuman, long humanActive, long slaBreached, long unread) {}

    public record Page(List<View> items, String nextCursor, boolean hasMore) {}

    public record SlaCandidate(String id, String tenantId, LocalDateTime dueAt, int stage) {}

    public record AgentAssignment(
            String agentId, String agentVersionId, String routeReason, long routingPolicyVersion) {}

    private final ChannelConversationMapper mapper;
    private final Duration defaultSla;

    public ChannelConversationService(ChannelConversationMapper mapper) {
        this(mapper, Duration.ofMinutes(15));
    }

    public ChannelConversationService(ChannelConversationMapper mapper, Duration defaultSla) {
        this.mapper = mapper;
        this.defaultSla = defaultSla == null || defaultSla.isNegative() ? Duration.ofMinutes(15) : defaultSla;
    }

    public View touchInbound(ChannelEventEntity event) {
        ChannelConversationEntity row = find(event.getTenantId(), event.getConnectionId(), event.getConversationId());
        if (row == null) {
            row = new ChannelConversationEntity();
            row.setTenantId(event.getTenantId());
            row.setConnectionId(event.getConnectionId());
            row.setOwnerId(event.getOwnerId());
            row.setProvider(event.getProvider());
            row.setChannelId(event.getChannelId());
            row.setAccountId(event.getAccountId());
            row.setConversationId(event.getConversationId());
            row.setStatus("BOT_ACTIVE");
            row.setAgentPaused(false);
            row.setUnreadCount(1);
            row.setLockVersion(1L);
            applyLastMessage(row, event);
            try {
                mapper.insert(row);
                return view(row);
            } catch (DuplicateKeyException race) {
                row = require(event.getTenantId(), event.getConnectionId(), event.getConversationId());
            }
        }
        mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getLastMessageAt, messageTime(event))
                        .set(ChannelConversationEntity::getLastEventId, event.getId())
                        .set(ChannelConversationEntity::getLastMessagePreview, preview(event.getContent()))
                        .setSql("unread_count = unread_count + 1, lock_version = lock_version + 1")
                        .eq(ChannelConversationEntity::getTenantId, event.getTenantId())
                        .eq(ChannelConversationEntity::getId, row.getId()));
        return view(require(event.getTenantId(), event.getConnectionId(), event.getConversationId()));
    }

    /** Lazily materializes conversations for events created before this aggregate existed. */
    public View ensure(ChannelEventEntity event) {
        ChannelConversationEntity existing =
                find(event.getTenantId(), event.getConnectionId(), event.getConversationId());
        if (existing != null) return view(existing);
        ChannelConversationEntity row = new ChannelConversationEntity();
        row.setTenantId(event.getTenantId());
        row.setConnectionId(event.getConnectionId());
        row.setOwnerId(event.getOwnerId());
        row.setProvider(event.getProvider());
        row.setChannelId(event.getChannelId());
        row.setAccountId(event.getAccountId());
        row.setConversationId(event.getConversationId());
        row.setStatus("BOT_ACTIVE");
        row.setAgentPaused(false);
        row.setUnreadCount(0);
        row.setLockVersion(1L);
        applyLastMessage(row, event);
        try {
            mapper.insert(row);
            return view(row);
        } catch (DuplicateKeyException race) {
            return view(require(event.getTenantId(), event.getConnectionId(), event.getConversationId()));
        }
    }

    public View touchOutbound(ChannelEventEntity event) {
        ChannelConversationEntity row =
                require(event.getTenantId(), event.getConnectionId(), event.getConversationId());
        mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getLastMessageAt, messageTime(event))
                        .set(ChannelConversationEntity::getLastEventId, event.getId())
                        .set(ChannelConversationEntity::getLastMessagePreview, preview(event.getContent()))
                        .set(ChannelConversationEntity::getUnreadCount, 0)
                        .setSql("lock_version = lock_version + 1")
                        .eq(ChannelConversationEntity::getTenantId, event.getTenantId())
                        .eq(ChannelConversationEntity::getId, row.getId()));
        return view(require(event.getTenantId(), event.getConnectionId(), event.getConversationId()));
    }

    public View requestHandoff(String tenantId, String connectionId, String conversationId, String assignmentGroup) {
        ChannelConversationEntity row = require(tenantId, connectionId, conversationId);
        LocalDateTime now = LocalDateTime.now();
        mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getStatus, "WAITING_HUMAN")
                        .set(ChannelConversationEntity::getAgentPaused, true)
                        .set(ChannelConversationEntity::getAssignmentGroup, text(assignmentGroup))
                        .set(ChannelConversationEntity::getHandoffRequestedAt, now)
                        .set(ChannelConversationEntity::getSlaDueAt, now.plus(defaultSla))
                        .set(ChannelConversationEntity::getSlaReminderStage, 0)
                        .set(ChannelConversationEntity::getSlaRemindedAt, null)
                        .set(ChannelConversationEntity::getSlaReminderLeaseUntil, null)
                        .set(ChannelConversationEntity::getSlaReminderLeaseOwner, null)
                        .set(ChannelConversationEntity::getAssigneeId, null)
                        .set(ChannelConversationEntity::getAssigneeName, null)
                        .setSql("lock_version = lock_version + 1")
                        .eq(ChannelConversationEntity::getTenantId, tenantId)
                        .eq(ChannelConversationEntity::getId, row.getId())
                        .ne(ChannelConversationEntity::getStatus, "CLOSED"));
        return view(require(tenantId, connectionId, conversationId));
    }

    /** Exactly one operator can claim a waiting conversation at an expected version. */
    public View claim(
            String tenantId,
            String id,
            long expectedVersion,
            String actorId,
            String actorName,
            String assignmentGroup) {
        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getStatus, "HUMAN_ACTIVE")
                        .set(ChannelConversationEntity::getAgentPaused, true)
                        .set(ChannelConversationEntity::getAssigneeId, required(actorId, "actorId"))
                        .set(ChannelConversationEntity::getAssigneeName, text(actorName))
                        .set(ChannelConversationEntity::getAssignmentGroup, text(assignmentGroup))
                        .set(ChannelConversationEntity::getClaimedAt, LocalDateTime.now())
                        .set(ChannelConversationEntity::getUnreadCount, 0)
                        .setSql("lock_version = lock_version + 1")
                        .eq(ChannelConversationEntity::getTenantId, tenantId)
                        .eq(ChannelConversationEntity::getId, id)
                        .eq(ChannelConversationEntity::getStatus, "WAITING_HUMAN")
                        .eq(ChannelConversationEntity::getLockVersion, expectedVersion));
        if (updated != 1)
            throw new ChannelException(
                    "channel_conversation_claim_conflict",
                    "Conversation was already claimed or changed; refresh before retrying");
        return view(requireById(tenantId, id));
    }

    public View resumeBot(String tenantId, String id, long expectedVersion) {
        return transition(tenantId, id, expectedVersion, "BOT_ACTIVE", false, false);
    }

    public View close(String tenantId, String id, long expectedVersion) {
        return transition(tenantId, id, expectedVersion, "CLOSED", true, true);
    }

    public boolean agentPaused(String tenantId, String connectionId, String conversationId) {
        ChannelConversationEntity row = find(tenantId, connectionId, conversationId);
        return row != null && Boolean.TRUE.equals(row.getAgentPaused());
    }

    public AgentAssignment agentAssignment(String tenantId, String connectionId, String conversationId) {
        ChannelConversationEntity row = find(tenantId, connectionId, conversationId);
        return row == null || text(row.getAgentId()) == null ? null : assignment(row);
    }

    /**
     * Pins the first effective routing decision to a conversation. Later binding or publication changes
     * must not silently change the Agent definition halfway through a customer conversation.
     */
    public AgentAssignment pinAgentRoute(
            String tenantId,
            String connectionId,
            String conversationId,
            String agentId,
            String agentVersionId,
            String routeReason,
            long routingPolicyVersion) {
        ChannelConversationEntity row = require(tenantId, connectionId, conversationId);
        if (text(row.getAgentId()) != null) return assignment(row);
        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getAgentId, required(agentId, "agentId"))
                        .set(ChannelConversationEntity::getAgentVersionId, text(agentVersionId))
                        .set(ChannelConversationEntity::getAgentRouteReason, text(routeReason))
                        .set(ChannelConversationEntity::getRoutingPolicyVersion, Math.max(1, routingPolicyVersion))
                        .setSql("lock_version = lock_version + 1")
                        .eq(ChannelConversationEntity::getTenantId, tenantId)
                        .eq(ChannelConversationEntity::getId, row.getId())
                        .and(q -> q.isNull(ChannelConversationEntity::getAgentId)
                                .or()
                                .eq(ChannelConversationEntity::getAgentId, "")));
        ChannelConversationEntity effective =
                updated == 1 ? requireById(tenantId, row.getId()) : require(tenantId, connectionId, conversationId);
        return assignment(effective);
    }

    /**
     * Atomically promotes a conversation from a failed primary Agent to its configured fallback.
     * The expected primary is a fencing condition. An unpinned new conversation may select the
     * fallback directly; a concurrent request that selected another Agent cannot be overwritten by
     * a late failure from the old Agent.
     */
    public AgentAssignment promoteFallbackRoute(
            String tenantId,
            String connectionId,
            String conversationId,
            String expectedAgentId,
            String fallbackAgentId,
            String fallbackVersionId,
            String routeReason,
            long routingPolicyVersion) {
        ChannelConversationEntity row = require(tenantId, connectionId, conversationId);
        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getAgentId, required(fallbackAgentId, "fallbackAgentId"))
                        .set(ChannelConversationEntity::getAgentVersionId, text(fallbackVersionId))
                        .set(ChannelConversationEntity::getAgentRouteReason, text(routeReason))
                        .set(ChannelConversationEntity::getRoutingPolicyVersion, Math.max(1, routingPolicyVersion))
                        .setSql("lock_version = lock_version + 1")
                        .eq(ChannelConversationEntity::getTenantId, required(tenantId, "tenantId"))
                        .eq(ChannelConversationEntity::getId, row.getId())
                        .and(q -> q.eq(
                                        ChannelConversationEntity::getAgentId,
                                        required(expectedAgentId, "expectedAgentId"))
                                .or()
                                .isNull(ChannelConversationEntity::getAgentId)
                                .or()
                                .eq(ChannelConversationEntity::getAgentId, "")));
        ChannelConversationEntity effective = requireById(tenantId, row.getId());
        if (updated != 1 && !fallbackAgentId.equals(effective.getAgentId())) {
            throw new IllegalStateException("conversation Agent route changed while fallback was being selected");
        }
        return assignment(effective);
    }

    public List<View> list(String tenantId, String status, String assigneeId, boolean slaBreached, int limit) {
        LambdaQueryWrapper<ChannelConversationEntity> query = conversationQuery(
                        tenantId, status, assigneeId, slaBreached, null, null)
                .orderByDesc(ChannelConversationEntity::getLastMessageAt)
                .orderByDesc(ChannelConversationEntity::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return mapper.selectList(query).stream()
                .map(ChannelConversationService::view)
                .toList();
    }

    /** Stable keyset pagination for a tenant queue, optionally narrowed to one channel. */
    public Page page(
            String tenantId,
            String status,
            String assigneeId,
            boolean slaBreached,
            String provider,
            String channelId,
            String cursor,
            int limit) {
        int pageSize = Math.max(1, Math.min(limit, 200));
        Cursor position = decodeCursor(cursor);
        LambdaQueryWrapper<ChannelConversationEntity> query = conversationQuery(
                        tenantId, status, assigneeId, slaBreached, provider, channelId)
                .orderByDesc(ChannelConversationEntity::getLastMessageAt)
                .orderByDesc(ChannelConversationEntity::getId)
                .last("LIMIT " + (pageSize + 1));
        if (position != null) {
            query.and(q -> q.lt(ChannelConversationEntity::getLastMessageAt, position.lastMessageAt())
                    .or(nested -> nested.eq(ChannelConversationEntity::getLastMessageAt, position.lastMessageAt())
                            .lt(ChannelConversationEntity::getId, position.id())));
        }
        List<ChannelConversationEntity> rows = mapper.selectList(query);
        boolean hasMore = rows.size() > pageSize;
        List<ChannelConversationEntity> slice = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore && !slice.isEmpty() ? encodeCursor(slice.getLast()) : null;
        return new Page(slice.stream().map(ChannelConversationService::view).toList(), next, hasMore);
    }

    private LambdaQueryWrapper<ChannelConversationEntity> conversationQuery(
            String tenantId, String status, String assigneeId, boolean slaBreached, String provider, String channelId) {
        LambdaQueryWrapper<ChannelConversationEntity> query = new LambdaQueryWrapper<ChannelConversationEntity>()
                .eq(ChannelConversationEntity::getTenantId, required(tenantId, "tenantId"));
        if (text(status) != null)
            query.eq(ChannelConversationEntity::getStatus, status.trim().toUpperCase());
        if (text(assigneeId) != null) query.eq(ChannelConversationEntity::getAssigneeId, assigneeId.trim());
        if (text(provider) != null) query.eq(ChannelConversationEntity::getProvider, provider.trim());
        if (text(channelId) != null) query.eq(ChannelConversationEntity::getChannelId, channelId.trim());
        if (slaBreached)
            query.in(ChannelConversationEntity::getStatus, "WAITING_HUMAN", "HUMAN_ACTIVE")
                    .lt(ChannelConversationEntity::getSlaDueAt, LocalDateTime.now());
        return query;
    }

    public Summary summary(String tenantId) {
        List<ChannelConversationEntity> rows = mapper.selectList(new LambdaQueryWrapper<ChannelConversationEntity>()
                .eq(ChannelConversationEntity::getTenantId, tenantId));
        LocalDateTime now = LocalDateTime.now();
        return new Summary(
                rows.size(),
                rows.stream().filter(r -> "WAITING_HUMAN".equals(r.getStatus())).count(),
                rows.stream().filter(r -> "HUMAN_ACTIVE".equals(r.getStatus())).count(),
                rows.stream()
                        .filter(r -> r.getSlaDueAt() != null
                                && r.getSlaDueAt().isBefore(now)
                                && !"CLOSED".equals(r.getStatus()))
                        .count(),
                rows.stream()
                        .mapToLong(r -> r.getUnreadCount() == null ? 0 : r.getUnreadCount())
                        .sum());
    }

    public View requireView(String tenantId, String id) {
        return view(requireById(tenantId, id));
    }

    private record Cursor(LocalDateTime lastMessageAt, String id) {}

    private static String encodeCursor(ChannelConversationEntity entity) {
        if (entity.getLastMessageAt() == null || text(entity.getId()) == null) {
            throw new IllegalStateException("conversation cursor fields are missing");
        }
        String raw = entity.getLastMessageAt() + "|" + entity.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator <= 0 || separator == raw.length() - 1) throw new IllegalArgumentException();
            return new Cursor(LocalDateTime.parse(raw.substring(0, separator)), raw.substring(separator + 1));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid channel conversation cursor");
        }
    }

    /** Cross-tenant scan is intentionally restricted to the trusted SLA worker. */
    public List<SlaCandidate> findSlaReminderCandidates(LocalDateTime threshold, int limit) {
        return TenantSqlScope.bypass(() -> mapper
                .selectList(new LambdaQueryWrapper<ChannelConversationEntity>()
                        .in(ChannelConversationEntity::getStatus, "WAITING_HUMAN", "HUMAN_ACTIVE")
                        .isNotNull(ChannelConversationEntity::getSlaDueAt)
                        .le(ChannelConversationEntity::getSlaDueAt, threshold)
                        .orderByAsc(ChannelConversationEntity::getSlaDueAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(row -> new SlaCandidate(
                        row.getId(),
                        row.getTenantId(),
                        row.getSlaDueAt(),
                        row.getSlaReminderStage() == null ? 0 : row.getSlaReminderStage()))
                .toList());
    }

    public ChannelConversationEntity claimSlaReminder(
            SlaCandidate candidate, int targetStage, String workerId, LocalDateTime now, Duration leaseDuration) {
        int updated = TenantSqlScope.bypass(() -> mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getSlaReminderLeaseOwner, workerId)
                        .set(ChannelConversationEntity::getSlaReminderLeaseUntil, now.plus(leaseDuration))
                        .eq(ChannelConversationEntity::getTenantId, candidate.tenantId())
                        .eq(ChannelConversationEntity::getId, candidate.id())
                        .in(ChannelConversationEntity::getStatus, "WAITING_HUMAN", "HUMAN_ACTIVE")
                        .lt(ChannelConversationEntity::getSlaReminderStage, targetStage)
                        .and(q -> q.isNull(ChannelConversationEntity::getSlaReminderLeaseUntil)
                                .or()
                                .lt(ChannelConversationEntity::getSlaReminderLeaseUntil, now))));
        if (updated != 1) return null;
        return TenantSqlScope.bypass(() -> mapper.selectOne(new LambdaQueryWrapper<ChannelConversationEntity>()
                .eq(ChannelConversationEntity::getTenantId, candidate.tenantId())
                .eq(ChannelConversationEntity::getId, candidate.id())
                .last("LIMIT 1")));
    }

    public void completeSlaReminder(String tenantId, String id, String workerId, int stage, LocalDateTime now) {
        TenantSqlScope.bypass(() -> mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getSlaReminderStage, stage)
                        .set(ChannelConversationEntity::getSlaRemindedAt, now)
                        .set(ChannelConversationEntity::getSlaReminderLeaseUntil, null)
                        .set(ChannelConversationEntity::getSlaReminderLeaseOwner, null)
                        .eq(ChannelConversationEntity::getTenantId, tenantId)
                        .eq(ChannelConversationEntity::getId, id)
                        .eq(ChannelConversationEntity::getSlaReminderLeaseOwner, workerId)));
    }

    public void releaseSlaReminder(String tenantId, String id, String workerId) {
        TenantSqlScope.bypass(() -> mapper.update(
                null,
                new LambdaUpdateWrapper<ChannelConversationEntity>()
                        .set(ChannelConversationEntity::getSlaReminderLeaseUntil, null)
                        .set(ChannelConversationEntity::getSlaReminderLeaseOwner, null)
                        .eq(ChannelConversationEntity::getTenantId, tenantId)
                        .eq(ChannelConversationEntity::getId, id)
                        .eq(ChannelConversationEntity::getSlaReminderLeaseOwner, workerId)));
    }

    private View transition(
            String tenantId, String id, long expectedVersion, String status, boolean paused, boolean closing) {
        LambdaUpdateWrapper<ChannelConversationEntity> update = new LambdaUpdateWrapper<ChannelConversationEntity>()
                .set(ChannelConversationEntity::getStatus, status)
                .set(ChannelConversationEntity::getAgentPaused, paused)
                .setSql("lock_version = lock_version + 1")
                .eq(ChannelConversationEntity::getTenantId, tenantId)
                .eq(ChannelConversationEntity::getId, id)
                .eq(ChannelConversationEntity::getLockVersion, expectedVersion);
        if (!paused)
            update.set(ChannelConversationEntity::getAssigneeId, null)
                    .set(ChannelConversationEntity::getAssigneeName, null)
                    .set(ChannelConversationEntity::getClaimedAt, null);
        if (closing) update.set(ChannelConversationEntity::getClosedAt, LocalDateTime.now());
        if (mapper.update(null, update) != 1)
            throw new ChannelException(
                    "channel_conversation_version_conflict", "Conversation changed; refresh before retrying");
        return view(requireById(tenantId, id));
    }

    private ChannelConversationEntity require(String tenantId, String connectionId, String conversationId) {
        ChannelConversationEntity row = find(tenantId, connectionId, conversationId);
        if (row == null) throw new ChannelException("channel_conversation_not_found", "Conversation not found");
        return row;
    }

    private ChannelConversationEntity find(String tenantId, String connectionId, String conversationId) {
        if (text(connectionId) == null || text(conversationId) == null) return null;
        return mapper.selectOne(new LambdaQueryWrapper<ChannelConversationEntity>()
                .eq(ChannelConversationEntity::getTenantId, tenantId)
                .eq(ChannelConversationEntity::getConnectionId, connectionId)
                .eq(ChannelConversationEntity::getConversationId, conversationId)
                .last("LIMIT 1"));
    }

    private ChannelConversationEntity requireById(String tenantId, String id) {
        ChannelConversationEntity row = mapper.selectOne(new LambdaQueryWrapper<ChannelConversationEntity>()
                .eq(ChannelConversationEntity::getTenantId, tenantId)
                .eq(ChannelConversationEntity::getId, id)
                .last("LIMIT 1"));
        if (row == null) throw new ChannelException("channel_conversation_not_found", "Conversation not found");
        return row;
    }

    private static void applyLastMessage(ChannelConversationEntity row, ChannelEventEntity event) {
        row.setLastMessageAt(messageTime(event));
        row.setLastEventId(event.getId());
        row.setLastMessagePreview(preview(event.getContent()));
    }

    private static LocalDateTime messageTime(ChannelEventEntity event) {
        return event.getEventTime() == null ? LocalDateTime.now() : event.getEventTime();
    }

    private static View view(ChannelConversationEntity row) {
        boolean breached = row.getSlaDueAt() != null
                && row.getSlaDueAt().isBefore(LocalDateTime.now())
                && !"CLOSED".equals(row.getStatus());
        return new View(
                row.getId(),
                row.getTenantId(),
                row.getConnectionId(),
                row.getOwnerId(),
                row.getProvider(),
                row.getChannelId(),
                row.getAccountId(),
                row.getConversationId(),
                row.getAgentId(),
                row.getAgentVersionId(),
                row.getAgentRouteReason(),
                row.getRoutingPolicyVersion() == null ? 1 : row.getRoutingPolicyVersion(),
                row.getStatus(),
                Boolean.TRUE.equals(row.getAgentPaused()),
                row.getAssigneeId(),
                row.getAssigneeName(),
                row.getAssignmentGroup(),
                row.getHandoffRequestedAt(),
                row.getClaimedAt(),
                row.getClosedAt(),
                row.getLastMessageAt(),
                row.getLastEventId(),
                row.getLastMessagePreview(),
                row.getUnreadCount() == null ? 0 : row.getUnreadCount(),
                row.getSlaDueAt(),
                breached,
                row.getLockVersion() == null ? 0 : row.getLockVersion());
    }

    private static AgentAssignment assignment(ChannelConversationEntity row) {
        return new AgentAssignment(
                row.getAgentId(),
                row.getAgentVersionId(),
                row.getAgentRouteReason(),
                row.getRoutingPolicyVersion() == null ? 1 : row.getRoutingPolicyVersion());
    }

    private static String preview(String value) {
        return value == null ? null : value.length() <= 240 ? value : value.substring(0, 240);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String name) {
        String result = text(value);
        if (result == null) throw new IllegalArgumentException(name + " is required");
        return result;
    }
}
