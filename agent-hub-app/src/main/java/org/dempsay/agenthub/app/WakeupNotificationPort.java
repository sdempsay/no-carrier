package org.dempsay.agenthub.app;

import org.dempsay.agenthub.core.port.NotificationPort;

/**
 * Notification port that wakes long-polling readers.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class WakeupNotificationPort implements NotificationPort {
    private final ChannelWakeups wakeups;

    public WakeupNotificationPort(final ChannelWakeups wakeups) {
        this.wakeups = wakeups;
    }

    @Override
    public void notifyMessagePosted(final String channelId) {
        wakeups.signal(channelId);
    }
}
