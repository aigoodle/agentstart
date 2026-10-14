package io.aigoodle.workflow.interaction;

import io.aigoodle.workflow.entity.HumanInteractionEntity;

/** Optional delivery adapter; workflow persistence remains channel-neutral. */
@FunctionalInterface
public interface HumanInteractionNotifier {
    /** Returns the provider message id when one is available. */
    String notify(HumanInteractionEntity interaction);
}
