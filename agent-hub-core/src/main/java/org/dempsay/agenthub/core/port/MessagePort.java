package org.dempsay.agenthub.core.port;

import java.util.List;
import java.util.UUID;

import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Message operations for public channels.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface MessagePort {
    /**
     * Create a top-level message in a channel.
     *
     * @param dto message data
     * @return created message with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<MessageDto>> create(MessageDto dto);

    /**
     * Create a reply to a message.
     *
     * @param dto reply data with parentMessageId and threadId
     * @return created reply with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<MessageDto>> reply(MessageDto dto);

    /**
     * Get a message by id.
     *
     * @param messageId message metadata id
     * @return message with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<MessageDto>> get(UUID messageId);

    /**
     * List messages in a channel with cursor-based pagination.
     *
     * @param channelId channel metadata id
     * @param limit max number of messages (default 50, max 100)
     * @param before cursor for older messages (exclusive)
     * @param after cursor for newer messages (exclusive)
     * @return list of messages with metadata ordered by (createdAt, id) descending
     */
    ExceptionalResponse<List<AetherPersisted<MessageDto>>> list(String channelId, int limit, String before, String after);

    /**
     * Soft-delete a message (tombstone).
     *
     * @param messageId message metadata id
     * @param deletedByAgentId deleting agent metadata id
     * @param deletedAt deletion timestamp
     * @return updated message with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<MessageDto>> delete(UUID messageId, String deletedByAgentId, String deletedAt);
}