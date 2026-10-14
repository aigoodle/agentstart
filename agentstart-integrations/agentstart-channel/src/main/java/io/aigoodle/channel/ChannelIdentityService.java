package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.channel.persistence.ChannelIdentityEntity;
import io.aigoodle.channel.persistence.ChannelIdentityMapper;
import java.util.List;

/** Verified mapping between an external channel principal and an enterprise identity. */
public class ChannelIdentityService {
    public record Identity(
            String id,
            String tenantId,
            String provider,
            String channelId,
            String accountId,
            String externalUserId,
            String enterpriseUserId,
            String verificationStatus,
            boolean enabled) {}

    private final ChannelIdentityMapper mapper;

    public ChannelIdentityService(ChannelIdentityMapper mapper) {
        this.mapper = mapper;
    }

    public Identity resolve(String tenantId, ChannelInboundEvent event) {
        if (event.senderId() == null || event.senderId().isBlank()) return null;
        ChannelIdentityEntity entity = mapper.selectOne(
                query(tenantId, event.provider(), event.channelId(), event.accountId(), event.senderId())
                        .last("LIMIT 1"));
        return entity == null ? null : view(entity);
    }

    public Identity save(
            String tenantId,
            String provider,
            String channelId,
            String accountId,
            String externalUserId,
            String enterpriseUserId,
            String status,
            boolean enabled) {
        ChannelIdentityEntity entity = mapper.selectOne(
                query(tenantId, provider, channelId, accountId, externalUserId).last("LIMIT 1"));
        if (entity == null) {
            entity = new ChannelIdentityEntity();
            entity.setTenantId(required(tenantId));
            entity.setProvider(required(provider));
            entity.setChannelId(required(channelId));
            entity.setAccountId(required(accountId));
            entity.setExternalUserId(required(externalUserId));
        }
        entity.setEnterpriseUserId(required(enterpriseUserId));
        entity.setVerificationStatus(
                status == null || status.isBlank() ? "VERIFIED" : status.trim().toUpperCase());
        entity.setEnabled(enabled);
        if (entity.getId() == null) mapper.insert(entity);
        else
            mapper.update(
                    entity,
                    new LambdaUpdateWrapper<ChannelIdentityEntity>()
                            .eq(ChannelIdentityEntity::getTenantId, entity.getTenantId())
                            .eq(ChannelIdentityEntity::getId, entity.getId()));
        return view(entity);
    }

    public List<Identity> list(String tenantId) {
        return mapper
                .selectList(new LambdaQueryWrapper<ChannelIdentityEntity>()
                        .eq(ChannelIdentityEntity::getTenantId, required(tenantId))
                        .orderByDesc(ChannelIdentityEntity::getUpdatedAt))
                .stream()
                .map(ChannelIdentityService::view)
                .toList();
    }

    public void delete(String tenantId, String id) {
        mapper.delete(new LambdaQueryWrapper<ChannelIdentityEntity>()
                .eq(ChannelIdentityEntity::getTenantId, required(tenantId))
                .eq(ChannelIdentityEntity::getId, required(id)));
    }

    private static LambdaQueryWrapper<ChannelIdentityEntity> query(
            String tenant, String provider, String channel, String account, String external) {
        return new LambdaQueryWrapper<ChannelIdentityEntity>()
                .eq(ChannelIdentityEntity::getTenantId, required(tenant))
                .eq(ChannelIdentityEntity::getProvider, required(provider))
                .eq(ChannelIdentityEntity::getChannelId, required(channel))
                .eq(ChannelIdentityEntity::getAccountId, required(account))
                .eq(ChannelIdentityEntity::getExternalUserId, required(external));
    }

    private static Identity view(ChannelIdentityEntity e) {
        return new Identity(
                e.getId(),
                e.getTenantId(),
                e.getProvider(),
                e.getChannelId(),
                e.getAccountId(),
                e.getExternalUserId(),
                e.getEnterpriseUserId(),
                e.getVerificationStatus(),
                Boolean.TRUE.equals(e.getEnabled()));
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("required value is missing");
        return value.trim();
    }
}
