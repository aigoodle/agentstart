package io.aigoodle.channel.nativebot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.channel.persistence.ChannelConnectionEntity;
import io.aigoodle.channel.persistence.ChannelConnectionMapper;
import io.aigoodle.common.crypto.TenantSecretCodec;
import io.aigoodle.persistence.TenantSqlScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

final class JdbcNativeAccountStore implements NativeAccountStore {
    private final ChannelConnectionMapper mapper;
    private final TenantSecretCodec secrets;

    JdbcNativeAccountStore(ChannelConnectionMapper mapper, TenantSecretCodec secrets) {
        this.mapper = mapper;
        this.secrets = secrets;
    }

    @Override
    public Optional<Saved> find(String channelId, String accountId) {
        ChannelConnectionEntity row =
                TenantSqlScope.bypass(() -> mapper.selectOne(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getProvider, NativeChannelRuntimeProvider.PROVIDER)
                        .eq(ChannelConnectionEntity::getChannelId, channelId)
                        .eq(ChannelConnectionEntity::getRuntimeAccountId, accountId)
                        .last("LIMIT 1")));
        if (row == null) return Optional.empty();
        return Optional.of(toSaved(row));
    }

    @Override
    public List<SavedAccount> findEnabled() {
        List<ChannelConnectionEntity> rows =
                TenantSqlScope.bypass(() -> mapper.selectList(new LambdaQueryWrapper<ChannelConnectionEntity>()
                        .eq(ChannelConnectionEntity::getProvider, NativeChannelRuntimeProvider.PROVIDER)
                        .eq(ChannelConnectionEntity::getDesiredStatus, "ACTIVE")));
        return rows.stream()
                .filter(row -> row.getChannelId() != null && row.getRuntimeAccountId() != null)
                .map(row -> new SavedAccount(row.getChannelId(), row.getRuntimeAccountId(), toSaved(row)))
                .toList();
    }

    private Saved toSaved(ChannelConnectionEntity row) {
        var values = new LinkedHashMap<>(secrets.decode(row.getTenantId(), row.getEncryptedConfig()));
        values.putAll(secrets.decode(row.getTenantId(), row.getEncryptedCredentials()));
        values.put("accountId", row.getRuntimeAccountId());
        return new Saved(row.getName(), "ACTIVE".equals(row.getDesiredStatus()), values);
    }
}
