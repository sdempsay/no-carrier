package org.dempsay.agenthub.core.service;

import org.dempsay.agenthub.core.port.NotificationPort;

/**
 * No-op notification port for production use.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class NoOpNotificationPort implements NotificationPort {
    @Override
    public void notifyMessagePosted(final String channelId) {
        // No-op
    }
}