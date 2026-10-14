package io.aigoodle.workflow.interaction;

import io.aigoodle.workflow.entity.HumanInteractionEntity;

/** Extension point for chat, email, SMS, bot, webhook or future approval delivery. */
public interface HumanInteractionHook {
    boolean supports(HumanInteractionEntity interaction);

    DeliveryResult deliver(HumanInteractionEntity interaction, String publicFormUrl);

    record DeliveryResult(boolean accepted, String presentation, String externalMessageId, String detail) {}
}
