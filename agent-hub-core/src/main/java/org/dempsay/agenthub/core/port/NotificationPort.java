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

    /**
     * Notify that a message was stored, including who wrote it.
     *
     * <p>The default keeps existing listeners, which only need the channel, working.
     * A listener that needs the author or thread overrides this method. The body is
     * not included.
     *
     * @param message stored message, without its body
     * @since 1.0.0
     */
    default void notifyMessagePosted(final PostedMessage message) {
        notifyMessagePosted(message.channelId());
    }
}