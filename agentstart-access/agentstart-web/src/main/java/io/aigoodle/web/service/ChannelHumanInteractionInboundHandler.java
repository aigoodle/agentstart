package io.aigoodle.web.service;

import io.aigoodle.channel.ChannelConnectionService;
import io.aigoodle.channel.ChannelInboundEvent;
import io.aigoodle.channel.ChannelInboundHandler;
import io.aigoodle.channel.ChannelInboundResult;
import io.aigoodle.workflow.entity.HumanInteractionEntity;
import io.aigoodle.workflow.service.HumanInteractionService;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Claims operator replies before they can accidentally start a new message workflow. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnBean({HumanInteractionService.class, ChannelConnectionService.class})
public class ChannelHumanInteractionInboundHandler implements ChannelInboundHandler {
    private static final Pattern SHORT_CODE = Pattern.compile("(?i)(?:#|＃)([A-HJ-NP-Z2-9]{6})");
    private final HumanInteractionService interactions;
    private final ChannelConnectionService connections;

    public ChannelHumanInteractionInboundHandler(
            HumanInteractionService interactions, ChannelConnectionService connections) {
        this.interactions = interactions;
        this.connections = connections;
    }

    @Override
    public boolean supports(ChannelInboundEvent event) {
        return selection(event).isPresent();
    }

    @Override
    public ChannelInboundResult handle(ChannelInboundEvent event) {
        return tryHandle(event).orElseGet(ChannelInboundResult::unhandled);
    }

    @Override
    public Optional<ChannelInboundResult> tryHandle(ChannelInboundEvent event) {
        return selection(event).map(interaction -> {
            var submission = interactions.submitChannel(
                    interaction, event.messageId(), stripCode(event.content()), event.senderId());
            return new ChannelInboundResult(
                    true,
                    submission.accepted() ? "已收到，流程将继续处理。" : null,
                    submission.duplicate() ? "human_interaction_duplicate" : "human_interaction_resumed",
                    Map.of(
                            "managed",
                            true,
                            "routeSource",
                            "HUMAN_INTERACTION",
                            "interactionId",
                            interaction.getId(),
                            "workflowRunId",
                            interaction.getRunId()));
        });
    }

    private Optional<HumanInteractionEntity> selection(ChannelInboundEvent event) {
        var ownership =
                connections.ownership(event.provider(), event.runtimeNodeId(), event.channelId(), event.accountId());
        if (ownership == null || event.senderId() == null) return Optional.empty();
        return Optional.ofNullable(interactions.matchChannel(
                ownership.tenantId(), ownership.connectionId(), event.senderId(), code(event.content())));
    }

    private static String code(String content) {
        if (content == null) return null;
        Matcher matcher = SHORT_CODE.matcher(content);
        return matcher.find() ? matcher.group(1).toUpperCase() : null;
    }

    private static String stripCode(String content) {
        return content == null ? "" : SHORT_CODE.matcher(content).replaceAll("").trim();
    }
}
