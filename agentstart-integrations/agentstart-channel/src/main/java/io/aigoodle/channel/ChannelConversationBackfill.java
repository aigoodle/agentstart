package io.aigoodle.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.channel.persistence.ChannelEventEntity;
import io.aigoodle.channel.persistence.ChannelEventMapper;
import io.aigoodle.persistence.TenantSqlScope;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** One-time-compatible lazy migration for messages recorded before conversation aggregation existed. */
public final class ChannelConversationBackfill implements ApplicationRunner {
    private final ChannelEventMapper events;
    private final ChannelConversationService conversations;

    public ChannelConversationBackfill(ChannelEventMapper events, ChannelConversationService conversations) {
        this.events = events;
        this.conversations = conversations;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantSqlScope.bypass(() -> {
            events.selectList(new LambdaQueryWrapper<ChannelEventEntity>()
                            .eq(ChannelEventEntity::getDirection, "INBOUND")
                            .isNotNull(ChannelEventEntity::getConnectionId)
                            .isNotNull(ChannelEventEntity::getConversationId)
                            .orderByDesc(ChannelEventEntity::getCreatedAt)
                            .last("LIMIT 5000"))
                    .forEach(conversations::ensure);
            return null;
        });
    }
}
