package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.channel.persistence.ChannelAuditEntity;
import io.aigoodle.channel.persistence.ChannelAuditMapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.util.JsonUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Secret-free audit trail for channel administration and operator actions. */
public class ChannelAuditService {
    public record View(
            String id,
            String tenantId,
            String actorId,
            String actorName,
            String principalType,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            Map<String, Object> details,
            LocalDateTime createdAt) {}

    private final ChannelAuditMapper mapper;

    public ChannelAuditService(ChannelAuditMapper mapper) {
        this.mapper = mapper;
    }

    public void success(String action, String resourceType, String resourceId, Map<String, Object> details) {
        record(action, resourceType, resourceId, "SUCCESS", details);
    }

    public void record(
            String action, String resourceType, String resourceId, String outcome, Map<String, Object> details) {
        CurrentUser actor = UserContextHolder.get();
        ChannelAuditEntity entity = new ChannelAuditEntity();
        entity.setTenantId(UserContextHolder.currentTenantId());
        entity.setActorId(actor == null ? null : actor.getUserId());
        entity.setActorName(actor == null ? null : actor.getUsername());
        entity.setPrincipalType(
                actor == null || actor.getPrincipalType() == null
                        ? "ANONYMOUS"
                        : actor.getPrincipalType().name());
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setOutcome(outcome == null ? "UNKNOWN" : outcome);
        entity.setDetailsJson(details == null || details.isEmpty() ? null : JsonUtils.toJson(details));
        mapper.insert(entity);
    }

    public List<View> list(String tenantId, int limit) {
        return list(tenantId, null, null, null, null, null, limit);
    }

    public List<View> list(
            String tenantId,
            String action,
            String resourceType,
            String resourceId,
            String actorId,
            String outcome,
            int limit) {
        LambdaQueryWrapper<ChannelAuditEntity> query = new LambdaQueryWrapper<ChannelAuditEntity>()
                .eq(ChannelAuditEntity::getTenantId, required(tenantId))
                .orderByDesc(ChannelAuditEntity::getCreatedAt)
                .orderByDesc(ChannelAuditEntity::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        if (text(action) != null) query.eq(ChannelAuditEntity::getAction, action.trim());
        if (text(resourceType) != null) query.eq(ChannelAuditEntity::getResourceType, resourceType.trim());
        if (text(resourceId) != null) query.eq(ChannelAuditEntity::getResourceId, resourceId.trim());
        if (text(actorId) != null) query.eq(ChannelAuditEntity::getActorId, actorId.trim());
        if (text(outcome) != null)
            query.eq(ChannelAuditEntity::getOutcome, outcome.trim().toUpperCase());
        return mapper.selectList(query).stream().map(ChannelAuditService::view).toList();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("tenantId is required");
        return value.trim();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static View view(ChannelAuditEntity entity) {
        return new View(
                entity.getId(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getActorName(),
                entity.getPrincipalType(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getOutcome(),
                JsonUtils.parseMap(entity.getDetailsJson()),
                entity.getCreatedAt());
    }
}
