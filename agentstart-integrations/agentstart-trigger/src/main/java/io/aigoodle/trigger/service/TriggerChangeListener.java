package io.aigoodle.trigger.service;

import io.aigoodle.trigger.entity.TriggerEntity;

/**
 * Notified when triggers are created/updated/removed, so schedulers (e.g. the cron
 * scheduler) can (re)register them. Published as Spring beans.
 */
public interface TriggerChangeListener {

    void onSaved(TriggerEntity trigger);

    void onRemoved(String triggerId);
}
