package org.dempsay.agenthub.core.port;

/**
 * Internal notification port for ephemeral wake-up hints.
 * No durable delivery state is persisted.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface NotificationPort {
    /**
     * Notify that a new message was posted in a channel.
     *
     * @param channelId channel metadata id
     */
    void notifyMessagePosted(String channelId);
}