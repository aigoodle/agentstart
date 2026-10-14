package io.aigoodle.channel;

import org.springframework.context.ApplicationEventPublisher;

/** Default non-invasive integration: host applications can consume ChannelSlaReminder as a Spring event. */
public final class SpringEventChannelSlaNotifier implements ChannelSlaNotifier {
    private final ApplicationEventPublisher publisher;

    public SpringEventChannelSlaNotifier(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void notify(ChannelSlaReminder reminder) {
        publisher.publishEvent(reminder);
    }
}
