package io.aigoodle.trigger.api;

/**
 * How a trigger fires.
 */
public enum TriggerType {

    /** Fired by an inbound HTTP call to a webhook path. */
    WEBHOOK,

    /** Fired on a cron schedule. */
    CRON,

    /** Fired when a named internal event is published. */
    EVENT,

    /** Fired by a normalized inbound message from an installed channel connection. */
    CHANNEL_MESSAGE,

    /** Fired only by an explicit API call. */
    MANUAL
}
