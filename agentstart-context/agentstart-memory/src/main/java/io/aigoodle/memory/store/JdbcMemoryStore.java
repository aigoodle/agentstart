package io.aigoodle.memory.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.memory.*;
import io.aigoodle.memory.entity.MemoryEntity;
import io.aigoodle.memory.mapper.MemoryMapper;
import io.aigoodle.persistence.TenantSqlScope;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public class JdbcMemoryStore implements MemoryStore {
    private final MemoryMapper mapper;

    public JdbcMemoryStore(MemoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(MemoryItem item) {
        MemoryEntity entity = new MemoryEntity();
        entity.setId(item.id());
        entity.setTenantId(item.tenantId());
        entity.setOwnerId(item.ownerId());
        entity.setConversationId(item.conversationId());
        entity.setTier(item.tier().name());
        entity.setRole(item.role().name());
        entity.setContent(item.content());
        entity.setImportance(item.importance());
        entity.setExpiresAt(toLocal(item.expiresAt()));
        entity.setAccessCount(item.accessCount());
        mapper.insert(entity);
    }

    @Override
    public List<MemoryItem> find(MemoryQuery query) {
        LambdaQueryWrapper<MemoryEntity> wrapper = new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, query.tenantId())
                .in(
                        MemoryEntity::getTier,
                        query.tiers().stream().map(Enum::name).toList())
                .eq(query.ownerId() != null && !query.ownerId().isBlank(), MemoryEntity::getOwnerId, query.ownerId())
                .eq(
                        query.conversationId() != null
                                && !query.conversationId().isBlank(),
                        MemoryEntity::getConversationId,
                        query.conversationId())
                .and(part -> part.isNull(MemoryEntity::getExpiresAt)
                        .or()
                        .gt(MemoryEntity::getExpiresAt, LocalDateTime.now()))
                .orderByDesc(MemoryEntity::getCreatedAt)
                .last("limit " + Math.min(1000, query.limit() * 8));
        return mapper.selectList(wrapper).stream().map(JdbcMemoryStore::toItem).toList();
    }

    @Override
    public void purgeExpired(Instant now) {
        TenantSqlScope.bypass(() ->
                mapper.delete(new LambdaQueryWrapper<MemoryEntity>().lt(MemoryEntity::getExpiresAt, toLocal(now))));
    }

    @Override
    public void delete(String tenantId, String ownerId, String conversationId) {
        mapper.delete(new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, tenantId)
                .eq(ownerId != null && !ownerId.isBlank(), MemoryEntity::getOwnerId, ownerId)
                .eq(MemoryEntity::getConversationId, conversationId));
    }

    @Override
    public void recordAccess(String tenantId, Collection<String> memoryIds, Instant accessedAt) {
        if (memoryIds == null || memoryIds.isEmpty()) return;
        mapper.update(
                null,
                new LambdaUpdateWrapper<MemoryEntity>()
                        .eq(MemoryEntity::getTenantId, tenantId)
                        .in(MemoryEntity::getId, memoryIds)
                        .setSql("access_count = COALESCE(access_count, 0) + 1")
                        .set(MemoryEntity::getUpdatedAt, toLocal(accessedAt)));
    }

    private static MemoryItem toItem(MemoryEntity value) {
        return new MemoryItem(
                value.getId(),
                value.getTenantId(),
                value.getOwnerId(),
                value.getConversationId(),
                MemoryTier.valueOf(value.getTier()),
                MemoryRole.valueOf(value.getRole()),
                value.getContent(),
                value.getImportance() == null ? 0.5 : value.getImportance(),
                toInstant(value.getCreatedAt()),
                toInstant(value.getExpiresAt()),
                value.getAccessCount() == null ? 0 : value.getAccessCount(),
                Map.of());
    }

    private static LocalDateTime toLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
