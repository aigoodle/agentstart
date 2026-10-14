package io.aigoodle.channel;

/** Replace this bean to deliver SLA reminders to IM, email, webhook, or an enterprise event bus. */
@FunctionalInterface
public interface ChannelSlaNotifier {
    void notify(ChannelSlaReminder reminder);
}
