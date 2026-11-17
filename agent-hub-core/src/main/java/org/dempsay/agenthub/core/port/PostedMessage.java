package org.dempsay.agenthub.core.port;

/**
 * The facts a listener needs after a message is stored.
 *
 * <p>The body is intentionally absent. Wake pings and other ephemeral hints must not
 * receive message content.
 *
 * @param messageId stored message id
 * @param channelId channel id
 * @param threadId thread root id, or null for a top-level post
 * @param parentMessageId parent id, or null when this message starts a thread
 * @param authorAgentId author id
 * @param deleted whether the stored row is a tombstone
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public record PostedMessage(
        String messageId,
        String channelId,
        String threadId,
        String parentMessageId,
        String authorAgentId,
        boolean deleted) {
}
