package org.dempsay.agenthub.app;

import java.util.List;

import org.dempsay.agenthub.core.port.NotificationPort;
import org.dempsay.agenthub.core.port.PostedMessage;

/**
 * Fans a stored-message notice out to several listeners.
 *
 * <p>One listener throwing does not stop the others and does not escape to the
 * message post.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class CompositeNotificationPort implements NotificationPort {
    private final List<NotificationPort> ports;

    public CompositeNotificationPort(final NotificationPort... ports) {
        this.ports = List.of(ports);
    }

    @Override
    public void notifyMessagePosted(final String channelId) {
        for (final var port : ports) {
            port.notifyMessagePosted(channelId);
        }
    }

    @Override
    public void notifyMessagePosted(final PostedMessage message) {
        for (final var port : ports) {
            port.notifyMessagePosted(message);
        }
    }
}
